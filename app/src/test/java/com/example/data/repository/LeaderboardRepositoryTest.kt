package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardRepositoryTest {

    @Test
    fun buildLeaderboardStateRanksEntriesByDerivedScore() {
        val previousState = LeaderboardState(
            topPlayers = listOf(
                LeaderboardEntry(
                    userUid = "user-1",
                    displayName = "Alpha",
                    score = 5200,
                    weeklyScore = 4200,
                    totalCoinsEarned = 5000,
                    successfulRedeems = 4,
                    trustScore = 110,
                    totalTransactions = 20,
                    leaguePosition = "DIAMOND LEAGUE",
                    currentRank = 1,
                    weeklyRank = 1,
                    hallOfFameRank = 1,
                    isCurrentUser = false
                ),
                LeaderboardEntry(
                    userUid = "user-2",
                    displayName = "Beta",
                    score = 2800,
                    weeklyScore = 2600,
                    totalCoinsEarned = 2500,
                    successfulRedeems = 2,
                    trustScore = 100,
                    totalTransactions = 12,
                    leaguePosition = "SILVER LEAGUE",
                    currentRank = 2,
                    weeklyRank = 2,
                    hallOfFameRank = 2,
                    isCurrentUser = false
                )
            )
        )

        val snapshot = AppSnapshot(
            userUid = "user-3",
            username = "Current",
            displayName = "Current",
            trustScore = 140,
            totalAdsWatched = 8,
            sessionAds = 2,
            successfulRedeems = 3,
            transactionStats = TransactionStatistics(
                totalTransactions = 9,
                coinsEarnedLifetime = 3500,
                lastTransactionTime = "2026-06-29 10:00"
            ),
            redeemStats = RedeemQueueStats(totalLifetimeRedeems = 3)
        )

        val state = LeaderboardRepository.buildLeaderboardState(snapshot, previousState)

        assertEquals(3, state.topPlayers.size)
        assertEquals(1, state.currentUserStanding.currentRank)
        assertEquals("DIAMOND LEAGUE", state.currentUserStanding.leaguePosition)
        assertEquals(3, state.stats.totalPlayers)
    }
}
