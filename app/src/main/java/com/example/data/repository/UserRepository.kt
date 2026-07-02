package com.example.data.repository

import android.content.Context
import com.kryptoloot.app.BuildConfig
import com.example.data.api.*
import com.example.data.api.RedemptionHistoryItem
import com.example.data.local.DeviceUtils
import com.example.data.local.UserPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val redeemPaymentAdapter = moshi.adapter<List<RedeemPayment>>(
        Types.newParameterizedType(List::class.java, RedeemPayment::class.java)
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
        applyDebugTestBalanceIfNeeded()
        emitSnapshot(createHandshakeResponseFromPrefs())
    }

    fun refreshFromPreferences() {
        emitSnapshot(createHandshakeResponseFromPrefs())
    }

    fun mergeRemoteRedemptions(remote: List<RedemptionHistoryItem>) {
        val existingTransactions = readTransactions()
        val nonRedemptionTransactions = existingTransactions.filter {
            it.transactionType != TransactionType.REDEEM_REQUEST &&
                it.transactionType != TransactionType.REDEEM_PROCESSING &&
                it.transactionType != TransactionType.REDEEM_APPROVED &&
                it.transactionType != TransactionType.REDEEM_REJECTED &&
                it.transactionType != TransactionType.QUEUE_COMPLETED
        }.toMutableList()

        if (remote.isEmpty()) {
            val sortedTransactions = nonRedemptionTransactions.sortedByDescending { it.timestamp }
            prefs.transactionHistoryJson = transactionAdapter.toJson(sortedTransactions)
            transactionRepository?.refreshFromPreferences()
            refreshFromPreferences()
            return
        }

        val mergedRedemptions = remote.map { item ->
            val status = item.status.trim().uppercase()
            val transactionType = when (status) {
                "PENDING", "QUEUED", "PROCESSING" -> TransactionType.REDEEM_REQUEST
                "APPROVED" -> TransactionType.REDEEM_APPROVED
                "COMPLETED", "PAID" -> TransactionType.QUEUE_COMPLETED
                "REJECTED" -> TransactionType.REDEEM_REJECTED
                else -> TransactionType.REDEEM_REQUEST
            }
            val coinsBefore = item.coins_before.coerceAtLeast(0)
            val coinsAfter = item.coins_after.coerceAtLeast(0)
            TransactionRecord(
                id = "txn-${item.request_id}-${item.queue_id ?: "0"}",
                userUid = prefs.masterUid ?: getMasterUid(),
                username = prefs.displayName ?: "Krypton_Warrior",
                transactionType = transactionType,
                type = transactionType.name,
                coinsBefore = coinsBefore,
                amount = item.coin_cost,
                coinsChanged = -item.coin_cost,
                coinsAfter = coinsAfter,
                rewardPack = item.reward_name ?: item.transaction_type,
                cashAmount = item.cash_amount ?: item.payout_value,
                queueId = item.queue_id,
                status = status,
                description = item.description.ifEmpty { item.transaction_type },
                timestamp = item.created_at,
                completedTimestamp = item.completed_at,
                deviceId = item.device_id,
                serverSyncFlag = true,
                versionNumber = item.version_number,
                message = item.description.ifEmpty { "Redeem status: $status" },
                rewardName = item.reward_name,
                rewardAmount = item.cash_amount?.toInt() ?: item.payout_value.toInt(),
                packId = item.queue_id?.toIntOrNull(),
                updatedAt = item.completed_at ?: item.created_at,
                previousBalance = coinsBefore,
                currentBalance = coinsAfter,
                legacyDescription = item.description
            )
        }

        val mergedTransactions = nonRedemptionTransactions.toMutableList()
        mergedRedemptions.sortedByDescending { it.timestamp }.forEach { redemption ->
            val existingIndex = mergedTransactions.indexOfFirst {
                it.queueId == redemption.queueId ||
                    (it.id.filter { char -> char.isDigit() }.toIntOrNull() == redemption.id.filter { char -> char.isDigit() }.toIntOrNull())
            }
            if (existingIndex >= 0) {
                mergedTransactions[existingIndex] = redemption
            } else {
                mergedTransactions.add(0, redemption)
            }
        }

        val sortedTransactions = mergedTransactions.sortedByDescending { it.timestamp }
        prefs.transactionHistoryJson = transactionAdapter.toJson(sortedTransactions)
        transactionRepository?.refreshFromPreferences()
        refreshFromPreferences()
    }

    private fun applyDebugTestBalanceIfNeeded() {
        if (!BuildConfig.DEBUG || prefs.debugTestBalanceApplied) return
        prefs.coinBalance = 300
        prefs.debugTestBalanceApplied = true
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

    fun adjustCoins(amount: Int) {
        prefs.coinBalance = (prefs.coinBalance + amount).coerceAtLeast(0)
        refreshFromPreferences()
    }

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

    fun recordTransaction(record: TransactionRecord) {
        transactionRepository?.recordTransaction(record) ?: addTransaction(record)
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
        recordTransaction(
            TransactionRecord(
                id = "txn-${System.currentTimeMillis()}",
                userUid = getMasterUid(),
                username = prefs.displayName ?: "Krypton_Warrior",
                transactionType = TransactionType.WATCH_REWARD,
                type = TransactionType.WATCH_REWARD.name,
                coinsBefore = previousBalance,
                amount = response.coins_earned,
                coinsChanged = response.coins_earned,
                coinsAfter = prefs.coinBalance,
                status = "COMPLETED",
                description = "Coins earned from rewarded ad",
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                completedTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                deviceId = prefs.deviceId,
                serverSyncFlag = false,
                versionNumber = 1,
                message = "Coins earned from rewarded ad",
                previousBalance = previousBalance,
                currentBalance = prefs.coinBalance,
                legacyDescription = "Coins earned from rewarded ad"
            )
        )
        refreshFromPreferences()
    }

    @Suppress("UNUSED_PARAMETER")
    fun applyRedemptionSuccess(requestId: Int, coins: Int, payoutValue: Float, status: String) {
        val nextBalance = (prefs.coinBalance - coins).coerceAtLeast(0)
        prefs.coinBalance = nextBalance
        refreshFromPreferences()
    }

    fun addTransaction(record: TransactionRecord) {
        val current = readTransactions().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == record.id || (record.queueId != null && it.queueId == record.queueId) }
        if (existingIndex >= 0) {
            current[existingIndex] = record
        } else {
            current.add(0, record)
        }
        prefs.transactionHistoryJson = transactionAdapter.toJson(current)
        refreshFromPreferences()
    }

    fun getRedeemQueue(): List<RedeemQueueEntry> = deriveRedeemQueueEntries(readRedeemPayments())

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
        prefs.notificationCount = 0
        prefs.totalCoinsEarned = 0
        prefs.transactionHistoryJson = null
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
        val transactions = currentTransactions()
        val redeemPayments = readRedeemPayments()
        val redeemRequests = deriveRedeemRequests(redeemPayments)
        val redeemStats = buildRedeemQueueStats(redeemPayments)
        val transactionStats = buildTransactionStatistics(transactions)
        val leaderboardState = leaderboardRepository?.leaderboardState?.value ?: LeaderboardState()
        val snapshot = AppSnapshot(
            username = prefs.displayName ?: "Krypton_Warrior",
            userUid = getMasterUid(),
            coinBalance = prefs.coinBalance,
            totalAdsWatched = prefs.totalAdsWatched,
            dailyAdsWatched = prefs.adsWatchedToday,
            successfulRedeems = redeemPayments.count { it.status == PaymentStatus.COMPLETED },
            transactionHistory = transactions,
            redeemRequests = redeemRequests,
            redeemStats = redeemStats,
            redeemPayments = redeemPayments,
            transactionStats = transactionStats,
            leaderboardState = leaderboardState,
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
        if (_appState.value == snapshot) {
            return
        }
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

    private fun readRedeemPayments(): List<RedeemPayment> {
        val json = prefs.redeemPaymentsJson.orEmpty()
        if (json.isEmpty()) return emptyList()
        return try {
            redeemPaymentAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun deriveRedeemRequests(payments: List<RedeemPayment>): List<RedeemRequestRecord> {
        val activeQueue = payments.filter {
            it.status == PaymentStatus.PENDING || it.status == PaymentStatus.APPROVED
        }.sortedByDescending { it.createdAt }

        return payments.map { payment ->
            val queuePosition = activeQueue.indexOfFirst { it.id == payment.id }.let { if (it >= 0) it + 1 else 0 }
            RedeemRequestRecord(
                id = payment.id,
                userUid = payment.userUid,
                username = payment.username,
                packId = payment.rewardPackId,
                packName = payment.rewardPack,
                coinsUsed = payment.coinsRequired,
                rewardAmount = payment.cashAmount.toInt(),
                status = payment.status.name,
                queuePosition = queuePosition,
                estimatedProcessingTime = when (payment.status) {
                    PaymentStatus.PENDING -> "Processing now"
                    PaymentStatus.APPROVED -> "Approved for payout"
                    PaymentStatus.COMPLETED -> "Completed"
                    PaymentStatus.REJECTED -> "Rejected"
                    PaymentStatus.CANCELLED -> "Cancelled"
                },
                requestTimestamp = payment.createdAt,
                createdAt = payment.createdAt,
                updatedAt = payment.updatedAt
            )
        }
    }

    private fun deriveRedeemQueueEntries(payments: List<RedeemPayment>): List<RedeemQueueEntry> {
        return payments
            .filter { it.status == PaymentStatus.PENDING || it.status == PaymentStatus.APPROVED }
            .sortedByDescending { it.createdAt }
            .mapIndexed { index, payment ->
                RedeemQueueEntry(
                    id = payment.id,
                    userUid = payment.userUid,
                    username = payment.username,
                    packId = payment.rewardPackId,
                    packName = payment.rewardPack,
                    coinCost = payment.coinsRequired,
                    rewardAmount = payment.cashAmount.toInt(),
                    requestTimestamp = payment.createdAt,
                    currentStatus = payment.status.name,
                    processingPosition = index + 1,
                    estimatedProcessingTime = if (index == 0) "Processing now" else "${index} queued ahead",
                    previousBalance = prefs.coinBalance,
                    currentBalance = prefs.coinBalance,
                    createdAt = payment.createdAt,
                    updatedAt = payment.updatedAt
                )
            }
    }

    private fun currentTransactions(): List<TransactionRecord> {
        return transactionRepository?.getTransactionsSnapshot() ?: readTransactions()
    }

    private fun buildTransactionStatistics(records: List<TransactionRecord>): TransactionStatistics {
        val ordered = records.sortedByDescending { it.timestamp }
        val rewardTransactions = records.filter {
            it.transactionType == TransactionType.WATCH_REWARD || it.transactionType == TransactionType.COIN_BONUS
        }
        val redeemTransactions = records.filter {
            it.transactionType == TransactionType.REDEEM_REQUEST ||
                it.transactionType == TransactionType.REDEEM_PROCESSING ||
                it.transactionType == TransactionType.REDEEM_APPROVED ||
                it.transactionType == TransactionType.REDEEM_REJECTED ||
                it.transactionType == TransactionType.QUEUE_COMPLETED
        }.distinctBy { it.queueId ?: it.id }
        val coinsEarned = rewardTransactions.sumOf { it.coinsChanged.coerceAtLeast(0) }
        val coinsRedeemed = redeemTransactions.sumOf { (it.coinsBefore - it.coinsAfter).coerceAtLeast(0) }
        val averageReward = if (rewardTransactions.isNotEmpty()) coinsEarned.toFloat() / rewardTransactions.size.toFloat() else 0f
        val lastRedeem = redeemTransactions.firstOrNull()
        return TransactionStatistics(
            totalTransactions = records.size,
            coinsEarnedLifetime = coinsEarned,
            coinsRedeemedLifetime = coinsRedeemed,
            lastTransactionTime = ordered.firstOrNull()?.timestamp,
            lastRedeemTime = lastRedeem?.completedTimestamp ?: lastRedeem?.timestamp,
            averageCoinsPerReward = averageReward,
            rewardTransactions = rewardTransactions.size,
            redeemTransactions = redeemTransactions.size,
            approvalTransactions = records.count { it.transactionType == TransactionType.REDEEM_APPROVED },
            rejectedTransactions = records.count { it.transactionType == TransactionType.REDEEM_REJECTED },
            systemTransactions = records.count { it.transactionType == TransactionType.SYSTEM }
        )
    }

    private fun buildRedeemQueueStats(payments: List<RedeemPayment>): RedeemQueueStats {
        val queueEntries = deriveRedeemQueueEntries(payments)
        val active = queueEntries.firstOrNull()
        val pending = payments.count { it.status == PaymentStatus.PENDING }
        val approved = payments.count { it.status == PaymentStatus.APPROVED }
        val completed = payments.count { it.status == PaymentStatus.COMPLETED }
        val rejected = payments.count { it.status == PaymentStatus.REJECTED }
        val cancelled = payments.count { it.status == PaymentStatus.CANCELLED }
        val queued = queueEntries.size
        val latestCompletedAt = payments
            .filter { it.status == PaymentStatus.COMPLETED }
            .maxByOrNull { it.updatedAt }
            ?.updatedAt
        return RedeemQueueStats(
            pendingCount = pending,
            queuedCount = queued,
            underReviewCount = payments.count { it.status == PaymentStatus.PENDING && it.deliveryType != RedeemDeliveryType.REDEEM_CODE },
            approvedCount = approved,
            completedCount = completed,
            rejectedCount = rejected + cancelled,
            totalLifetimeRedeems = pending + completed + rejected + cancelled,
            lastRedeemAt = latestCompletedAt ?: active?.updatedAt,
            activeQueueId = active?.id,
            activeQueuePosition = active?.processingPosition,
            activeStatus = active?.currentStatus,
            estimatedWaitText = active?.estimatedProcessingTime
        )
    }

}
