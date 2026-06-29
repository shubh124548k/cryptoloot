package com.example.data.repository

import com.example.data.local.UserPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LeaderboardRepository(
    private val userRepo: UserRepository,
    private val prefs: UserPreferences
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val leaderboardAdapter = moshi.adapter(LeaderboardStats::class.java)

    private val _leaderboard = MutableStateFlow(LeaderboardStats())
    val leaderboard: StateFlow<LeaderboardStats> = _leaderboard.asStateFlow()

    init {
        refreshLeaderboard(userRepo.getCurrentSnapshot())
    }

    fun refreshLeaderboard(snapshot: AppSnapshot = userRepo.getCurrentSnapshot()) {
        val score = calculateLeaderboardScore(snapshot)
        val stats = LeaderboardStats(
            adsWatched = snapshot.totalAdsWatched,
            coinsEarned = snapshot.totalCoinsEarned,
            successfulRedeems = snapshot.successfulRedeems,
            trustScore = snapshot.trustScore,
            leaderboardScore = score,
            currentRank = snapshot.redeemStats.totalLifetimeRedeems.coerceAtLeast(0),
            weeklyRank = snapshot.redeemStats.pendingCount.coerceAtLeast(0),
            globalRank = snapshot.successfulRedeems.coerceAtLeast(0)
        )
        _leaderboard.value = stats
        prefs.leaderboardJson = leaderboardAdapter.toJson(stats)
    }

    fun calculateLeaderboardScore(snapshot: AppSnapshot): Int {
        return calculateLeaderboardScore(
            totalAds = snapshot.totalAdsWatched,
            totalCoinsEarned = snapshot.totalCoinsEarned,
            successfulRedeems = snapshot.successfulRedeems,
            trustScore = snapshot.trustScore
        )
    }

    fun calculateLeaderboardScore(
        totalAds: Int,
        totalCoinsEarned: Int,
        successfulRedeems: Int,
        trustScore: Int
    ): Int {
        val adsScore = totalAds * 0.4f
        val coinScore = totalCoinsEarned * 0.3f
        val redeemScore = successfulRedeems * 0.2f
        val trustScoreValue = trustScore * 0.1f
        return (adsScore + coinScore + redeemScore + trustScoreValue).toInt()
    }
}
