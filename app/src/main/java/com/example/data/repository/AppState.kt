package com.example.data.repository

data class AppSnapshot(
    val username: String = "Krypton_Warrior",
    val userUid: String = "",
    val coinBalance: Int = 0,
    val totalAdsWatched: Int = 0,
    val dailyAdsWatched: Int = 0,
    val successfulRedeems: Int = 0,
    val transactionHistory: List<TransactionRecord> = emptyList(),
    val redeemRequests: List<RedeemRequestRecord> = emptyList(),
    val redeemStats: RedeemQueueStats = RedeemQueueStats(),
    val redeemPayments: List<RedeemPayment> = emptyList(),
    val transactionStats: TransactionStatistics = TransactionStatistics(),
    val leaderboardState: LeaderboardState = LeaderboardState(),
    val trustScore: Int = 100,
    val totalCoinsEarned: Int = 0,
    val notificationCount: Int = 0,
    val adsWatchedToday: Int = 0,
    val sessionAds: Int = 0,
    val dailyCap: Int = 100,
    val sessionCap: Int = 15,
    val breakUntil: Long? = null,
    val operationalStatus: String = "ACTIVE",
    val masterUid: String? = null,
    val deviceId: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null
)

data class TransactionRecord(
    val id: String,
    val userUid: String = "",
    val username: String = "Krypton_Warrior",
    val transactionType: TransactionType = TransactionType.SYSTEM,
    val type: String,
    val coinsBefore: Int = 0,
    val amount: Int,
    val coinsChanged: Int = amount,
    val coinsAfter: Int = 0,
    val rewardPack: String? = null,
    val cashAmount: Float? = null,
    val queueId: String? = null,
    val status: String,
    val description: String = "",
    val timestamp: String,
    val completedTimestamp: String? = null,
    val deviceId: String? = null,
    val serverSyncFlag: Boolean = false,
    val versionNumber: Int = 1,
    val message: String? = null,
    val rewardName: String? = null,
    val rewardAmount: Int? = null,
    val packId: Int? = null,
    val updatedAt: String? = null,
    val action: String = type,
    val previousBalance: Int? = null,
    val currentBalance: Int? = null,
    val legacyDescription: String? = null
)

data class RedeemRequestRecord(
    val id: String,
    val userUid: String,
    val username: String,
    val packId: Int,
    val packName: String,
    val coinsUsed: Int,
    val rewardAmount: Int,
    val status: String,
    val queuePosition: Int = 0,
    val estimatedProcessingTime: String = "",
    val requestTimestamp: String = "",
    val createdAt: String,
    val updatedAt: String
)

data class RedeemQueueStats(
    val pendingCount: Int = 0,
    val queuedCount: Int = 0,
    val underReviewCount: Int = 0,
    val approvedCount: Int = 0,
    val completedCount: Int = 0,
    val rejectedCount: Int = 0,
    val totalLifetimeRedeems: Int = 0,
    val lastRedeemAt: String? = null,
    val activeQueueId: String? = null,
    val activeQueuePosition: Int? = null,
    val activeStatus: String? = null,
    val estimatedWaitText: String? = null
)

data class TransactionStatistics(
    val totalTransactions: Int = 0,
    val coinsEarnedLifetime: Int = 0,
    val coinsRedeemedLifetime: Int = 0,
    val lastTransactionTime: String? = null,
    val lastRedeemTime: String? = null,
    val averageCoinsPerReward: Float = 0f,
    val rewardTransactions: Int = 0,
    val redeemTransactions: Int = 0,
    val approvalTransactions: Int = 0,
    val rejectedTransactions: Int = 0,
    val systemTransactions: Int = 0
)

enum class TransactionType {
    WATCH_REWARD,
    COIN_BONUS,
    REDEEM_REQUEST,
    REDEEM_PROCESSING,
    REDEEM_APPROVED,
    REDEEM_REJECTED,
    ADMIN_ADJUSTMENT,
    QUEUE_COMPLETED,
    SYSTEM
}

enum class TransactionFilter {
    ALL,
    REWARDS,
    REDEEMS,
    APPROVALS,
    REJECTED,
    SYSTEM
}
