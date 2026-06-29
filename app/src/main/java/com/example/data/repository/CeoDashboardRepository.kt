package com.example.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class CeoDashboardRepository(
    private val userRepo: UserRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    init {
        userRepo.appState.onEach { snapshot ->
            refreshDashboard(snapshot)
        }.launchIn(scope)
        refreshDashboard(userRepo.getCurrentSnapshot())
    }

    fun refreshDashboard(snapshot: AppSnapshot = userRepo.getCurrentSnapshot()) {
        val stats = buildDashboardStats(snapshot)
        val revenueSummary = buildRevenueSummary(snapshot, stats)
        val coinEconomySummary = buildCoinEconomySummary(snapshot, stats)
        val redemptionSummary = buildRedemptionSummary(snapshot, stats)
        val leaderboardSummary = buildLeaderboardSummary(snapshot, stats)
        val trustSummary = buildTrustSummary(snapshot, stats)
        val adPerformanceSummary = buildAdPerformanceSummary(snapshot)
        val budgetSummary = buildBudgetSummary(snapshot, stats, revenueSummary)

        _dashboardState.value = DashboardState(
            stats = stats,
            revenueSummary = revenueSummary,
            coinEconomySummary = coinEconomySummary,
            redemptionSummary = redemptionSummary,
            leaderboardSummary = leaderboardSummary,
            trustSummary = trustSummary,
            adPerformanceSummary = adPerformanceSummary,
            budgetSummary = budgetSummary
        )
    }

    fun dashboardSnapshot(): DashboardState = _dashboardState.value

    private fun buildDashboardStats(snapshot: AppSnapshot): DashboardStats {
        val totalUsers = if (snapshot.userUid.isNotEmpty() || snapshot.masterUid != null) 1 else 0
        val activeUsers = if (hasActivity(snapshot)) 1 else 0
        val totalCoinsIssued = snapshot.totalCoinsEarned.coerceAtLeast(snapshot.coinBalance)
        val totalCoinsRedeemed = max(
            snapshot.transactionStats.coinsRedeemedLifetime,
            snapshot.redeemRequests.sumOf { it.coinsUsed }.coerceAtLeast(snapshot.redeemStats.totalLifetimeRedeems * 10)
        )
        val outstandingCoins = (totalCoinsIssued - totalCoinsRedeemed).coerceAtLeast(0)
        val todaysCoins = snapshot.transactionHistory
            .filter { isSameDay(it.timestamp, currentDayLabel()) }
            .filter { it.transactionType == TransactionType.WATCH_REWARD || it.transactionType == TransactionType.COIN_BONUS }
            .sumOf { it.coinsChanged.coerceAtLeast(0) }
        val todaysRedeems = snapshot.transactionHistory
            .filter { isSameDay(it.timestamp, currentDayLabel()) }
            .filter { isRedeemEvent(it.transactionType) }
            .size
        val dailyActivity = snapshot.transactionHistory
            .groupBy { dayLabel(it.timestamp) }
            .toSortedMap()
            .map { (day, transactions) ->
                DailyActivityPoint(
                    day = day,
                    activityCount = transactions.size,
                    coinVolume = transactions.sumOf { it.coinsChanged.coerceAtLeast(0) }
                )
            }

        return DashboardStats(
            totalUsers = totalUsers,
            activeUsers = activeUsers,
            totalCoinsIssued = totalCoinsIssued,
            totalCoinsRedeemed = totalCoinsRedeemed,
            outstandingCoins = outstandingCoins,
            todaysCoins = todaysCoins,
            todaysRedeems = todaysRedeems,
            todaysRevenue = todaysCoins * COIN_VALUE_RUPEES,
            lifetimeRevenue = (totalCoinsRedeemed * COIN_VALUE_RUPEES),
            averageCoinsPerUser = if (totalUsers > 0) totalCoinsIssued.toFloat() / totalUsers else 0f,
            averageRedeemsPerUser = if (totalUsers > 0) todaysRedeems.toFloat() / totalUsers else 0f,
            trustDistribution = buildTrustDistribution(snapshot.trustScore),
            leaderboardDistribution = buildLeaderboardDistribution(snapshot.leaderboardState.stats.currentLeague),
            dailyActivity = dailyActivity
        )
    }

    private fun buildRevenueSummary(snapshot: AppSnapshot, stats: DashboardStats): RevenueSummary {
        val projectedMonthlyRevenue = stats.lifetimeRevenue.coerceAtLeast(0f)
        return RevenueSummary(
            todaysRevenue = stats.todaysRevenue,
            lifetimeRevenue = stats.lifetimeRevenue,
            averageRevenuePerUser = if (stats.totalUsers > 0) stats.lifetimeRevenue / stats.totalUsers else 0f,
            projectedMonthlyRevenue = projectedMonthlyRevenue
        )
    }

    private fun buildCoinEconomySummary(snapshot: AppSnapshot, stats: DashboardStats): CoinEconomySummary {
        return CoinEconomySummary(
            totalCoinsIssued = stats.totalCoinsIssued,
            totalCoinsRedeemed = stats.totalCoinsRedeemed,
            outstandingCoins = stats.outstandingCoins,
            balanceInCirculation = snapshot.coinBalance.coerceAtLeast(0),
            averageCoinsPerUser = stats.averageCoinsPerUser
        )
    }

    private fun buildRedemptionSummary(snapshot: AppSnapshot, stats: DashboardStats): RedemptionSummary {
        return RedemptionSummary(
            totalRedeems = stats.totalCoinsRedeemed,
            todaysRedeems = stats.todaysRedeems,
            successfulRedeems = snapshot.successfulRedeems.coerceAtLeast(0),
            pendingRedeems = snapshot.redeemStats.pendingCount.coerceAtLeast(0),
            averageRedeemsPerUser = stats.averageRedeemsPerUser
        )
    }

    private fun buildLeaderboardSummary(snapshot: AppSnapshot, stats: DashboardStats): LeaderboardSummary {
        return LeaderboardSummary(
            currentRank = snapshot.leaderboardState.currentUserStanding.currentRank,
            currentLeague = snapshot.leaderboardState.stats.currentLeague,
            leaderboardScore = snapshot.leaderboardState.stats.leaderboardScore,
            leaderboardDistribution = stats.leaderboardDistribution
        )
    }

    private fun buildTrustSummary(snapshot: AppSnapshot, stats: DashboardStats): TrustSummary {
        return TrustSummary(
            averageTrustScore = snapshot.trustScore.toFloat(),
            trustDistribution = stats.trustDistribution,
            activeTrustBand = when {
                snapshot.trustScore >= 90 -> "HIGH"
                snapshot.trustScore >= 70 -> "MEDIUM"
                else -> "LOW"
            }
        )
    }

    private fun buildAdPerformanceSummary(snapshot: AppSnapshot): AdPerformanceSummary {
        val adRewardCoins = snapshot.transactionHistory
            .filter { it.transactionType == TransactionType.WATCH_REWARD }
            .sumOf { it.coinsChanged.coerceAtLeast(0) }
        val adsToday = snapshot.transactionHistory
            .filter { isSameDay(it.timestamp, currentDayLabel()) }
            .count { it.transactionType == TransactionType.WATCH_REWARD }
        return AdPerformanceSummary(
            totalAdsWatched = snapshot.totalAdsWatched.coerceAtLeast(0),
            adsWatchedToday = snapshot.adsWatchedToday.coerceAtLeast(adsToday),
            adRewardCoins = adRewardCoins,
            averageCoinsPerAd = if (snapshot.totalAdsWatched > 0) adRewardCoins.toFloat() / snapshot.totalAdsWatched else 0f,
            lastAdAt = snapshot.transactionHistory.firstOrNull { it.transactionType == TransactionType.WATCH_REWARD }?.timestamp
        )
    }

    private fun buildBudgetSummary(
        snapshot: AppSnapshot,
        stats: DashboardStats,
        revenueSummary: RevenueSummary
    ): BudgetSummary {
        val estimatedLiability = (stats.outstandingCoins * COIN_VALUE_RUPEES).coerceAtLeast(0f)
        val monthlyProjection = revenueSummary.projectedMonthlyRevenue.coerceAtLeast(0f)
        return BudgetSummary(
            estimatedCompanyLiability = estimatedLiability,
            monthlyProjection = monthlyProjection,
            liabilityBuffer = (monthlyProjection - estimatedLiability).coerceAtLeast(0f)
        )
    }

    private fun hasActivity(snapshot: AppSnapshot): Boolean {
        return snapshot.transactionHistory.isNotEmpty() ||
            snapshot.redeemRequests.isNotEmpty() ||
            snapshot.totalAdsWatched > 0 ||
            snapshot.totalCoinsEarned > 0 ||
            snapshot.successfulRedeems > 0
    }

    private fun buildTrustDistribution(trustScore: Int): Map<String, Int> {
        val label = when {
            trustScore >= 90 -> "HIGH"
            trustScore >= 70 -> "MEDIUM"
            else -> "LOW"
        }
        return mapOf(
            "HIGH" to if (label == "HIGH") 1 else 0,
            "MEDIUM" to if (label == "MEDIUM") 1 else 0,
            "LOW" to if (label == "LOW") 1 else 0
        )
    }

    private fun buildLeaderboardDistribution(league: String): Map<String, Int> {
        return mapOf(
            "BRONZE" to if (league == "BRONZE LEAGUE") 1 else 0,
            "SILVER" to if (league == "SILVER LEAGUE") 1 else 0,
            "GOLD" to if (league == "GOLD LEAGUE") 1 else 0,
            "PLATINUM" to if (league == "PLATINUM LEAGUE") 1 else 0,
            "DIAMOND" to if (league == "DIAMOND LEAGUE") 1 else 0,
            "MYTHIC" to if (league == "MYTHIC LEAGUE") 1 else 0
        )
    }

    private fun isRedeemEvent(type: TransactionType): Boolean = when (type) {
        TransactionType.REDEEM_REQUEST,
        TransactionType.REDEEM_PROCESSING,
        TransactionType.REDEEM_APPROVED,
        TransactionType.REDEEM_REJECTED,
        TransactionType.QUEUE_COMPLETED -> true
        else -> false
    }

    private fun isSameDay(timestamp: String?, dayLabel: String): Boolean {
        if (timestamp.isNullOrBlank()) return false
        return dayLabel(timestamp) == dayLabel
    }

    private fun dayLabel(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return currentDayLabel()
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = parser.parse(timestamp) ?: Date()
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        }.getOrDefault(currentDayLabel())
    }

    private fun currentDayLabel(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private companion object {
        const val COIN_VALUE_RUPEES = 0.0333f
    }
}
