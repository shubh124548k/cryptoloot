package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.*
import com.example.data.local.UserPreferences
import com.example.data.local.DeviceUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardsRepository(
    private val api: KryptoLootApi,
    private val prefs: UserPreferences,
    private val userRepo: UserRepository,
    private val redeemPaymentRepository: RedeemPaymentRepository? = null
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun getEconomyRates(): EconomyTiersResponse {
        return try {
            api.getRates()
        } catch (e: Exception) {
            EconomyTiersResponse(
                current_ecpm = 40.0f,
                tiers = listOf(
                    EconomyTier(300, 30, 10.0f, "starter"),
                    EconomyTier(600, 60, 25.0f, "value"),
                    EconomyTier(1000, 100, 50.0f, "premium")
                ),
                coins_per_ad = 10,
                revenue_share = "40%"
            )
        }
    }

    suspend fun redeemCoins(coins: Int, pack: RewardPack? = null): RedeemResponse {
        return withContext(Dispatchers.IO) {
            val deviceId = prefs.deviceId
            val currentBalance = prefs.coinBalance

            if (currentBalance < coins) {
                return@withContext RedeemResponse(
                    success = false,
                    request_id = 0,
                    transaction_id = "",
                    coins_deducted = 0,
                    coins_remaining = currentBalance,
                    payout_value = 0f,
                    currency = "INR",
                    status = "REJECTED",
                    message = "Insufficient coin balance. Keep grinding! 💪",
                    estimated_delivery = ""
                )
            }

            if (pack == null) {
                return@withContext RedeemResponse(
                    success = false,
                    request_id = 0,
                    transaction_id = "",
                    coins_deducted = 0,
                    coins_remaining = currentBalance,
                    payout_value = 0f,
                    currency = "INR",
                    status = "REJECTED",
                    message = "Selected reward pack is unavailable.",
                    estimated_delivery = ""
                )
            }

            val queue = userRepo.getRedeemQueue()
            val activeRequest = queue.firstOrNull { it.packId == pack.id && it.currentStatus !in setOf("COMPLETED", "REJECTED") }
            if (activeRequest != null) {
                return@withContext RedeemResponse(
                    success = false,
                    request_id = activeRequest.id.filter { it.isDigit() }.toIntOrNull() ?: 0,
                    transaction_id = "",
                    coins_deducted = 0,
                    coins_remaining = currentBalance,
                    payout_value = 0f,
                    currency = "INR",
                    status = activeRequest.currentStatus,
                    message = "A redeem request for this pack is already in progress.",
                    estimated_delivery = activeRequest.estimatedProcessingTime
                )
            }

            // Backend-first submission
            val payout = pack.rewardAmount.toFloat()
            try {
                val req = RedeemRequest(
                    device_id = deviceId,
                    coins_to_redeem = coins,
                    username = userRepo.getCurrentSnapshot().displayName ?: prefs.displayName ?: "Krypton_Warrior",
                    reward_pack = pack.id.toString(),
                    coins = coins,
                    cash_amount = payout,
                    payment_method = "UPI",
                    payment_details = ""
                )
                val adapter = moshi.adapter(RedeemRequest::class.java)
                try { Log.d("TRACE", "RewardsRepository.redeemCoins RedeemRequest JSON: ${adapter.toJson(req)}") } catch (_: Exception) {}
                val response = api.redeem(req)
                if (response.success) {
                    // Use backend as single source of truth
                    prefs.coinBalance = response.coins_remaining
                    userRepo.refreshFromPreferences()
                    try {
                        val remote = api.getRedemptions(deviceId)
                        if (remote.isNotEmpty()) userRepo.mergeRemoteRedemptions(remote)
                    } catch (_: Exception) { }

                    return@withContext response
                } else {
                    return@withContext response
                }
            } catch (e: Exception) {
                // If network is unavailable, persist a single offline request and retry later
                val context = userRepo.context
                val online = try { DeviceUtils.isOnline(context) } catch (_: Exception) { false }
                if (!online) {
                    // Prevent storing duplicates
                    if (!prefs.offlineRedeemJson.isNullOrBlank()) {
                        return@withContext RedeemResponse(
                            success = false,
                            request_id = 0,
                            transaction_id = "",
                            coins_deducted = 0,
                            coins_remaining = prefs.coinBalance,
                            payout_value = payout,
                            currency = "INR",
                            status = "PENDING",
                            message = "An offline redeem is already pending. Will retry when online.",
                            estimated_delivery = "Awaiting connectivity"
                        )
                    }

                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    val offline = mapOf(
                        "id" to "offline-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}",
                        "pack_id" to pack.id,
                        "coins" to coins,
                        "payout" to payout,
                        "created_at" to now
                    )
                    prefs.offlineRedeemJson = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java).toJson(offline)

                    // Deduct coins offline and refresh UI
                    userRepo.deductCoinsOffline(coins)
                    userRepo.refreshFromPreferences()

                    return@withContext RedeemResponse(
                        success = true,
                        request_id = 0,
                        transaction_id = "",
                        coins_deducted = coins,
                        coins_remaining = prefs.coinBalance,
                        payout_value = payout,
                        currency = "INR",
                        status = "QUEUED",
                        message = "Redeem queued offline. Will retry when connectivity returns.",
                        estimated_delivery = "Awaiting connectivity"
                    )
                }
                return@withContext RedeemResponse(
                    success = false,
                    request_id = 0,
                    transaction_id = "",
                    coins_deducted = 0,
                    coins_remaining = prefs.coinBalance,
                    payout_value = payout,
                    currency = "INR",
                    status = "ERROR",
                    message = "Failed to submit redeem request: ${e.message}",
                    estimated_delivery = ""
                )
            }
        }
    }

    suspend fun getRedemptionHistory(): List<RedemptionHistoryItem> {
        val deviceId = prefs.deviceId
        return try {
            val remote = api.getRedemptions(deviceId)
            Log.d("SYNC_TRACE", "getRedemptionHistory: API returned ${remote.size} items")
            remote.forEachIndexed { i, item ->
                Log.d("SYNC_TRACE", "getRedemptionHistory: item[$i] queue_id=${item.queue_id} status=${item.status} admin_reply='${item.admin_reply}' request_id=${item.request_id}")
            }
            userRepo.mergeRemoteRedemptions(remote)
            remote
        } catch (e: Exception) {
            Log.d("SYNC_TRACE", "getRedemptionHistory: EXCEPTION ${e::class.simpleName}: ${e.message}")
            emptyList()
        }
    }
}

