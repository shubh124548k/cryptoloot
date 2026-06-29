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
    val type: String,
    val amount: Int,
    val status: String,
    val timestamp: String,
    val message: String? = null,
    val rewardName: String? = null,
    val rewardAmount: Int? = null,
    val packId: Int? = null,
    val updatedAt: String? = null,
    val action: String = type,
    val previousBalance: Int? = null,
    val currentBalance: Int? = null,
    val description: String? = null
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
