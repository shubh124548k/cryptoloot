package com.example.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.max
import kotlin.math.min

class FraudDetectionRepository(
    private val userRepo: UserRepository? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _fraudSnapshot = MutableStateFlow(FraudReport())
    val fraudSnapshot: StateFlow<FraudReport> = _fraudSnapshot.asStateFlow()

    init {
        userRepo?.appState?.onEach { snapshot ->
            refreshFraud(snapshot)
        }?.launchIn(scope)
        userRepo?.let { refreshFraud(it.getCurrentSnapshot()) }
    }

    fun refreshFraud(snapshot: AppSnapshot) {
        _fraudSnapshot.value = buildFraudReport(snapshot)
    }

    fun fraudSnapshot(): FraudReport = _fraudSnapshot.value

    fun calculateTrustScore(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Int {
        return buildFraudReport(snapshot).trustScore
    }

    fun calculateFraudRisk(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): FraudRiskLevel {
        return buildFraudReport(snapshot).riskLevel
    }

    fun calculatePenalty(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): TrustPenalty {
        return buildFraudReport(snapshot).penalty
    }

    fun calculateRecovery(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): TrustReward {
        return buildFraudReport(snapshot).recovery
    }

    fun calculateDeviceConsistency(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Int {
        return buildFraudReport(snapshot).statistics.deviceConsistency
    }

    fun calculateRewardVelocity(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Int {
        return buildFraudReport(snapshot).statistics.rewardVelocity
    }

    fun calculateRedeemVelocity(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Int {
        return buildFraudReport(snapshot).statistics.redeemVelocity
    }

    fun calculateSessionHealth(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Int {
        return buildFraudReport(snapshot).statistics.sessionHealth
    }

    fun calculateRiskLevel(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): FraudRiskLevel {
        return buildFraudReport(snapshot).riskLevel
    }

    fun buildFraudReport(snapshot: AppSnapshot): FraudReport {
        val rewardVelocity = calculateRewardVelocityInternal(snapshot)
        val redeemVelocity = calculateRedeemVelocityInternal(snapshot)
        val deviceConsistency = calculateDeviceConsistencyInternal(snapshot)
        val sessionHealth = calculateSessionHealthInternal(snapshot)
        val riskSignals = mutableListOf<FraudSignal>()

        if (rewardVelocity > 8) {
            riskSignals += FraudSignal(FraudCategory.REWARD_VELOCITY, "Reward velocity above baseline", 12)
        }
        if (redeemVelocity > 5) {
            riskSignals += FraudSignal(FraudCategory.REDEEM_VELOCITY, "Redeem velocity above baseline", 10)
        }
        if (deviceConsistency < 60) {
            riskSignals += FraudSignal(FraudCategory.DEVICE_CONSISTENCY, "Device consistency below expected range", 8)
        }
        if (sessionHealth < 60) {
            riskSignals += FraudSignal(FraudCategory.SESSION_HEALTH, "Session health below expected range", 7)
        }
        if (snapshot.transactionStats.totalTransactions > 20) {
            riskSignals += FraudSignal(FraudCategory.RAPID_STATE_CHANGE, "Rapid state changes detected", 9)
        }

        val penaltyScore = riskSignals.sumOf { it.weight }
        val baseScore = snapshot.trustScore.coerceIn(0, 100)
        val adjustedScore = max(0, min(100, baseScore - penaltyScore))
        val recoveryScore = if (adjustedScore > 0) calculateRecoveryInternal(adjustedScore, snapshot) else 0
        val finalScore = min(100, max(0, adjustedScore + recoveryScore))
        val riskLevel = calculateRiskLevelInternal(finalScore, riskSignals.size)

        val penalty = TrustPenalty(
            amount = penaltyScore,
            reason = riskSignals.firstOrNull()?.message ?: "No suspicious activity",
            isActive = penaltyScore > 0,
            categories = riskSignals.map { it.category }
        )
        val recovery = TrustReward(
            amount = recoveryScore,
            reason = "Gradual recovery from stable activity",
            isActive = recoveryScore > 0,
            recoveredAt = snapshot.transactionStats.lastTransactionTime
        )
        val history = TrustHistory(
            currentScore = finalScore,
            previousScore = baseScore,
            events = listOf(
                TrustEvent(
                    score = finalScore,
                    reason = penalty.reason,
                    timestamp = snapshot.transactionStats.lastTransactionTime
                )
            )
        )
        val statistics = FraudStatistics(
            rewardVelocity = rewardVelocity,
            redeemVelocity = redeemVelocity,
            deviceConsistency = deviceConsistency,
            sessionHealth = sessionHealth,
            signalCount = riskSignals.size,
            trustScore = finalScore
        )

        return FraudReport(
            trustScore = finalScore,
            riskLevel = riskLevel,
            signals = riskSignals,
            penalty = penalty,
            recovery = recovery,
            history = history,
            statistics = statistics
        )
    }

    private fun calculateRecoveryInternal(score: Int, snapshot: AppSnapshot): Int {
        val stabilityBonus = when {
            snapshot.transactionStats.totalTransactions < 5 -> 2
            snapshot.transactionStats.totalTransactions < 10 -> 4
            else -> 6
        }
        return if (score < 100) stabilityBonus else 0
    }

    private fun calculateRewardVelocityInternal(snapshot: AppSnapshot): Int {
        val earned = snapshot.transactionStats.coinsEarnedLifetime.coerceAtLeast(0)
        val transactions = snapshot.transactionStats.totalTransactions.coerceAtLeast(0)
        return (earned / max(1, transactions + 1)).coerceIn(0, 100)
    }

    private fun calculateRedeemVelocityInternal(snapshot: AppSnapshot): Int {
        val redeemed = snapshot.transactionStats.coinsRedeemedLifetime.coerceAtLeast(0)
        val transactions = snapshot.transactionStats.totalTransactions.coerceAtLeast(0)
        return (redeemed / max(1, transactions + 1)).coerceIn(0, 100)
    }

    private fun calculateDeviceConsistencyInternal(snapshot: AppSnapshot): Int {
        val trust = snapshot.trustScore.coerceIn(0, 100)
        val activity = snapshot.transactionStats.totalTransactions.coerceAtLeast(0)
        return (trust - (activity / 5)).coerceIn(0, 100)
    }

    private fun calculateSessionHealthInternal(snapshot: AppSnapshot): Int {
        val trust = snapshot.trustScore.coerceIn(0, 100)
        val ads = snapshot.totalAdsWatched.coerceAtLeast(0)
        return (trust - min(ads, 20)).coerceIn(0, 100)
    }

    private fun calculateRiskLevelInternal(score: Int, signalCount: Int): FraudRiskLevel {
        return when {
            score <= 20 || signalCount >= 4 -> FraudRiskLevel.CRITICAL
            score <= 40 || signalCount >= 3 -> FraudRiskLevel.HIGH
            score <= 70 || signalCount >= 2 -> FraudRiskLevel.MEDIUM
            score <= 85 -> FraudRiskLevel.LOW
            else -> FraudRiskLevel.SAFE
        }
    }
}

enum class FraudCategory {
    REWARD_VELOCITY,
    REDEEM_VELOCITY,
    DEVICE_CONSISTENCY,
    SESSION_HEALTH,
    RAPID_STATE_CHANGE
}

enum class FraudRiskLevel {
    SAFE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class FraudSignal(
    val category: FraudCategory,
    val message: String,
    val weight: Int
)

data class TrustScoreSnapshot(
    val trustScore: Int = 100,
    val riskLevel: FraudRiskLevel = FraudRiskLevel.SAFE,
    val penalty: TrustPenalty = TrustPenalty(),
    val recovery: TrustReward = TrustReward(),
    val statistics: FraudStatistics = FraudStatistics()
)

data class TrustEvent(
    val score: Int = 100,
    val reason: String = "Stable activity",
    val timestamp: String? = null
)

data class TrustPenalty(
    val amount: Int = 0,
    val reason: String = "No penalty",
    val isActive: Boolean = false,
    val categories: List<FraudCategory> = emptyList()
)

data class TrustReward(
    val amount: Int = 0,
    val reason: String = "No recovery",
    val isActive: Boolean = false,
    val recoveredAt: String? = null
)

data class TrustHistory(
    val currentScore: Int = 100,
    val previousScore: Int = 100,
    val events: List<TrustEvent> = emptyList()
)

data class FraudReport(
    val trustScore: Int = 100,
    val riskLevel: FraudRiskLevel = FraudRiskLevel.SAFE,
    val signals: List<FraudSignal> = emptyList(),
    val penalty: TrustPenalty = TrustPenalty(),
    val recovery: TrustReward = TrustReward(),
    val history: TrustHistory = TrustHistory(),
    val statistics: FraudStatistics = FraudStatistics()
)

data class FraudStatistics(
    val rewardVelocity: Int = 0,
    val redeemVelocity: Int = 0,
    val deviceConsistency: Int = 100,
    val sessionHealth: Int = 100,
    val signalCount: Int = 0,
    val trustScore: Int = 100
)
