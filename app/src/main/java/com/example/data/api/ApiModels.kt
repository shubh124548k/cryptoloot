package com.example.data.api

data class HandshakeRequest(
    val device_id: String,
    val network_mac: String?,
    val is_vpn_flag: Boolean,
    val is_emulator_flag: Boolean
)

data class HandshakeResponse(
    val status: String,
    val device_id: String,
    val current_balance: Int,
    val trust_score: Int,
    val operational_status: String,
    val ads_today: Int,
    val session_ads: Int,
    val break_until: String?,
    val daily_cap: Int,
    val session_cap: Int,
    val coins_per_ad: Int,
    val min_redeem: Int
)

data class AdCompletionRequest(
    val device_id: String,
    val time_spent_seconds: Int
)

data class AdCompletionResponse(
    val success: Boolean,
    val coins_earned: Int,
    val new_balance: Int,
    val ads_today: Int,
    val session_ads: Int,
    val break_until: String?,
    val trust_score: Int,
    val message: String
)

data class EconomyTiersResponse(
    val current_ecpm: Float,
    val tiers: List<EconomyTier>,
    val coins_per_ad: Int,
    val revenue_share: String
)

data class EconomyTier(
    val coins: Int,
    val ads: Int,
    val rupee_value: Float,
    val badge: String
)

data class RedeemRequest(
    val device_id: String,
    val coins_to_redeem: Int
)

data class RedeemResponse(
    val success: Boolean,
    val request_id: Int,
    val coins_deducted: Int,
    val coins_remaining: Int,
    val payout_value: Float,
    val currency: String,
    val status: String,
    val message: String,
    val estimated_delivery: String
)

data class LeaderboardItem(
    val rank: Int,
    val device_id: String,
    val coins: Int,
    val ads_watched: Int
)

data class RedemptionHistoryItem(
    val request_id: Int,
    val coin_cost: Int,
    val payout_value: Float,
    val status: String,
    val code_value: String?,
    val created_at: String,
    val transaction_type: String = "SYSTEM",
    val description: String = "",
    val queue_id: String? = null,
    val coins_before: Int = 0,
    val coins_after: Int = 0,
    val completed_at: String? = null,
    val device_id: String? = null,
    val server_synced: Boolean = false,
    val version_number: Int = 1,
    val reward_name: String? = null,
    val cash_amount: Float? = null
)

data class DeviceHandshakeRequest(
    val device_id: String
)

data class DeviceHandshakeResponse(
    val status: String,
    val master_uid: String,
    val coin_balance: Int,
    val trust_score: Int,
    val operational_status: String,
    val ads_today: Int,
    val session_ads: Int,
    val break_until: String?,
    val daily_reset_seconds_remaining: Int? = 0
)

data class UidRecoveryRequest(
    val device_id: String,
    val pasted_uid: String
)

data class UidRecoveryResponse(
    val status: String,
    val message: String?,
    val master_uid: String?,
    val coin_balance: Int?,
    val trust_score: Int?,
    val operational_status: String?,
    val ads_today: Int?,
    val session_ads: Int?,
    val break_until: String?
)
