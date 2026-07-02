package com.example.data.repository

import com.example.data.local.UserPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class LeaderboardRepository(
    private val userRepo: UserRepository,
    private val prefs: UserPreferences
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val leaderboardStatsAdapter = moshi.adapter(LeaderboardStats::class.java)
    private val leaderboardStateAdapter = moshi.adapter(LeaderboardState::class.java)

    private val _leaderboard = MutableStateFlow(loadPersistedState().stats)
    val leaderboard: StateFlow<LeaderboardStats> = _leaderboard.asStateFlow()

    private val _leaderboardState = MutableStateFlow(loadPersistedState())
    val leaderboardState: StateFlow<LeaderboardState> = _leaderboardState.asStateFlow()

    private val _weeklyLeaderboard = MutableStateFlow(_leaderboardState.value.weeklyLeaderboard)
    val weeklyLeaderboard: StateFlow<WeeklyLeaderboard> = _weeklyLeaderboard.asStateFlow()

    private val _hallOfFameLeaderboard = MutableStateFlow(_leaderboardState.value.hallOfFameLeaderboard)
    val hallOfFameLeaderboard: StateFlow<HallOfFameLeaderboard> = _hallOfFameLeaderboard.asStateFlow()

    private val _currentUserStanding = MutableStateFlow(_leaderboardState.value.currentUserStanding)
    val currentUserStanding: StateFlow<CurrentUserStanding> = _currentUserStanding.asStateFlow()

    private val _topPlayers = MutableStateFlow(_leaderboardState.value.topPlayers)
    val topPlayers: StateFlow<List<LeaderboardEntry>> = _topPlayers.asStateFlow()

    private val _weeklyReset = MutableStateFlow(_leaderboardState.value.weeklyReset)
    val weeklyReset: StateFlow<WeeklyReset> = _weeklyReset.asStateFlow()

    init {
        refreshLeaderboard(userRepo.getCurrentSnapshot())
    }

    fun refreshLeaderboard(snapshot: AppSnapshot = userRepo.getCurrentSnapshot()) {
        val previousState = _leaderboardState.value
        val state = buildLeaderboardState(snapshot, previousState)

        _leaderboard.value = state.stats
        _leaderboardState.value = state
        _weeklyLeaderboard.value = state.weeklyLeaderboard
        _hallOfFameLeaderboard.value = state.hallOfFameLeaderboard
        _currentUserStanding.value = state.currentUserStanding
        _topPlayers.value = state.topPlayers
        _weeklyReset.value = state.weeklyReset
        prefs.leaderboardJson = leaderboardStateAdapter.toJson(state)
    }

    companion object {
        fun buildLeaderboardState(
            snapshot: AppSnapshot,
            previousState: LeaderboardState = LeaderboardState()
        ): LeaderboardState {
            val transactionStats = snapshot.transactionStats
            val weeklyReset = buildWeeklyReset()
            val currentScore = calculateLeaderboardScoreInternal(snapshot)
            val weeklyScore = calculateWeeklyLeaderboardScoreInternal(snapshot)
            val currentLeague = leagueForScoreInternal(currentScore)
            val lastActivityAt = transactionStats.lastTransactionTime ?: snapshot.redeemStats.lastRedeemAt
            val successfulRedeems = snapshot.successfulRedeems

            val currentEntry = LeaderboardEntry(
                userUid = snapshot.userUid.ifEmpty { snapshot.masterUid ?: snapshot.deviceId },
                displayName = snapshot.displayName ?: snapshot.username,
                score = currentScore,
                weeklyScore = weeklyScore,
                totalCoinsEarned = transactionStats.coinsEarnedLifetime,
                successfulRedeems = successfulRedeems,
                trustScore = snapshot.trustScore,
                totalTransactions = transactionStats.totalTransactions,
                leaguePosition = currentLeague,
                currentRank = 1,
                weeklyRank = 1,
                hallOfFameRank = 1,
                rankDifference = 0,
                isCurrentUser = true,
                lastActivityAt = lastActivityAt
            )

            val seededEntries = (previousState.topPlayers.filter { it.userUid != currentEntry.userUid } + currentEntry)
                .distinctBy { it.userUid }
                .sortedByDescending { it.score }

            val rankedEntries = seededEntries.mapIndexed { index, entry ->
                val previousRank = previousState.topPlayers.indexOfFirst { it.userUid == entry.userUid } + 1
                val rankDifference = if (previousRank > 0) previousRank - (index + 1) else 0
                entry.copy(
                    currentRank = index + 1,
                    weeklyRank = entry.weeklyRank.coerceAtLeast(index + 1),
                    hallOfFameRank = index + 1,
                    rankDifference = rankDifference,
                    isCurrentUser = entry.userUid == currentEntry.userUid
                )
            }

            val weeklyEntries = rankedEntries
                .map { entry ->
                    val weeklyEntryScore = if (entry.isCurrentUser) weeklyScore else entry.weeklyScore
                    entry.copy(score = weeklyEntryScore, weeklyScore = weeklyEntryScore)
                }
                .sortedByDescending { it.score }
                .mapIndexed { index, entry ->
                    entry.copy(weeklyRank = index + 1)
                }

            val hallOfFameEntries = rankedEntries
                .sortedByDescending { it.score }
                .mapIndexed { index, entry ->
                    entry.copy(hallOfFameRank = index + 1)
                }

            val currentRankedEntry = rankedEntries.firstOrNull { it.isCurrentUser } ?: currentEntry
            val weeklyCurrentEntry = weeklyEntries.firstOrNull { it.isCurrentUser } ?: currentEntry
            val hallOfFameCurrentEntry = hallOfFameEntries.firstOrNull { it.isCurrentUser } ?: currentEntry
            val totalPlayers = rankedEntries.size.coerceAtLeast(1)

            val stats = LeaderboardStats(
                adsWatched = snapshot.totalAdsWatched,
                coinsEarned = transactionStats.coinsEarnedLifetime,
                successfulRedeems = successfulRedeems,
                trustScore = snapshot.trustScore,
                leaderboardScore = currentRankedEntry.score,
                currentRank = currentRankedEntry.currentRank,
                weeklyRank = weeklyCurrentEntry.weeklyRank,
                hallOfFameRank = hallOfFameCurrentEntry.hallOfFameRank,
                globalRank = currentRankedEntry.currentRank,
                totalPlayers = totalPlayers,
                rankDifference = currentRankedEntry.rankDifference,
                currentLeague = currentLeague,
                weeklyResetAt = weeklyReset.nextResetAt,
                lastActivityAt = lastActivityAt
            )

            val currentStanding = CurrentUserStanding(
                userUid = currentRankedEntry.userUid,
                displayName = currentRankedEntry.displayName,
                currentRank = currentRankedEntry.currentRank,
                weeklyRank = weeklyCurrentEntry.weeklyRank,
                hallOfFameRank = hallOfFameCurrentEntry.hallOfFameRank,
                leaderboardScore = currentRankedEntry.score,
                rankDifference = currentRankedEntry.rankDifference,
                leaguePosition = currentRankedEntry.leaguePosition,
                totalPlayers = totalPlayers,
                weeklyReset = weeklyReset
            )

            val weekly = WeeklyLeaderboard(
                entries = weeklyEntries.take(10),
                totalPlayers = totalPlayers,
                currentUserStanding = currentStanding.copy(
                    currentRank = weeklyCurrentEntry.currentRank,
                    weeklyRank = weeklyCurrentEntry.weeklyRank,
                    leaderboardScore = weeklyCurrentEntry.score,
                    leaguePosition = weeklyCurrentEntry.leaguePosition
                ),
                weeklyReset = weeklyReset
            )

            val hallOfFame = HallOfFameLeaderboard(
                entries = hallOfFameEntries.take(10),
                totalPlayers = totalPlayers,
                currentUserStanding = currentStanding.copy(
                    currentRank = hallOfFameCurrentEntry.currentRank,
                    hallOfFameRank = hallOfFameCurrentEntry.hallOfFameRank,
                    leaderboardScore = hallOfFameCurrentEntry.score,
                    leaguePosition = hallOfFameCurrentEntry.leaguePosition
                )
            )

            return LeaderboardState(
                stats = stats,
                weeklyLeaderboard = weekly,
                hallOfFameLeaderboard = hallOfFame,
                currentUserStanding = currentStanding,
                topPlayers = rankedEntries.take(10),
                weeklyReset = weeklyReset
            )
        }

        private fun calculateLeaderboardScoreInternal(snapshot: AppSnapshot): Int {
            val transactionStats = snapshot.transactionStats
            val activityBonus = (transactionStats.totalTransactions * 15) + (snapshot.totalAdsWatched * 5) + (snapshot.sessionAds * 2)
            return calculateLeaderboardScoreInternal(
                totalCoinsEarned = transactionStats.coinsEarnedLifetime,
                successfulRedeems = snapshot.redeemStats.completedCount,
                trustScore = snapshot.trustScore,
                totalTransactions = transactionStats.totalTransactions,
                activityBonus = activityBonus
            )
        }

        private fun calculateLeaderboardScoreInternal(
            totalCoinsEarned: Int,
            successfulRedeems: Int,
            trustScore: Int,
            totalTransactions: Int,
            activityBonus: Int
        ): Int {
            val baseScore = totalCoinsEarned
            val redeemScore = successfulRedeems * 250
            val trustScoreValue = trustScore * 10
            val activityScore = totalTransactions * 15
            return max(0, baseScore + redeemScore + trustScoreValue + activityScore + activityBonus)
        }

        private fun calculateWeeklyLeaderboardScoreInternal(snapshot: AppSnapshot): Int {
            val since = System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)
            val recentTransactions = snapshot.transactionHistory.filter { parseTimestampInternal(it.timestamp) >= since }
            val recentRewardCoins = recentTransactions.filter {
                it.transactionType == TransactionType.WATCH_REWARD || it.transactionType == TransactionType.COIN_BONUS
            }.sumOf { it.coinsChanged.coerceAtLeast(0) }
            val recentRedeems = recentTransactions.filter { isRedeemEvent(it.transactionType) }.distinctBy { it.queueId ?: it.id }
            val activityBonus = recentTransactions.size * 12 + snapshot.sessionAds * 2
            return max(0, recentRewardCoins + (recentRedeems.size * 250) + (snapshot.trustScore * 8) + activityBonus)
        }

        private fun buildWeeklyReset(): WeeklyReset {
            val now = Date()
            val nextReset = Date(now.time + (7L * 24L * 60L * 60L * 1000L))
            return WeeklyReset(
                periodDays = 7,
                periodStartAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(now),
                nextResetAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(nextReset),
                resetLabel = "WEEKLY_RESET"
            )
        }

        private fun leagueForScoreInternal(score: Int): String = when {
            score >= 10000 -> "MYTHIC LEAGUE"
            score >= 5000 -> "DIAMOND LEAGUE"
            score >= 2500 -> "PLATINUM LEAGUE"
            score >= 1000 -> "GOLD LEAGUE"
            score >= 500 -> "SILVER LEAGUE"
            else -> "BRONZE LEAGUE"
        }

        private fun parseTimestampInternal(timestamp: String?): Long {
            if (timestamp.isNullOrEmpty()) return 0L
            return runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(timestamp)?.time ?: 0L
            }.getOrDefault(0L)
        }

        private fun isRedeemEvent(type: TransactionType): Boolean = when (type) {
            TransactionType.REDEEM_REQUEST,
            TransactionType.REDEEM_PROCESSING,
            TransactionType.REDEEM_APPROVED,
            TransactionType.REDEEM_REJECTED,
            TransactionType.QUEUE_COMPLETED -> true
            else -> false
        }
    }

    fun calculateLeaderboardScore(snapshot: AppSnapshot): Int = buildLeaderboardState(snapshot).stats.leaderboardScore

    fun calculateLeaderboardScore(
        totalCoinsEarned: Int,
        successfulRedeems: Int,
        trustScore: Int,
        totalTransactions: Int,
        activityBonus: Int
    ): Int {
        return max(0, totalCoinsEarned + (successfulRedeems * 250) + (trustScore * 10) + (totalTransactions * 15) + activityBonus)
    }

    fun calculateWeeklyLeaderboardScore(snapshot: AppSnapshot): Int = buildLeaderboardState(snapshot).weeklyLeaderboard.entries.firstOrNull { it.isCurrentUser }?.score ?: 0

    private fun loadPersistedState(): LeaderboardState {
        val json = prefs.leaderboardJson.orEmpty()
        if (json.isEmpty()) return LeaderboardState()
        return runCatching { leaderboardStateAdapter.fromJson(json) ?: LeaderboardState() }.getOrDefault(LeaderboardState())
    }
}
