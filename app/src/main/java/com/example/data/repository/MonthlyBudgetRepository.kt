package com.example.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MonthlyBudgetRepository(
    private val userRepo: UserRepository? = null,
    private val transactionRepo: TransactionRepository? = null,
    private val rewardRepo: RewardRepository? = null,
    private val leaderboardRepo: LeaderboardRepository? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _budgetSnapshot = MutableStateFlow(MonthlyBudgetSnapshot())
    val budgetSnapshot: StateFlow<MonthlyBudgetSnapshot> = _budgetSnapshot.asStateFlow()

    init {
        userRepo?.appState?.onEach { snapshot ->
            refreshBudget(snapshot)
        }?.launchIn(scope)
        userRepo?.let { refreshBudget(it.getCurrentSnapshot()) }
    }

    fun refreshBudget(snapshot: AppSnapshot) {
        _budgetSnapshot.value = calculateBudget(snapshot)
    }

    fun budgetSnapshot(): MonthlyBudgetSnapshot = _budgetSnapshot.value

    fun calculateRemainingBudget(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Int {
        return calculateBudget(snapshot).remainingBudget
    }

    fun calculateDailyAllowance(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Int {
        return calculateBudget(snapshot).dailyBudget.allowance
    }

    fun calculateProjectedMonthlySpend(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Float {
        return calculateBudget(snapshot).forecast.projectedSpend
    }

    fun calculateOutstandingLiability(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Float {
        return calculateBudget(snapshot).projection.outstandingLiability
    }

    fun calculateSafeCoinIssuance(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Int {
        return calculateBudget(snapshot).forecast.safeCoinIssuance
    }

    fun isBudgetExceeded(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Boolean {
        return calculateBudget(snapshot).status == BudgetStatus.EXCEEDED
    }

    fun isBudgetWarning(snapshot: AppSnapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()): Boolean {
        return calculateBudget(snapshot).status == BudgetStatus.WARNING || calculateBudget(snapshot).status == BudgetStatus.CRITICAL
    }

    fun calculateBudget(snapshot: AppSnapshot): MonthlyBudgetSnapshot {
        val transactionStats = transactionRepo?.getStatisticsSnapshot() ?: snapshot.transactionStats
        val rewardPressure = rewardRepo?.rewardProgress?.value?.count { it.isUnlocked } ?: 0
        val coinsIssued = snapshot.totalCoinsEarned.coerceAtLeast(snapshot.coinBalance).coerceAtLeast(transactionStats.coinsEarnedLifetime)
        val coinsRedeemed = transactionStats.coinsRedeemedLifetime.coerceAtLeast(snapshot.successfulRedeems * 10)
        val outstandingLiability = maxOf(0, coinsIssued - coinsRedeemed)
        val monthlyBudget = maxOf(
            coinsIssued,
            coinsRedeemed * 2,
            snapshot.transactionStats.totalTransactions * 100,
            snapshot.successfulRedeems * 1000,
            rewardPressure * 250
        )
        val remainingBudget = maxOf(0, monthlyBudget - outstandingLiability)
        val dailySpend = if (coinsIssued > 0) coinsRedeemed.toFloat() / maxOf(1, DAYS_IN_MONTH) else 0f
        val dailyBudget = monthlyBudget / DAYS_IN_MONTH
        val daysElapsed = minOf(DAYS_IN_MONTH, getDaysElapsed(snapshot.transactionStats.lastTransactionTime))
        val daysLeft = maxOf(1, DAYS_IN_MONTH - daysElapsed)
        val dailyAllowance = maxOf(0, (remainingBudget / daysLeft.toFloat()).toInt())
        val projectedSpend = if (daysElapsed > 0) (dailySpend * DAYS_IN_MONTH) else 0f
        val projectedMonthEndSpend = projectedSpend.coerceAtLeast(outstandingLiability.toFloat())
        val safeCoinIssuance = maxOf(0, remainingBudget - (projectedMonthEndSpend.toInt()))
        val usagePercent = if (monthlyBudget > 0) ((outstandingLiability.toFloat() / monthlyBudget.toFloat()) * 100f) else 0f
        val status = when {
            usagePercent >= 100f -> BudgetStatus.EXCEEDED
            usagePercent >= 95f -> BudgetStatus.CRITICAL
            usagePercent >= 90f -> BudgetStatus.LIMITED
            usagePercent >= 80f -> BudgetStatus.WARNING
            else -> BudgetStatus.NORMAL
        }
        val warningThreshold = when (status) {
            BudgetStatus.EXCEEDED -> 100f
            BudgetStatus.CRITICAL -> 95f
            BudgetStatus.LIMITED -> 90f
            BudgetStatus.WARNING -> 80f
            BudgetStatus.NORMAL -> 70f
        }
        val alert = BudgetAlert(
            percentageUsed = usagePercent,
            threshold = warningThreshold,
            isWarning = status == BudgetStatus.WARNING || status == BudgetStatus.CRITICAL || status == BudgetStatus.LIMITED || status == BudgetStatus.EXCEEDED,
            isCritical = status == BudgetStatus.CRITICAL || status == BudgetStatus.EXCEEDED,
            message = when (status) {
                BudgetStatus.EXCEEDED -> "Budget exceeded"
                BudgetStatus.CRITICAL -> "Budget critical"
                BudgetStatus.LIMITED -> "Budget limited"
                BudgetStatus.WARNING -> "Budget warning"
                BudgetStatus.NORMAL -> "Budget normal"
            }
        )
        val budgetUsage = BudgetUsage(
            monthlyBudget = monthlyBudget,
            usedBudget = outstandingLiability,
            remainingBudget = remainingBudget,
            usagePercentage = usagePercent
        )
        val forecast = BudgetForecast(
            projectedSpend = projectedSpend,
            safeCoinIssuance = safeCoinIssuance,
            dailyAllowance = dailyAllowance,
            remainingBudget = remainingBudget
        )
        val dailyBudgetInfo = DailyBudget(
            allowance = dailyAllowance,
            dailySpend = dailySpend.toInt(),
            daysLeft = daysLeft,
            remaining = remainingBudget
        )
        val projection = BudgetProjection(
            projectedMonthEndSpend = projectedMonthEndSpend,
            outstandingLiability = outstandingLiability.toFloat(),
            safeRemainingBudget = remainingBudget - projectedMonthEndSpend.toInt()
        )
        val statistics = BudgetStatistics(
            totalIssued = coinsIssued,
            totalRedeemed = coinsRedeemed,
            outstandingLiability = outstandingLiability,
            cashPaid = coinsRedeemed.toFloat() * COIN_VALUE_RUPEES,
            expectedCashLiability = outstandingLiability.toFloat() * COIN_VALUE_RUPEES,
            dailySpend = dailySpend.toInt(),
            dailyBudgetRemaining = remainingBudget,
            projectedMonthEndSpend = projectedMonthEndSpend.toInt(),
            safeRemainingBudget = safeCoinIssuance
        )

        return MonthlyBudgetSnapshot(
            monthlyBudget = MonthlyBudget(
                monthlyBudget = monthlyBudget,
                coinsIssued = coinsIssued,
                coinsRedeemed = coinsRedeemed,
                outstandingCoinLiability = outstandingLiability,
                cashPaid = statistics.cashPaid,
                expectedCashLiability = statistics.expectedCashLiability,
                dailySpend = dailySpend.toInt(),
                dailyBudgetRemaining = remainingBudget,
                projectedMonthEndSpend = projectedMonthEndSpend.toInt(),
                safeRemainingBudget = safeCoinIssuance,
                status = status,
                alert = alert
            ),
            usage = budgetUsage,
            forecast = forecast,
            dailyBudget = dailyBudgetInfo,
            projection = projection,
            statistics = statistics,
            alert = alert,
            status = status,
            remainingBudget = remainingBudget,
            dailyAllowance = dailyAllowance,
            projectedMonthlySpend = projectedMonthEndSpend,
            outstandingLiability = outstandingLiability.toFloat(),
            safeCoinIssuance = safeCoinIssuance
        )
    }

    private fun getDaysElapsed(lastTransactionTime: String?): Int {
        if (lastTransactionTime.isNullOrEmpty()) return 0
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return runCatching {
            val timestamp = parser.parse(lastTransactionTime)?.time ?: 0L
            val now = System.currentTimeMillis()
            val diffDays = ((now - timestamp) / (1000L * 60L * 60L * 24L)).toInt()
            diffDays.coerceIn(0, DAYS_IN_MONTH)
        }.getOrDefault(0)
    }

    private companion object {
        const val DAYS_IN_MONTH = 30
        const val COIN_VALUE_RUPEES = 0.0333f
    }
}

data class MonthlyBudget(
    val monthlyBudget: Int = 0,
    val coinsIssued: Int = 0,
    val coinsRedeemed: Int = 0,
    val outstandingCoinLiability: Int = 0,
    val cashPaid: Float = 0f,
    val expectedCashLiability: Float = 0f,
    val dailySpend: Int = 0,
    val dailyBudgetRemaining: Int = 0,
    val projectedMonthEndSpend: Int = 0,
    val safeRemainingBudget: Int = 0,
    val status: BudgetStatus = BudgetStatus.NORMAL,
    val alert: BudgetAlert = BudgetAlert()
)

data class MonthlyBudgetSnapshot(
    val monthlyBudget: MonthlyBudget = MonthlyBudget(),
    val usage: BudgetUsage = BudgetUsage(),
    val forecast: BudgetForecast = BudgetForecast(),
    val dailyBudget: DailyBudget = DailyBudget(),
    val projection: BudgetProjection = BudgetProjection(),
    val statistics: BudgetStatistics = BudgetStatistics(),
    val alert: BudgetAlert = BudgetAlert(),
    val status: BudgetStatus = BudgetStatus.NORMAL,
    val remainingBudget: Int = 0,
    val dailyAllowance: Int = 0,
    val projectedMonthlySpend: Float = 0f,
    val outstandingLiability: Float = 0f,
    val safeCoinIssuance: Int = 0
)

data class BudgetUsage(
    val monthlyBudget: Int = 0,
    val usedBudget: Int = 0,
    val remainingBudget: Int = 0,
    val usagePercentage: Float = 0f
)

data class BudgetForecast(
    val projectedSpend: Float = 0f,
    val safeCoinIssuance: Int = 0,
    val dailyAllowance: Int = 0,
    val remainingBudget: Int = 0
)

data class DailyBudget(
    val allowance: Int = 0,
    val dailySpend: Int = 0,
    val daysLeft: Int = 0,
    val remaining: Int = 0
)

data class BudgetAlert(
    val percentageUsed: Float = 0f,
    val threshold: Float = 70f,
    val isWarning: Boolean = false,
    val isCritical: Boolean = false,
    val message: String = "Budget normal"
)

enum class BudgetStatus {
    NORMAL,
    WARNING,
    LIMITED,
    CRITICAL,
    EXCEEDED
}

data class BudgetProjection(
    val projectedMonthEndSpend: Float = 0f,
    val outstandingLiability: Float = 0f,
    val safeRemainingBudget: Int = 0
)

data class BudgetStatistics(
    val totalIssued: Int = 0,
    val totalRedeemed: Int = 0,
    val outstandingLiability: Int = 0,
    val cashPaid: Float = 0f,
    val expectedCashLiability: Float = 0f,
    val dailySpend: Int = 0,
    val dailyBudgetRemaining: Int = 0,
    val projectedMonthEndSpend: Int = 0,
    val safeRemainingBudget: Int = 0
)
