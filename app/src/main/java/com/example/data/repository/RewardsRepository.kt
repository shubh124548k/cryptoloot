package com.example.data.repository

import android.content.Context
import com.example.data.api.*
import com.example.data.local.UserPreferences
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
    private val userRepo: UserRepository
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val historyAdapter = moshi.adapter<List<RedemptionHistoryItem>>(
        Types.newParameterizedType(List::class.java, RedemptionHistoryItem::class.java)
    )

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
                    coins_deducted = 0,
                    coins_remaining = currentBalance,
                    payout_value = 0f,
                    currency = "INR",
                    status = activeRequest.currentStatus,
                    message = "A redeem request for this pack is already in progress.",
                    estimated_delivery = activeRequest.estimatedProcessingTime
                )
            }

            val payout = pack.rewardAmount.toFloat()
            val requestId = (100000..999999).random()
            val requestUuid = "REQ-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}"
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val queuePosition = queue.size + 1
            val estimatedWait = if (queuePosition <= 1) "Processing now" else "${queuePosition - 1} queued ahead"
            val previousBalance = currentBalance

            userRepo.applyRedemptionSuccess(requestId, coins, payout, "PENDING")
            userRepo.addRedeemRequest(
                RedeemRequestRecord(
                    id = requestUuid,
                    userUid = userRepo.getCurrentSnapshot().userUid.ifEmpty { prefs.masterUid ?: deviceId },
                    username = userRepo.getCurrentSnapshot().displayName ?: prefs.displayName ?: "Krypton_Warrior",
                    packId = pack.id,
                    packName = pack.name,
                    coinsUsed = coins,
                    rewardAmount = pack.rewardAmount,
                    status = "PENDING",
                    queuePosition = queuePosition,
                    estimatedProcessingTime = estimatedWait,
                    requestTimestamp = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
            userRepo.addRedeemQueueEntry(
                RedeemQueueEntry(
                    id = requestUuid,
                    userUid = userRepo.getCurrentSnapshot().userUid.ifEmpty { prefs.masterUid ?: deviceId },
                    username = userRepo.getCurrentSnapshot().displayName ?: prefs.displayName ?: "Krypton_Warrior",
                    packId = pack.id,
                    packName = pack.name,
                    coinCost = coins,
                    rewardAmount = pack.rewardAmount,
                    requestTimestamp = now,
                    currentStatus = "QUEUED",
                    processingPosition = queuePosition,
                    estimatedProcessingTime = estimatedWait,
                    previousBalance = previousBalance,
                    currentBalance = prefs.coinBalance,
                    createdAt = now,
                    updatedAt = now
                )
            )
            saveRedemptionOffline(requestId, coins, payout, "PENDING", pack.name)

            RedeemResponse(
                success = true,
                request_id = requestId,
                coins_deducted = coins,
                coins_remaining = prefs.coinBalance,
                payout_value = payout,
                currency = "INR",
                status = "QUEUED",
                message = "Redemption queued successfully. Your request is now being processed.",
                estimated_delivery = estimatedWait
            )
        }
    }

    suspend fun getRedemptionHistory(): List<RedemptionHistoryItem> {
        val deviceId = prefs.deviceId
        return try {
            val remote = api.getRedemptions(deviceId)
            if (remote.isNotEmpty()) {
                saveHistoryToPrefs(remote)
                remote
            } else {
                getOfflineHistory()
            }
        } catch (e: Exception) {
            getOfflineHistory()
        }
    }

    private fun getOfflineHistory(): List<RedemptionHistoryItem> {
        val historyJson = userRepo.getLocalHistoryJson()
        if (historyJson.isEmpty()) {
            return emptyList()
        }
        return try {
            historyAdapter.fromJson(historyJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistoryToPrefs(list: List<RedemptionHistoryItem>) {
        val json = historyAdapter.toJson(list)
        userRepo.saveLocalHistoryJson(json)
    }

    private fun saveRedemptionOffline(reqId: Int, coins: Int, payout: Float, status: String, rewardName: String? = null) {
        val current = getOfflineHistory().toMutableList()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        current.add(
            0,
            RedemptionHistoryItem(
                request_id = reqId,
                coin_cost = coins,
                payout_value = payout,
                status = status,
                code_value = if (status == "COMPLETED") "KL-${(1000..9999).random()}-${(1000..9999).random()}" else null,
                created_at = dateStr
            )
        )
        saveHistoryToPrefs(current)
    }
}

fun UserRepository.getLocalHistoryJson(): String {
    val sp = this.context.applicationContext.getSharedPreferences("kryptoloot_history", Context.MODE_PRIVATE)
    return sp.getString("history_json", "") ?: ""
}

fun UserRepository.saveLocalHistoryJson(json: String) {
    val sp = this.context.applicationContext.getSharedPreferences("kryptoloot_history", Context.MODE_PRIVATE)
    sp.edit().putString("history_json", json).apply()
}
