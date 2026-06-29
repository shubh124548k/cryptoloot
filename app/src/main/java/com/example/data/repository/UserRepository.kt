package com.example.data.repository

import android.content.Context
import com.example.data.api.*
import com.example.data.local.DeviceUtils
import com.example.data.local.UserPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserRepository(
    val context: Context,
    private val api: KryptoLootApi,
    private val prefs: UserPreferences
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val transactionAdapter = moshi.adapter<List<TransactionRecord>>(
        Types.newParameterizedType(List::class.java, TransactionRecord::class.java)
    )
    private val redeemRequestAdapter = moshi.adapter<List<RedeemRequestRecord>>(
        Types.newParameterizedType(List::class.java, RedeemRequestRecord::class.java)
    )
    private val redeemQueueAdapter = moshi.adapter<List<RedeemQueueEntry>>(
        Types.newParameterizedType(List::class.java, RedeemQueueEntry::class.java)
    )

    private val _appState = MutableStateFlow(AppSnapshot())
    val appState: StateFlow<AppSnapshot> = _appState.asStateFlow()

    private var rewardRepository: RewardRepository? = null
    private var transactionRepository: TransactionRepository? = null
    private var leaderboardRepository: LeaderboardRepository? = null
    private var notificationRepository: NotificationRepository? = null
    private var syncRepository: SyncRepository? = null

    init {
        loadOfflineState()
    }

    private fun loadOfflineState() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        prefs.resetDailyLimitIfNeeded(today)
        emitSnapshot(createHandshakeResponseFromPrefs())
    }

    fun refreshFromPreferences() {
        emitSnapshot(createHandshakeResponseFromPrefs())
    }

    fun attachRepositories(
        rewardRepository: RewardRepository,
        transactionRepository: TransactionRepository,
        leaderboardRepository: LeaderboardRepository,
        notificationRepository: NotificationRepository,
        syncRepository: SyncRepository
    ) {
        this.rewardRepository = rewardRepository
        this.transactionRepository = transactionRepository
        this.leaderboardRepository = leaderboardRepository
        this.notificationRepository = notificationRepository
        this.syncRepository = syncRepository
    }

    suspend fun performHandshake(): HandshakeResponse {
        val isVpn = DeviceUtils.isVpnActive(context)
        val isEmulator = DeviceUtils.isRunningOnEmulator()
        val deviceId = prefs.deviceId

        if (isVpn && prefs.trustScore >= 100) {
            prefs.trustScore = (prefs.trustScore - 25).coerceAtLeast(0)
        }
        if (isEmulator && prefs.trustScore >= 75) {
            prefs.trustScore = (prefs.trustScore - 25).coerceAtLeast(0)
        }

        return try {
            val response = withTimeout(8000L) {
                api.handshake(
                    HandshakeRequest(
                        device_id = deviceId,
                        network_mac = "02:00:00:00:00:00",
                        is_vpn_flag = isVpn,
                        is_emulator_flag = isEmulator
                    )
                )
            }
            syncFromHandshake(response)
            response
        } catch (e: TimeoutCancellationException) {
            emitSnapshot(createHandshakeResponseFromPrefs())
            createHandshakeResponseFromPrefs()
        } catch (e: Exception) {
            emitSnapshot(createHandshakeResponseFromPrefs())
            createHandshakeResponseFromPrefs()
        }
    }

    fun getDeviceId(): String = prefs.deviceId
    fun getLocalCoins(): Int = prefs.coinBalance
    fun getLocalTrustScore(): Int = prefs.trustScore

    fun getDisplayName(): String = prefs.displayName ?: "Krypton_Warrior"
    fun getPhotoUrl(): String? = prefs.photoUrl

    fun getMasterUid(): String {
        var uid = prefs.masterUid
        if (uid.isNullOrEmpty()) {
            val allowedChars = ('A'..'Z') + ('0'..'9')
            val part1 = (1..4).map { allowedChars.random() }.joinToString("")
            val part2 = (1..4).map { allowedChars.random() }.joinToString("")
            uid = "KL-$part1-$part2"
            prefs.masterUid = uid
        }
        return uid
    }

    suspend fun performDeviceHandshake(): DeviceHandshakeResponse {
        val deviceId = prefs.deviceId
        return try {
            val response = withTimeout(8000L) {
                api.deviceHandshake(DeviceHandshakeRequest(device_id = deviceId))
            }
            prefs.masterUid = response.master_uid
            prefs.coinBalance = response.coin_balance
            prefs.trustScore = response.trust_score
            prefs.adsWatchedToday = response.ads_today
            prefs.sessionAds = response.session_ads
            emitSnapshot(
                HandshakeResponse(
                    status = response.status,
                    device_id = deviceId,
                    current_balance = response.coin_balance,
                    trust_score = response.trust_score,
                    operational_status = response.operational_status,
                    ads_today = response.ads_today,
                    session_ads = response.session_ads,
                    break_until = response.break_until,
                    daily_cap = 100,
                    session_cap = 15,
                    coins_per_ad = 10,
                    min_redeem = 300
                )
            )
            response
        } catch (e: TimeoutCancellationException) {
            val fallbackUid = getMasterUid()
            emitSnapshot(
                HandshakeResponse(
                    status = "ok",
                    device_id = deviceId,
                    current_balance = prefs.coinBalance,
                    trust_score = prefs.trustScore,
                    operational_status = "ACTIVE",
                    ads_today = prefs.adsWatchedToday,
                    session_ads = prefs.sessionAds,
                    break_until = null,
                    daily_cap = 100,
                    session_cap = 15,
                    coins_per_ad = 10,
                    min_redeem = 300
                )
            )
            DeviceHandshakeResponse(
                status = "success",
                master_uid = fallbackUid,
                coin_balance = prefs.coinBalance,
                trust_score = prefs.trustScore,
                operational_status = "ACTIVE",
                ads_today = prefs.adsWatchedToday,
                session_ads = prefs.sessionAds,
                break_until = null
            )
        } catch (e: Exception) {
            val fallbackUid = getMasterUid()
            emitSnapshot(
                HandshakeResponse(
                    status = "ok",
                    device_id = deviceId,
                    current_balance = prefs.coinBalance,
                    trust_score = prefs.trustScore,
                    operational_status = "ACTIVE",
                    ads_today = prefs.adsWatchedToday,
                    session_ads = prefs.sessionAds,
                    break_until = null,
                    daily_cap = 100,
                    session_cap = 15,
                    coins_per_ad = 10,
                    min_redeem = 300
                )
            )
            DeviceHandshakeResponse(
                status = "success",
                master_uid = fallbackUid,
                coin_balance = prefs.coinBalance,
                trust_score = prefs.trustScore,
                operational_status = "ACTIVE",
                ads_today = prefs.adsWatchedToday,
                session_ads = prefs.sessionAds,
                break_until = null
            )
        }
    }

    suspend fun performUidRecovery(pastedUid: String): UidRecoveryResponse {
        val deviceId = prefs.deviceId
        return try {
            val response = withTimeout(8000L) {
                api.recoverUid(UidRecoveryRequest(device_id = deviceId, pasted_uid = pastedUid))
            }
            if (response.status == "success") {
                response.master_uid?.let { prefs.masterUid = it }
                response.coin_balance?.let { prefs.coinBalance = it }
                response.trust_score?.let { prefs.trustScore = it }
                response.ads_today?.let { prefs.adsWatchedToday = it }
                response.session_ads?.let { prefs.sessionAds = it }
                emitSnapshot(
                    HandshakeResponse(
                        status = "ok",
                        device_id = deviceId,
                        current_balance = prefs.coinBalance,
                        trust_score = prefs.trustScore,
                        operational_status = response.operational_status ?: "ACTIVE",
                        ads_today = prefs.adsWatchedToday,
                        session_ads = prefs.sessionAds,
                        break_until = response.break_until,
                        daily_cap = 100,
                        session_cap = 15,
                        coins_per_ad = 10,
                        min_redeem = 300
                    )
                )
            }
            response
        } catch (e: Exception) {
            UidRecoveryResponse(
                status = "error",
                message = "Network error: ${e.message}",
                master_uid = null,
                coin_balance = null,
                trust_score = null,
                operational_status = null,
                ads_today = null,
                session_ads = null,
                break_until = null
            )
        }
    }

    fun updateProfile(displayName: String, photoUrl: String?) {
        prefs.displayName = displayName
        prefs.photoUrl = photoUrl
        refreshFromPreferences()
    }

    fun deductCoinsOffline(amount: Int) {
        prefs.coinBalance = (prefs.coinBalance - amount).coerceAtLeast(0)
        refreshFromPreferences()
    }

    fun applyAdCompletion(response: AdCompletionResponse) {
        val previousBalance = prefs.coinBalance
        prefs.coinBalance = response.new_balance
        prefs.adsWatchedToday = response.ads_today
        prefs.sessionAds = response.session_ads
        prefs.trustScore = response.trust_score
        prefs.totalAdsWatched = (prefs.totalAdsWatched + 1).coerceAtLeast(0)
        prefs.totalCoinsEarned = (prefs.totalCoinsEarned + response.coins_earned).coerceAtLeast(0)
        transactionRepository?.recordTransaction(
            TransactionRecord(
                id = "txn-${System.currentTimeMillis()}",
                type = "coins_earned",
                amount = response.coins_earned,
                status = "COMPLETED",
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                message = "Coins earned from rewarded ad",
                previousBalance = previousBalance,
                currentBalance = prefs.coinBalance,
                description = "Coins earned from rewarded ad",
                action = "coins_earned"
            )
        )
        refreshFromPreferences()
    }

    fun applyRedemptionSuccess(requestId: Int, coins: Int, payoutValue: Float, status: String) {
        val previousBalance = prefs.coinBalance
        val nextBalance = (prefs.coinBalance - coins).coerceAtLeast(0)
        prefs.coinBalance = nextBalance
        prefs.successfulRedeems = (prefs.successfulRedeems + 1).coerceAtLeast(0)
        transactionRepository?.recordTransaction(
            TransactionRecord(
                id = "req-$requestId",
                type = "redeem",
                amount = coins,
                status = status,
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                message = "Redeem request #$requestId submitted",
                rewardAmount = payoutValue.toInt(),
                previousBalance = previousBalance,
                currentBalance = prefs.coinBalance,
                description = "Reward redeemed for ${coins} coins",
                action = "reward_redeemed"
            )
        )
        refreshFromPreferences()
    }

    fun addTransaction(record: TransactionRecord) {
        val current = readTransactions().toMutableList()
        current.add(0, record)
        prefs.transactionHistoryJson = transactionAdapter.toJson(current)
        refreshFromPreferences()
    }

    fun addRedeemRequest(record: RedeemRequestRecord) {
        val current = readRedeemRequests().toMutableList()
        current.add(0, record)
        prefs.redeemRequestsJson = redeemRequestAdapter.toJson(current)
        refreshFromPreferences()
    }

    fun addRedeemQueueEntry(entry: RedeemQueueEntry) {
        val current = readRedeemQueue().toMutableList()
        if (current.any { it.id == entry.id }) return
        current.add(0, entry)
        prefs.redeemQueueJson = redeemQueueAdapter.toJson(current)
        refreshFromPreferences()
    }

    fun updateRedeemQueueStatus(
        queueId: String,
        status: String,
        position: Int? = null,
        estimatedWait: String? = null
    ) {
        val current = readRedeemQueue().toMutableList()
        val index = current.indexOfFirst { it.id == queueId }
        if (index < 0) return

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val existing = current[index]
        current[index] = existing.copy(
            currentStatus = status,
            processingPosition = position ?: existing.processingPosition,
            estimatedProcessingTime = estimatedWait ?: existing.estimatedProcessingTime,
            updatedAt = timestamp
        )
        prefs.redeemQueueJson = redeemQueueAdapter.toJson(current)
        refreshFromPreferences()
    }

    fun finalizeRedeemQueue(queueId: String, finalStatus: String) {
        updateRedeemQueueStatus(queueId, finalStatus)
    }

    fun getActiveRedeemQueueEntry(): RedeemQueueEntry? = readRedeemQueue().firstOrNull()

    fun updateRedeemQueueEntry(entry: RedeemQueueEntry) {
        val current = readRedeemQueue().toMutableList()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            current[index] = entry
            prefs.redeemQueueJson = redeemQueueAdapter.toJson(current)
            refreshFromPreferences()
        }
    }

    fun getRedeemQueue(): List<RedeemQueueEntry> = readRedeemQueue()

    fun updateNotificationCount(count: Int) {
        prefs.notificationCount = count
        refreshFromPreferences()
    }

    fun getCurrentSnapshot(): AppSnapshot = _appState.value

    fun resetState() {
        prefs.coinBalance = 0
        prefs.adsWatchedToday = 0
        prefs.sessionAds = 0
        prefs.trustScore = 100
        prefs.totalAdsWatched = 0
        prefs.successfulRedeems = 0
        prefs.notificationCount = 0
        prefs.totalCoinsEarned = 0
        prefs.transactionHistoryJson = null
        prefs.redeemRequestsJson = null
        prefs.redeemQueueJson = null
        prefs.masterUid = null
        prefs.displayName = "Krypton_Warrior"
        prefs.photoUrl = null
        refreshFromPreferences()
    }

    private fun createHandshakeResponseFromPrefs(): HandshakeResponse = HandshakeResponse(
        status = "ok",
        device_id = prefs.deviceId,
        current_balance = prefs.coinBalance,
        trust_score = prefs.trustScore,
        operational_status = "ACTIVE",
        ads_today = prefs.adsWatchedToday,
        session_ads = prefs.sessionAds,
        break_until = null,
        daily_cap = 100,
        session_cap = 15,
        coins_per_ad = 10,
        min_redeem = 300
    )

    private fun syncFromHandshake(response: HandshakeResponse) {
        prefs.coinBalance = response.current_balance
        prefs.trustScore = response.trust_score
        prefs.adsWatchedToday = response.ads_today
        prefs.sessionAds = response.session_ads
        emitSnapshot(response)
    }

    private fun emitSnapshot(handshake: HandshakeResponse?) {
        val snapshot = AppSnapshot(
            username = prefs.displayName ?: "Krypton_Warrior",
            userUid = getMasterUid(),
            coinBalance = prefs.coinBalance,
            totalAdsWatched = prefs.totalAdsWatched,
            dailyAdsWatched = prefs.adsWatchedToday,
            successfulRedeems = prefs.successfulRedeems,
            transactionHistory = readTransactions(),
            redeemRequests = readRedeemRequests(),
            redeemStats = buildRedeemQueueStats(readRedeemQueue()),
            trustScore = prefs.trustScore,
            totalCoinsEarned = prefs.totalCoinsEarned,
            notificationCount = prefs.notificationCount,
            adsWatchedToday = handshake?.ads_today ?: prefs.adsWatchedToday,
            sessionAds = handshake?.session_ads ?: prefs.sessionAds,
            dailyCap = handshake?.daily_cap ?: 100,
            sessionCap = handshake?.session_cap ?: 15,
            breakUntil = handshake?.break_until?.toLongOrNull(),
            operationalStatus = handshake?.operational_status ?: "ACTIVE",
            masterUid = prefs.masterUid,
            deviceId = prefs.deviceId,
            displayName = prefs.displayName,
            photoUrl = prefs.photoUrl
        )
        _appState.value = snapshot
        rewardRepository?.refreshRewards(snapshot.coinBalance)
        leaderboardRepository?.refreshLeaderboard(snapshot)
        notificationRepository?.refreshFromSnapshot(snapshot)
        syncRepository?.applyRepositoryChange(snapshot)
    }

    private fun readTransactions(): List<TransactionRecord> {
        val json = prefs.transactionHistoryJson.orEmpty()
        if (json.isEmpty()) return emptyList()
        return try {
            transactionAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readRedeemRequests(): List<RedeemRequestRecord> {
        val json = prefs.redeemRequestsJson.orEmpty()
        if (json.isEmpty()) return emptyList()
        return try {
            redeemRequestAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readRedeemQueue(): List<RedeemQueueEntry> {
        val json = prefs.redeemQueueJson.orEmpty()
        if (json.isEmpty()) return emptyList()
        return try {
            redeemQueueAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildRedeemQueueStats(queue: List<RedeemQueueEntry>): RedeemQueueStats {
        val active = queue.firstOrNull()
        return RedeemQueueStats(
            pendingCount = queue.count { it.currentStatus == "PENDING" },
            queuedCount = queue.count { it.currentStatus == "QUEUED" },
            underReviewCount = queue.count { it.currentStatus == "UNDER REVIEW" },
            approvedCount = queue.count { it.currentStatus == "APPROVED" },
            completedCount = queue.count { it.currentStatus == "COMPLETED" },
            rejectedCount = queue.count { it.currentStatus == "REJECTED" },
            totalLifetimeRedeems = prefs.successfulRedeems,
            lastRedeemAt = active?.updatedAt,
            activeQueueId = active?.id,
            activeQueuePosition = active?.processingPosition,
            activeStatus = active?.currentStatus,
            estimatedWaitText = active?.estimatedProcessingTime
        )
    }

}
