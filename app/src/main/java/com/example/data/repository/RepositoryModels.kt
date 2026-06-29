package com.example.data.repository

data class UserProfile(
    val uid: String = "",
    val username: String = "Krypton_Warrior",
    val coinBalance: Int = 0,
    val coinsEarned: Int = 0,
    val adsWatched: Int = 0,
    val dailyAds: Int = 0,
    val successfulRedeems: Int = 0,
    val pendingRedeems: Int = 0,
    val trustScore: Int = 100,
    val leaderboardScore: Int = 0,
    val joinDate: String = "",
    val recoveryUid: String? = null,
    val recoveryMetadata: String? = null,
    val recoveryTimestamp: String? = null,
    val recoveryStatus: String = "PENDING"
)

data class UidRecord(
    val uid: String = "",
    val recoveryUid: String? = null,
    val recoveryMetadata: String? = null,
    val recoveryTimestamp: String? = null,
    val recoveryStatus: String = "PENDING"
)

data class RewardProgress(
    val id: Int,
    val name: String,
    val requiredCoins: Int,
    val rewardAmount: Int,
    val isUnlocked: Boolean,
    val currentProgress: Int,
    val progressPercentage: Int
)

data class LeaderboardStats(
    val adsWatched: Int = 0,
    val coinsEarned: Int = 0,
    val successfulRedeems: Int = 0,
    val trustScore: Int = 100,
    val leaderboardScore: Int = 0,
    val currentRank: Int = 0,
    val weeklyRank: Int = 0,
    val hallOfFameRank: Int = 0,
    val globalRank: Int = 0,
    val totalPlayers: Int = 0,
    val rankDifference: Int = 0,
    val currentLeague: String = "UNRANKED",
    val weeklyResetAt: String? = null,
    val lastActivityAt: String? = null
)

data class LeaderboardEntry(
    val userUid: String,
    val displayName: String,
    val score: Int,
    val weeklyScore: Int,
    val totalCoinsEarned: Int,
    val successfulRedeems: Int,
    val trustScore: Int,
    val totalTransactions: Int,
    val leaguePosition: String,
    val currentRank: Int = 0,
    val weeklyRank: Int = 0,
    val hallOfFameRank: Int = 0,
    val rankDifference: Int = 0,
    val isCurrentUser: Boolean = false,
    val lastActivityAt: String? = null
)

data class WeeklyReset(
    val periodDays: Int = 7,
    val periodStartAt: String? = null,
    val nextResetAt: String? = null,
    val resetLabel: String = "WEEKLY_RESET"
)

data class CurrentUserStanding(
    val userUid: String = "",
    val displayName: String = "Krypton_Warrior",
    val currentRank: Int = 0,
    val weeklyRank: Int = 0,
    val hallOfFameRank: Int = 0,
    val leaderboardScore: Int = 0,
    val rankDifference: Int = 0,
    val leaguePosition: String = "UNRANKED",
    val totalPlayers: Int = 0,
    val weeklyReset: WeeklyReset = WeeklyReset()
)

data class WeeklyLeaderboard(
    val entries: List<LeaderboardEntry> = emptyList(),
    val totalPlayers: Int = 0,
    val currentUserStanding: CurrentUserStanding = CurrentUserStanding(),
    val weeklyReset: WeeklyReset = WeeklyReset()
)

data class HallOfFameLeaderboard(
    val entries: List<LeaderboardEntry> = emptyList(),
    val totalPlayers: Int = 0,
    val currentUserStanding: CurrentUserStanding = CurrentUserStanding()
)

data class LeaderboardState(
    val stats: LeaderboardStats = LeaderboardStats(),
    val weeklyLeaderboard: WeeklyLeaderboard = WeeklyLeaderboard(),
    val hallOfFameLeaderboard: HallOfFameLeaderboard = HallOfFameLeaderboard(),
    val currentUserStanding: CurrentUserStanding = CurrentUserStanding(),
    val topPlayers: List<LeaderboardEntry> = emptyList(),
    val weeklyReset: WeeklyReset = WeeklyReset()
)

data class NotificationItem(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean = false
)

data class SyncQueueItem(
    val id: String,
    val entityType: String,
    val action: String,
    val payloadJson: String,
    val createdAt: String,
    val attemptCount: Int = 0,
    val status: String = "PENDING"
)

data class SyncState(
    val isSyncing: Boolean = false,
    val isOnline: Boolean = true,
    val pendingCount: Int = 0,
    val lastSyncAt: String? = null,
    val lastError: String? = null
)

data class RedeemQueueEntry(
    val id: String,
    val userUid: String,
    val username: String,
    val packId: Int,
    val packName: String,
    val coinCost: Int,
    val rewardAmount: Int,
    val requestTimestamp: String,
    val currentStatus: String,
    val processingPosition: Int,
    val estimatedProcessingTime: String,
    val previousBalance: Int,
    val currentBalance: Int,
    val createdAt: String,
    val updatedAt: String
)

data class RedeemQueueState(
    val activeQueue: RedeemQueueEntry? = null,
    val pendingCount: Int = 0,
    val queueSize: Int = 0,
    val lastRedeemAt: String? = null,
    val totalLifetimeRedeems: Int = 0
)
