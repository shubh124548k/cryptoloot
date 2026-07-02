package com.example.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.content.Context
import kotlinx.coroutines.delay
import com.example.data.api.*
import com.example.data.local.DeviceUtils
import com.kryptoloot.app.BuildConfig
import android.util.Log

interface RedeemPaymentStorage {
    fun readPayments(): List<RedeemPayment>
    fun writePayments(list: List<RedeemPayment>)
}

class PreferencesRedeemPaymentStorage(
    private val prefs: com.example.data.local.UserPreferences
) : RedeemPaymentStorage {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter<List<RedeemPayment>>(
        Types.newParameterizedType(List::class.java, RedeemPayment::class.java)
    )

    override fun readPayments(): List<RedeemPayment> {
        val json = prefs.redeemPaymentsJson.orEmpty()
        if (json.isBlank()) return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun writePayments(list: List<RedeemPayment>) {
        prefs.redeemPaymentsJson = adapter.toJson(list)
    }
}

class InMemoryRedeemPaymentStorage(
    private val payments: MutableList<RedeemPayment> = mutableListOf()
) : RedeemPaymentStorage {
    override fun readPayments(): List<RedeemPayment> = payments.toList()

    override fun writePayments(list: List<RedeemPayment>) {
        payments.clear()
        payments.addAll(list)
    }
}

class RedeemPaymentRepository(
    private val storage: RedeemPaymentStorage,
    private val api: KryptoLootApi,
    private val userRepo: UserRepository? = null,
    private val notificationRepo: NotificationRepository? = null,
    private val budgetRepository: MonthlyBudgetRepository? = null,
    private val fraudRepository: FraudDetectionRepository? = null,
    private val transactionRepository: TransactionRepository? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _paymentSnapshot = MutableStateFlow(RedeemPaymentSnapshot())
    val paymentSnapshotState: StateFlow<RedeemPaymentSnapshot> = _paymentSnapshot.asStateFlow()

    init {
        refreshFromStorage()
        userRepo?.appState?.onEach { snapshot ->
            refreshFromSnapshot(snapshot)
        }?.launchIn(scope)
    }

    fun paymentSnapshot(): RedeemPaymentSnapshot = _paymentSnapshot.value
    fun paymentFlow(): StateFlow<RedeemPaymentSnapshot> = paymentSnapshotState

    fun refreshFromSnapshot(snapshot: AppSnapshot) {
        val payments = readPayments().sortedByDescending { it.createdAt }
        _paymentSnapshot.value = buildSnapshot(snapshot, payments)
    }

    fun refreshFromStorage() {
        val snapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()
        val payments = readPayments().sortedByDescending { it.createdAt }
        _paymentSnapshot.value = buildSnapshot(snapshot, payments)
    }

    init {
        scope.launch { syncPendingRedeems() }
        // Start a background watcher to retry a single offline redeem when connectivity returns
        scope.launch {
            while (true) {
                try {
                    val ctx = userRepo?.context ?: break
                    val sp = ctx.applicationContext.getSharedPreferences("kryptoloot_prefs", Context.MODE_PRIVATE)
                    val offlineJson = sp.getString("offline_redeem_json", null)
                    if (!offlineJson.isNullOrBlank() && DeviceUtils.isOnline(ctx)) {
                        try {
                            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                            val mapAdapter = moshi.adapter(Map::class.java)
                            val offline = mapAdapter.fromJson(offlineJson) as? Map<*, *> ?: null
                            if (offline != null) {
                                val coins = (offline["coins"] as? Double)?.toInt() ?: (offline["coins"] as? Int) ?: 0
                                val packId = offline["pack_id"] as? String ?: ""
                                val req = RedeemRequest(
                                    device_id = userRepo.getDeviceId(),
                                    coins_to_redeem = coins,
                                    username = userRepo.getDisplayName(),
                                    reward_pack = packId,
                                    coins = coins,
                                    cash_amount = (offline["payout"] as? Double)?.toFloat() ?: 0f,
                                    payment_method = "OFFLINE_RETRY",
                                    payment_details = offline["id"] as? String ?: ""
                                )
                                val resp = try { api.redeem(req) } catch (_: Exception) { null }
                                if (resp != null && resp.success) {
                                    // Update local prefs coin balance and clear offline record
                                    sp.edit().remove("offline_redeem_json").apply()
                                    sp.edit().putInt("coin_balance", resp.coins_remaining).apply()
                                    userRepo.refreshFromPreferences()
                                    // refresh redemption history from backend as the source of truth
                                    try {
                                        val remote = api.getRedemptions(userRepo.getDeviceId())
                                        userRepo.mergeRemoteRedemptions(remote)
                                    } catch (_: Exception) { }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
                delay(5000L)
            }
        }
    }

    suspend fun submitRedeem(
        destination: RedeemDestination,
        rewardPack: RewardPack,
        requestedCoins: Int
    ): PaymentSubmissionResult {
        Log.e("REDEEM_TRACE", "STEP_07_REPO_ENTRY submitRedeem(destination) coins=$requestedCoins pack=${rewardPack.name}")
        val snapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()
        Log.e("REDEEM_TRACE", "STEP_08_SNAPSHOT balance=${snapshot.coinBalance} trust=${snapshot.trustScore} status=${snapshot.operationalStatus} userUid=${snapshot.userUid}")
        val validation = validateSubmission(snapshot, destination, rewardPack, requestedCoins)
        Log.e("REDEEM_TRACE", "STEP_08_VALIDATION isValid=${validation.isValid} errors=[${validation.errors.joinToString("; ")}] message=${validation.message}")
        if (!validation.isValid) {
            Log.e("REDEEM_TRACE", "STEP_08_VALIDATION_FAIL returning error to ViewModel: ${validation.message}")
            return PaymentSubmissionResult(
                payment = null,
                validation = validation,
                success = false,
                message = validation.message.ifEmpty { "Redeem request rejected." }
            )
        }

        val fraudReport = fraudRepository?.buildFraudReport(snapshot) ?: FraudReport()

        // Build the backend request FIRST
        val deviceId = userRepo?.getDeviceId().orEmpty()
        val req = RedeemRequest(
            device_id = deviceId,
            coins_to_redeem = requestedCoins,
            username = snapshot.displayName ?: snapshot.username,
            reward_pack = rewardPack.name,
            coins = requestedCoins,
            cash_amount = rewardPack.rewardAmount.toFloat(),
            payment_method = destination.deliveryType.displayLabel(),
            payment_details = when (destination.deliveryType) {
                RedeemDeliveryType.UPI -> destination.upiId
                RedeemDeliveryType.MOBILE -> destination.mobileNumber
                RedeemDeliveryType.REDEEM_CODE -> destination.redeemCode
            }
        )

        Log.e("REDEEM_TRACE", "STEP_09_HTTP_POST POST /api/v1/rewards/redeem device_id=${req.device_id} coins=${req.coins_to_redeem} method=${req.payment_method} details=${req.payment_details}")

        // Call backend FIRST — only proceed on HTTP 200
        return try {
            val response = api.redeem(req)
            Log.e("REDEEM_TRACE", "STEP_09_HTTP_OK HTTP 200 success=${response.success} transaction_id=${response.transaction_id} request_id=${response.request_id} status=${response.status} message=${response.message} coins_remaining=${response.coins_remaining}")

            if (!response.success) {
                Log.e("REDEEM_TRACE", "STEP_09_SERVER_REJECT server rejected: ${response.message}")
                return PaymentSubmissionResult(
                    payment = null,
                    validation = validation,
                    success = false,
                    message = response.message.ifEmpty { "Redeem request rejected by server." }
                )
            }

            // BACKEND CONFIRMED (HTTP 200, success=true) — now save locally
            Log.e("REDEEM_TRACE", "STEP_09_BACKEND_CONFIRMED saving locally: transaction_id=${response.transaction_id}, request_id=${response.request_id}")
            val now = timestamp()
            val payment = RedeemPayment(
                id = "pay-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}",
                orderId = response.transaction_id,
                userUid = snapshot.userUid.ifEmpty { userRepo?.getMasterUid().orEmpty() },
                username = snapshot.displayName ?: snapshot.username,
                rewardPack = rewardPack.name,
                rewardPackId = rewardPack.id,
                coinsRequired = requestedCoins,
                cashAmount = rewardPack.rewardAmount.toFloat(),
                upiId = destination.upiId?.trim()?.ifBlank { null },
                mobileNumber = destination.mobileNumber?.trim()?.ifBlank { null },
                deliveryType = destination.deliveryType,
                redeemCode = destination.redeemCode?.trim()?.ifBlank { null },
                trustScore = snapshot.trustScore,
                fraudScore = fraudReport.trustScore,
                fraudRisk = fraudReport.riskLevel.name,
                createdAt = now,
                updatedAt = now,
                status = PaymentStatus.PENDING,
                failureReason = null,
                transactionId = response.transaction_id,
                completedAt = null
            )

            // Deduct coins locally (AFTER backend confirmation)
            userRepo?.deductCoinsOffline(requestedCoins)
            val afterBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: (snapshot.coinBalance - requestedCoins).coerceAtLeast(0)
            Log.e("TRACE", "COIN DEDUCTION: before=${snapshot.coinBalance}, deducted=$requestedCoins, after=$afterBalance")

            // Save payment locally
            val updated = readPayments().toMutableList().apply { add(0, payment) }
            persistPayments(updated)
            Log.e("TRACE", "PAYMENT SAVED LOCALLY: ${payment.id} -> backend txn=${response.transaction_id}")

            val maskedDest = when (payment.deliveryType) {
                RedeemDeliveryType.UPI -> payment.upiId?.let { maskMiddle(it) } ?: ""
                RedeemDeliveryType.MOBILE -> payment.mobileNumber?.let { maskMobile(it) } ?: ""
                RedeemDeliveryType.REDEEM_CODE -> payment.redeemCode?.let { it } ?: ""
            }
            val tx = TransactionRecord(
                id = payment.id,
                userUid = payment.userUid,
                username = payment.username,
                transactionType = TransactionType.REDEEM_REQUEST,
                type = TransactionType.REDEEM_REQUEST.name,
                coinsBefore = snapshot.coinBalance,
                amount = payment.coinsRequired,
                coinsChanged = -payment.coinsRequired,
                coinsAfter = afterBalance,
                rewardPack = payment.rewardPack,
                cashAmount = payment.cashAmount,
                queueId = payment.id,
                status = "PENDING",
                description = "Redeem payment submitted for ${payment.rewardPack} via ${destination.displayLabel()}",
                timestamp = now,
                completedTimestamp = null,
                deviceId = snapshot.deviceId,
                serverSyncFlag = true,
                versionNumber = 1,
                message = "Redeem request submitted via ${destination.displayLabel()}",
                rewardName = payment.rewardPack,
                rewardAmount = payment.coinsRequired,
                packId = payment.rewardPackId,
                updatedAt = now,
                previousBalance = snapshot.coinBalance,
                currentBalance = afterBalance,
                legacyDescription = "Redeem payment submitted"
            )
            transactionRepository?.recordTransaction(tx) ?: userRepo?.recordTransaction(tx)
            notificationRepo?.createNotification(
                NotificationItem(
                    id = "pay-${payment.id}-${System.currentTimeMillis()}",
                    relatedId = payment.id,
                    title = "Payment Under Review",
                    message = "Payment is under review.\nRequest ID: ${response.request_id}\nReward Pack: ${payment.rewardPack}\nTimestamp: $now",
                    timestamp = now,
                    priority = NotificationPriority.HIGH,
                    category = NotificationCategory.REDEEM,
                    actionType = NotificationType.COINS_REDEEMED.name,
                    sourceRepository = "REDEEM_PAYMENT_REPOSITORY",
                    severity = "INFO",
                    notificationType = NotificationType.REDEEM_APPROVED
                )
            )
            userRepo?.refreshFromPreferences()
            refreshFromSnapshot(snapshot)

            Log.e("REDEEM_TRACE", "STEP_09_SAVED_LOCAL paymentId=${payment.id} returning success to ViewModel")
            PaymentSubmissionResult(
                payment = payment,
                validation = validation,
                success = true,
                message = "Redeem request submitted successfully.",
                backendRequestId = response.request_id?.toString() ?: response.transaction_id?.filter { it.isDigit() }?.take(9),
                backendTransactionId = response.transaction_id
            )
        } catch (e: retrofit2.HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            Log.e("REDEEM_TRACE", "STEP_09_HTTP_EXCEPTION HttpException code=${e.code()} body=$errorBody")
            PaymentSubmissionResult(
                payment = null,
                validation = validation,
                success = false,
                message = "Server error (${e.code()}): ${errorBody ?: e.message()}"
            )
        } catch (e: Exception) {
            Log.e("REDEEM_TRACE", "STEP_09_EXCEPTION type=${e::class.simpleName} message=${e.message}")
            PaymentSubmissionResult(
                payment = null,
                validation = validation,
                success = false,
                message = "Unable to process redemption: ${e.message ?: "Check connectivity."}"
            )
        }
    }

    fun submitRedeem(payment: RedeemPayment): PaymentSubmissionResult {
        Log.e("TRACE", "REPOSITORY_START RedeemPaymentRepository.submitRedeem(payment)")
        val snapshot = userRepo?.getCurrentSnapshot() ?: AppSnapshot()
        userRepo?.deductCoinsOffline(payment.coinsRequired)
        val afterBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: (snapshot.coinBalance - payment.coinsRequired).coerceAtLeast(0)
        val beforeBalance = snapshot.coinBalance
        val updated = readPayments().toMutableList().apply { add(0, payment) }
        persistPayments(updated)
        val tx = TransactionRecord(
            id = payment.id,
            userUid = payment.userUid,
            username = payment.username,
            transactionType = TransactionType.REDEEM_REQUEST,
            type = TransactionType.REDEEM_REQUEST.name,
            coinsBefore = beforeBalance,
            amount = payment.coinsRequired,
            coinsChanged = -payment.coinsRequired,
            coinsAfter = afterBalance,
            rewardPack = payment.rewardPack,
            cashAmount = payment.cashAmount,
            queueId = payment.id,
            status = "QUEUED",
            description = "Redeem request submitted for ${payment.rewardPack}",
            timestamp = payment.createdAt,
            completedTimestamp = null,
            deviceId = snapshot.deviceId,
            serverSyncFlag = false,
            versionNumber = 1,
            message = "Redeem request submitted",
            rewardName = payment.rewardPack,
            rewardAmount = payment.coinsRequired,
            packId = payment.rewardPackId,
            updatedAt = payment.updatedAt,
            previousBalance = beforeBalance,
            currentBalance = afterBalance,
            legacyDescription = "Redeem payment submitted"
        )
        transactionRepository?.recordTransaction(tx) ?: userRepo?.recordTransaction(tx)
        notificationRepo?.createNotification(
            NotificationItem(
                id = "pay-${payment.id}-${System.currentTimeMillis()}",
                relatedId = payment.id,
                title = "Redeem submitted",
                message = "Request ID: ${payment.id}\nReward Pack: ${payment.rewardPack}\nStatus: PENDING\nTimestamp: ${payment.createdAt}",
                timestamp = payment.createdAt,
                priority = NotificationPriority.HIGH,
                category = NotificationCategory.REDEEM,
                actionType = NotificationType.COINS_REDEEMED.name,
                sourceRepository = "REDEEM_PAYMENT_REPOSITORY",
                severity = "INFO",
                notificationType = NotificationType.REDEEM_APPROVED
            )
        )
        userRepo?.refreshFromPreferences()
        refreshFromSnapshot(snapshot.copy(coinBalance = afterBalance))

        Log.d("TRACE", "RedeemPaymentRepository.submitRedeem(payment) queued payment.rewardPack=${payment.rewardPack} payment.coinsRequired=${payment.coinsRequired} payment.cashAmount=${payment.cashAmount}")
        Log.e("TRACE", "STEP 4 launching coroutine")
        scope.launch {
            Log.e("TRACE", "STEP 5 coroutine started")
            try {
                Log.e("TRACE", "STEP 6 trySyncRedeem entered")
                Log.d("TRACE", "TRACE 3 - before trySyncRedeem")
                val syncedPayment = trySyncRedeem(payment)
                Log.d("TRACE", "TRACE 4 - after trySyncRedeem")
                if (syncedPayment != null && syncedPayment != payment) {
                    val cachedPayments = readPayments().toMutableList()
                    val index = cachedPayments.indexOfFirst { it.id == payment.id }
                    if (index >= 0) {
                        cachedPayments[index] = syncedPayment
                        persistPayments(cachedPayments)
                    }
                }
            } catch (e: Exception) {
                Log.e("TRACE", "TRACE EXCEPTION", e)
            }
        }

        return PaymentSubmissionResult(payment, PaymentValidation(true, emptyList(), "Redeem request submitted successfully."), true, "Redeem request submitted successfully.")
    }

    private suspend fun trySyncRedeem(payment: RedeemPayment): RedeemPayment? {
        Log.e("TRACE", "REPOSITORY_START RedeemPaymentRepository.trySyncRedeem")
        if (payment.transactionId != null || payment.backendRequestId != null) return payment
        Log.e("TRACE", "B checking network")
        if (!isNetworkAvailable()) return null

        val networkAvailable = isNetworkAvailable()
        Log.e("TRACE", "C networkAvailable=" + networkAvailable)
        if (!networkAvailable) {
            Log.e("TRACE", "D returning because network unavailable")
            return null
        }

        return try {
            val req = RedeemRequest(
                device_id = userRepo?.getDeviceId().orEmpty(),
                coins_to_redeem = payment.coinsRequired,
                username = payment.username,
                reward_pack = payment.rewardPack,
                coins = payment.coinsRequired,
                cash_amount = payment.cashAmount,
                payment_method = payment.deliveryType.displayLabel(),
                payment_details = buildPaymentDetails(payment)
            )
            val baseUrl = try {
                (api as? retrofit2.Retrofit?)?.baseUrl()?.toString() ?: BuildConfig.BASE_URL
            } catch (_: Exception) {
                BuildConfig.BASE_URL
            }
            val fullUrl = "${baseUrl}api/v1/rewards/redeem"
            val requestJson = try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(RedeemRequest::class.java)
                adapter.toJson(req)
            } catch (_: Exception) {
                "<json-serialization-failed>"
            }

            Log.e("TRACE", "REQUEST START")
            Log.e("TRACE", "REQUEST URL=$fullUrl")
            Log.e("TRACE", "REQUEST BODY=$requestJson")
            Log.e("TRACE", "BASE_URL=$baseUrl")
            Log.e("TRACE", "REQUEST DEVICE_ID=${req.device_id}")

            Log.e("TRACE", "API_CALL_START RedeemPaymentRepository.trySyncRedeem")
            val response = api.redeem(req)
            Log.e("TRACE", "RESPONSE CODE=200")
            Log.e("TRACE", "RESPONSE BODY=${response}")
            Log.e("TRACE", "RESPONSE SUCCESS=${response.success}")
            Log.e("TRACE", "RESPONSE transaction_id=${response.transaction_id}")
            Log.e("TRACE", "RESPONSE request_id=${response.request_id}")
            Log.e("TRACE", "RESPONSE status=${response.status}")
            Log.e("TRACE", "RESPONSE message=${response.message}")
            if (!response.success) return payment

            payment.copy(
                transactionId = response.transaction_id,
                backendRequestId = response.request_id,
                status = mapBackendStatus(response.status),
                updatedAt = timestamp(),
                estimatedDelivery = response.estimated_delivery
            )
        } catch (e: retrofit2.HttpException) {
            Log.e("TRACE", "NETWORK ERROR HttpException")
            Log.e("TRACE", "HTTP status code=${e.code()}")
            Log.e("TRACE", "Failure message=${e.message()}")
            Log.e("TRACE", "Exception stacktrace", e)
            Log.e("TRACE", "Timeout=false")
            Log.e("TRACE", "IOException=false")
            Log.e("TRACE", "UnknownHostException=false")
            Log.e("TRACE", "ConnectException=false")
            payment
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("TRACE", "NETWORK ERROR SocketTimeoutException")
            Log.e("TRACE", "Failure message=${e.message}")
            Log.e("TRACE", "Exception stacktrace", e)
            Log.e("TRACE", "Timeout=true")
            Log.e("TRACE", "IOException=false")
            Log.e("TRACE", "UnknownHostException=false")
            Log.e("TRACE", "ConnectException=false")
            payment
        } catch (e: java.io.IOException) {
            Log.e("TRACE", "NETWORK ERROR IOException")
            Log.e("TRACE", "Failure message=${e.message}")
            Log.e("TRACE", "Exception stacktrace", e)
            Log.e("TRACE", "Timeout=${e is java.net.SocketTimeoutException}")
            Log.e("TRACE", "IOException=true")
            Log.e("TRACE", "UnknownHostException=${e is java.net.UnknownHostException}")
            Log.e("TRACE", "ConnectException=${e is java.net.ConnectException}")
            payment
        } catch (e: java.net.UnknownHostException) {
            Log.e("TRACE", "NETWORK ERROR UnknownHostException")
            Log.e("TRACE", "Failure message=${e.message}")
            Log.e("TRACE", "Exception stacktrace", e)
            Log.e("TRACE", "Timeout=false")
            Log.e("TRACE", "IOException=true")
            Log.e("TRACE", "UnknownHostException=true")
            Log.e("TRACE", "ConnectException=false")
            payment
        } catch (e: java.net.ConnectException) {
            Log.e("TRACE", "NETWORK ERROR ConnectException")
            Log.e("TRACE", "Failure message=${e.message}")
            Log.e("TRACE", "Exception stacktrace", e)
            Log.e("TRACE", "Timeout=false")
            Log.e("TRACE", "IOException=true")
            Log.e("TRACE", "UnknownHostException=false")
            Log.e("TRACE", "ConnectException=true")
            payment
        } catch (e: Exception) {
            Log.e("TRACE", "NETWORK ERROR")
            Log.e("TRACE", "Failure message=${e.message}")
            Log.e("TRACE", "Exception stacktrace", e)
            Log.e("TRACE", "Timeout=${e is java.net.SocketTimeoutException}")
            Log.e("TRACE", "IOException=${e is java.io.IOException}")
            Log.e("TRACE", "UnknownHostException=${e is java.net.UnknownHostException}")
            Log.e("TRACE", "ConnectException=${e is java.net.ConnectException}")
            payment
        }
    }

    private fun buildPaymentDetails(payment: RedeemPayment): String? {
        return when (payment.deliveryType) {
            RedeemDeliveryType.UPI -> payment.upiId
            RedeemDeliveryType.MOBILE -> payment.mobileNumber
            RedeemDeliveryType.REDEEM_CODE -> payment.redeemCode
        }
    }

    private fun mapBackendStatus(rawStatus: String): PaymentStatus {
        return when (rawStatus.trim().uppercase()) {
            "QUEUED", "PENDING" -> PaymentStatus.PENDING
            "APPROVED", "PROCESSING" -> PaymentStatus.APPROVED
            "REJECTED" -> PaymentStatus.REJECTED
            "COMPLETED", "PAID" -> PaymentStatus.COMPLETED
            else -> PaymentStatus.PENDING
        }
    }

    fun rejectRedeem(paymentId: String, reason: String = "Rejected by support"): PaymentSubmissionResult {
        val current = readPayments().toMutableList()
        val index = current.indexOfFirst { it.id == paymentId }
        if (index < 0) {
            return PaymentSubmissionResult(null, PaymentValidation(false, listOf("Payment not found."), "Payment not found."), false, "Payment not found.")
        }
        val payment = current[index]
        val now = timestamp()
        val updatedPayment = payment.copy(status = PaymentStatus.REJECTED, updatedAt = now, failureReason = reason)
        current[index] = updatedPayment
        persistPayments(current)
        transactionRepository?.recordTransaction(
            TransactionRecord(
                id = updatedPayment.id,
                userUid = updatedPayment.userUid,
                username = updatedPayment.username,
                transactionType = TransactionType.REDEEM_REJECTED,
                type = TransactionType.REDEEM_REJECTED.name,
                coinsBefore = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                amount = updatedPayment.coinsRequired,
                coinsChanged = 0,
                coinsAfter = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                rewardPack = updatedPayment.rewardPack,
                cashAmount = updatedPayment.cashAmount,
                queueId = updatedPayment.id,
                status = "REJECTED",
                description = "Redeem rejected: $reason",
                timestamp = now,
                completedTimestamp = now,
                deviceId = userRepo?.getCurrentSnapshot()?.deviceId,
                serverSyncFlag = false,
                versionNumber = 1,
                message = reason,
                rewardName = updatedPayment.rewardPack,
                rewardAmount = updatedPayment.coinsRequired,
                packId = updatedPayment.rewardPackId,
                updatedAt = now,
                previousBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                currentBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                legacyDescription = "Redeem rejected"
            )
        ) ?: userRepo?.recordTransaction(
            TransactionRecord(
                id = updatedPayment.id,
                userUid = updatedPayment.userUid,
                username = updatedPayment.username,
                transactionType = TransactionType.REDEEM_REJECTED,
                type = TransactionType.REDEEM_REJECTED.name,
                coinsBefore = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                amount = updatedPayment.coinsRequired,
                coinsChanged = 0,
                coinsAfter = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                rewardPack = updatedPayment.rewardPack,
                cashAmount = updatedPayment.cashAmount,
                queueId = updatedPayment.id,
                status = "REJECTED",
                description = "Redeem rejected: $reason",
                timestamp = now,
                completedTimestamp = now,
                deviceId = userRepo?.getCurrentSnapshot()?.deviceId,
                serverSyncFlag = false,
                versionNumber = 1,
                message = reason,
                rewardName = updatedPayment.rewardPack,
                rewardAmount = updatedPayment.coinsRequired,
                packId = updatedPayment.rewardPackId,
                updatedAt = now,
                previousBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                currentBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                legacyDescription = "Redeem rejected"
            )
        )
        notificationRepo?.createNotification(
            NotificationItem(
                id = "pay-reject-${System.currentTimeMillis()}",
                relatedId = updatedPayment.id,
                title = "Redeem rejected",
                message = reason,
                timestamp = now,
                priority = NotificationPriority.CRITICAL,
                category = NotificationCategory.REDEEM,
                actionType = NotificationType.REDEEM_REJECTED.name,
                sourceRepository = "REDEEM_PAYMENT_REPOSITORY",
                severity = "WARNING",
                notificationType = NotificationType.REDEEM_REJECTED
            )
        )
        userRepo?.refreshFromPreferences()
        refreshFromStorage()
        return PaymentSubmissionResult(updatedPayment, PaymentValidation(true, emptyList(), "Redeem rejected."), true, "Redeem rejected.")
    }

    fun completeRedeem(paymentId: String, completion: PaymentCompletion): PaymentSubmissionResult {
        val current = readPayments().toMutableList()
        val index = current.indexOfFirst { it.id == paymentId }
        if (index < 0) {
            return PaymentSubmissionResult(null, PaymentValidation(false, listOf("Payment not found."), "Payment not found."), false, "Payment not found.")
        }
        val payment = current[index]
        val now = timestamp()
        val updatedPayment = payment.copy(
            status = PaymentStatus.COMPLETED,
            updatedAt = now,
            transactionId = completion.transactionId.ifBlank { payment.transactionId },
            completedAt = now,
            failureReason = null,
            paymentNote = completion.paymentNote ?: payment.paymentNote
        )
        current[index] = updatedPayment
        persistPayments(current)
        transactionRepository?.recordTransaction(
            TransactionRecord(
                id = updatedPayment.id,
                userUid = updatedPayment.userUid,
                username = updatedPayment.username,
                transactionType = TransactionType.QUEUE_COMPLETED,
                type = TransactionType.QUEUE_COMPLETED.name,
                coinsBefore = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                amount = updatedPayment.coinsRequired,
                coinsChanged = 0,
                coinsAfter = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                rewardPack = updatedPayment.rewardPack,
                cashAmount = updatedPayment.cashAmount,
                queueId = updatedPayment.id,
                status = "COMPLETED",
                description = "Payment sent for ${updatedPayment.rewardPack}",
                timestamp = now,
                completedTimestamp = now,
                deviceId = userRepo?.getCurrentSnapshot()?.deviceId,
                serverSyncFlag = false,
                versionNumber = 1,
                message = completion.paymentNote?.ifBlank { "Payment completed" } ?: "Payment completed",
                rewardName = updatedPayment.rewardPack,
                rewardAmount = updatedPayment.coinsRequired,
                packId = updatedPayment.rewardPackId,
                updatedAt = now,
                previousBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                currentBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                legacyDescription = "Payment sent"
            )
        ) ?: userRepo?.recordTransaction(
            TransactionRecord(
                id = updatedPayment.id,
                userUid = updatedPayment.userUid,
                username = updatedPayment.username,
                transactionType = TransactionType.QUEUE_COMPLETED,
                type = TransactionType.QUEUE_COMPLETED.name,
                coinsBefore = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                amount = updatedPayment.coinsRequired,
                coinsChanged = 0,
                coinsAfter = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                rewardPack = updatedPayment.rewardPack,
                cashAmount = updatedPayment.cashAmount,
                queueId = updatedPayment.id,
                status = "COMPLETED",
                description = "Payment sent for ${updatedPayment.rewardPack}",
                timestamp = now,
                completedTimestamp = now,
                deviceId = userRepo?.getCurrentSnapshot()?.deviceId,
                serverSyncFlag = false,
                versionNumber = 1,
                message = completion.paymentNote?.ifBlank { "Payment completed" } ?: "Payment completed",
                rewardName = updatedPayment.rewardPack,
                rewardAmount = updatedPayment.coinsRequired,
                packId = updatedPayment.rewardPackId,
                updatedAt = now,
                previousBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                currentBalance = userRepo?.getCurrentSnapshot()?.coinBalance ?: 0,
                legacyDescription = "Payment sent"
            )
        )
        notificationRepo?.createNotification(
            NotificationItem(
                id = "pay-complete-${System.currentTimeMillis()}",
                relatedId = updatedPayment.id,
                title = "Payment sent",
                message = completion.paymentNote?.ifBlank { "Your payment has been completed." } ?: "Your payment has been completed.",
                timestamp = now,
                priority = NotificationPriority.HIGH,
                category = NotificationCategory.REDEEM,
                actionType = NotificationType.REDEEM_APPROVED.name,
                sourceRepository = "REDEEM_PAYMENT_REPOSITORY",
                severity = "INFO",
                notificationType = NotificationType.REDEEM_APPROVED
            )
        )
        userRepo?.refreshFromPreferences()
        refreshFromStorage()
        return PaymentSubmissionResult(updatedPayment, PaymentValidation(true, emptyList(), "Payment completed."), true, "Payment completed.")
    }

    fun cancelRedeem(paymentId: String, reason: String = "Cancelled by support"): PaymentSubmissionResult {
        val current = readPayments().toMutableList()
        val index = current.indexOfFirst { it.id == paymentId }
        if (index < 0) {
            return PaymentSubmissionResult(null, PaymentValidation(false, listOf("Payment not found."), "Payment not found."), false, "Payment not found.")
        }
        val payment = current[index]
        if (payment.status == PaymentStatus.COMPLETED) {
            return PaymentSubmissionResult(payment, PaymentValidation(false, listOf("Payment already completed."), "Payment already completed."), false, "Payment already completed.")
        }
        val now = timestamp()
        val updatedPayment = payment.copy(status = PaymentStatus.CANCELLED, updatedAt = now, failureReason = reason)
        current[index] = updatedPayment
        persistPayments(current)
        notificationRepo?.createNotification(
            NotificationItem(
                id = "pay-cancel-${System.currentTimeMillis()}",
                relatedId = updatedPayment.id,
                title = "Redeem cancelled",
                message = reason,
                timestamp = now,
                priority = NotificationPriority.HIGH,
                category = NotificationCategory.REDEEM,
                actionType = NotificationType.REDEEM_REJECTED.name,
                sourceRepository = "REDEEM_PAYMENT_REPOSITORY",
                severity = "WARNING",
                notificationType = NotificationType.REDEEM_REJECTED
            )
        )
        userRepo?.refreshFromPreferences()
        refreshFromStorage()
        return PaymentSubmissionResult(updatedPayment, PaymentValidation(true, emptyList(), "Redeem cancelled."), true, "Redeem cancelled.")
    }

    fun paymentStatistics(): PaymentStatistics = paymentSnapshot().statistics
    fun pendingPayments(): List<RedeemPayment> = paymentSnapshot().pendingPayments
    fun completedPayments(): List<RedeemPayment> = paymentSnapshot().completedPayments
    fun failedPayments(): List<RedeemPayment> = paymentSnapshot().failedPayments

    fun clearAllPayments() {
        persistPayments(emptyList())
    }

    private fun validateSubmission(
        snapshot: AppSnapshot,
        destination: RedeemDestination,
        rewardPack: RewardPack,
        requestedCoins: Int
    ): PaymentValidation {
        Log.e("REDEEM_TRACE", "STEP_08A_VALIDATE balance=${snapshot.coinBalance} requested=$requestedCoins trust=${snapshot.trustScore} status=${snapshot.operationalStatus} dest=${destination.deliveryType}")
        val errors = mutableListOf<String>()
        val availablePack = RewardPackCatalog.fromBalance(snapshot.coinBalance).firstOrNull { it.id == rewardPack.id }
        if (availablePack == null) {
            Log.e("REDEEM_TRACE", "STEP_08A_CHECK pack_unavailable: coinBalance=${snapshot.coinBalance} packId=${rewardPack.id}")
            errors += "Reward pack is unavailable."
        }
        if (requestedCoins <= 0) {
            Log.e("REDEEM_TRACE", "STEP_08A_CHECK coins_zero_or_negative: requestedCoins=$requestedCoins")
            errors += "Coins must be greater than zero."
        }
        if (requestedCoins != rewardPack.requiredCoins) {
            Log.e("REDEEM_TRACE", "STEP_08A_CHECK coins_mismatch: requestedCoins=$requestedCoins packRequired=${rewardPack.requiredCoins}")
            errors += "Requested coin amount does not match the selected reward pack."
        }
        if (snapshot.coinBalance < requestedCoins) {
            Log.e("REDEEM_TRACE", "STEP_08A_CHECK insufficient_balance: balance=${snapshot.coinBalance} requested=$requestedCoins")
            errors += "Insufficient coin balance."
        }
        if (snapshot.operationalStatus != "ACTIVE") {
            Log.e("REDEEM_TRACE", "STEP_08A_CHECK account_inactive: status=${snapshot.operationalStatus}")
            errors += "User account is not active."
        }
        if (!destination.isValid()) {
            Log.e("REDEEM_TRACE", "STEP_08A_CHECK invalid_dest: upi=${destination.upiId} mobile=${destination.mobileNumber} type=${destination.deliveryType}")
            errors += "Provide a valid ${destination.deliveryType.name.lowercase(Locale.US)} destination."
        }
        if (!com.kryptoloot.app.BuildConfig.DEBUG) {
            if (snapshot.trustScore < 70) {
                Log.e("REDEEM_TRACE", "STEP_08A_CHECK trust_low: trust=${snapshot.trustScore} required=70")
                errors += "Trust score is below the required threshold."
            }
            val pendingDuplicates = readPayments().count { it.status == PaymentStatus.PENDING && it.rewardPackId == rewardPack.id }
            if (pendingDuplicates > 0) {
                Log.e("REDEEM_TRACE", "STEP_08A_CHECK pending_duplicate: count=$pendingDuplicates")
                errors += "A pending redeem for this reward already exists."
            }
        }
        val fraudReport = fraudRepository?.buildFraudReport(snapshot) ?: FraudReport()
        if (!com.kryptoloot.app.BuildConfig.DEBUG) {
            if (fraudReport.riskLevel == FraudRiskLevel.HIGH || fraudReport.riskLevel == FraudRiskLevel.CRITICAL) {
                Log.e("REDEEM_TRACE", "STEP_08A_CHECK fraud_high: risk=${fraudReport.riskLevel}")
                errors += "Fraud risk is too high for this request."
            }
        }
        if (!com.kryptoloot.app.BuildConfig.DEBUG) {
            val todayCount = readPayments().count { payment ->
                payment.createdAt.startsWith(dateLabel()) && payment.status != PaymentStatus.COMPLETED && payment.status != PaymentStatus.CANCELLED && payment.status != PaymentStatus.REJECTED
            }
            if (todayCount >= 3) {
                Log.e("REDEEM_TRACE", "STEP_08A_CHECK daily_limit: todayCount=$todayCount")
                errors += "Daily redeem limit reached."
            }
            val monthCount = readPayments().count { payment ->
                payment.createdAt.startsWith(monthLabel()) && payment.status != PaymentStatus.COMPLETED && payment.status != PaymentStatus.CANCELLED && payment.status != PaymentStatus.REJECTED
            }
            if (monthCount >= 10) {
                Log.e("REDEEM_TRACE", "STEP_08A_CHECK monthly_limit: monthCount=$monthCount")
                errors += "Monthly redeem limit reached."
            }
        }
        if (!com.kryptoloot.app.BuildConfig.DEBUG) {
            val budget = budgetRepository?.calculateBudget(snapshot) ?: MonthlyBudgetSnapshot()
            val projectedLiability = budget.projection.outstandingLiability + rewardPack.rewardAmount.toFloat()
            if (projectedLiability > budget.monthlyBudget.monthlyBudget.toFloat() * 1.1f) {
                Log.e("REDEEM_TRACE", "STEP_08A_CHECK budget_exceeded: projected=$projectedLiability limit=${budget.monthlyBudget.monthlyBudget.toFloat() * 1.1f}")
                errors += "Monthly budget cannot support this payout."
            }
        }
        val message = if (errors.isEmpty()) "Validation successful." else errors.joinToString(separator = "; ")
        Log.e("REDEEM_TRACE", "STEP_08A_RESULT isValid=${errors.isEmpty()} errorCount=${errors.size} message=$message")
        return PaymentValidation(
            isValid = errors.isEmpty(),
            errors = errors,
            message = message,
            trustScore = snapshot.trustScore,
            riskLevel = fraudReport.riskLevel.name
        )
    }

    private fun readPayments(): List<RedeemPayment> = storage.readPayments().sortedByDescending { it.createdAt }

    private fun persistPayments(list: List<RedeemPayment>) {
        val ordered = list.sortedByDescending { it.createdAt }
        storage.writePayments(ordered)
        refreshFromStorage()
    }

    private fun buildSnapshot(snapshot: AppSnapshot, payments: List<RedeemPayment>): RedeemPaymentSnapshot {
        val pending = payments.filter { it.status == PaymentStatus.PENDING || it.status == PaymentStatus.APPROVED }
        val completed = payments.filter { it.status == PaymentStatus.COMPLETED }
        val failed = payments.filter { it.status == PaymentStatus.REJECTED || it.status == PaymentStatus.CANCELLED }
        val stats = PaymentStatistics(
            totalPayments = payments.size,
            pendingCount = pending.size,
            approvedCount = payments.count { it.status == PaymentStatus.APPROVED },
            rejectedCount = payments.count { it.status == PaymentStatus.REJECTED },
            completedCount = completed.size,
            cancelledCount = payments.count { it.status == PaymentStatus.CANCELLED },
            failedCount = failed.size,
            totalCashAmount = payments.sumOf { it.cashAmount.toDouble() }.toFloat(),
            totalCoinsUsed = payments.sumOf { it.coinsRequired }
        )
        return RedeemPaymentSnapshot(
            payments = payments,
            pendingPayments = pending,
            completedPayments = completed,
            failedPayments = failed,
            statistics = stats,
            latestPayment = payments.firstOrNull()
        )
    }

    private suspend fun syncPendingRedeems() {
        try {
            val pending = readPayments().filter { it.status == PaymentStatus.PENDING || it.status == PaymentStatus.APPROVED }
            if (pending.isEmpty()) return
            val cachedPayments = readPayments().toMutableList()
            var changed = false
            for (payment in pending) {
                val syncedPayment = trySyncRedeem(payment) ?: continue
                val index = cachedPayments.indexOfFirst { it.id == syncedPayment.id }
                if (index >= 0 && cachedPayments[index] != syncedPayment) {
                    cachedPayments[index] = syncedPayment
                    changed = true
                }
            }
            if (changed) {
                persistPayments(cachedPayments)
            }
        } catch (_: Exception) {
        }
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    private fun dateLabel(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private fun monthLabel(): String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    private fun maskMiddle(value: String): String {
        val s = value.trim()
        if (s.length <= 4) return "****"
        val start = s.take(2)
        val end = s.takeLast(2)
        return "$start****$end"
    }

    private fun maskMobile(value: String): String {
        val s = value.trim()
        if (s.length != 10) return "**********"
        return "${s.take(2)}******${s.takeLast(2)}"
    }

    // === POLLING SUPPORT FOR LIVE STATUS UPDATES ===
    
    private var pollingJob: kotlinx.coroutines.Job? = null
    private val _isPollingActive = MutableStateFlow(false)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()

    /**
     * Start polling for redemption status updates
     * Useful when history screen is active to show live updates
     * Polls every 30 seconds for any status changes
     */
    fun startStatusPolling() {
        if (pollingJob?.isActive == true) return
        _isPollingActive.value = true
        pollingJob = scope.launch {
            while (_isPollingActive.value) {
                try {
                    pollRemoteRedemptions()
                    delay(30000L) // Poll every 30 seconds
                } catch (e: Exception) {
                    Log.e("RedeemPolling", "Error during polling: ${e.message}")
                    delay(30000L)
                }
            }
        }
    }

    /**
     * Stop polling for status updates
     * Call when history screen is closed to save battery/bandwidth
     */
    fun stopStatusPolling() {
        _isPollingActive.value = false
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Manual refresh of redemption status
     * Fetches latest status from backend and updates local cache
     */
    suspend fun refreshRedemptionStatus(): Boolean {
        return try {
            pollRemoteRedemptions()
            true
        } catch (e: Exception) {
            Log.e("RedeemRefresh", "Error refreshing status: ${e.message}")
            false
        }
    }

    /**
     * Internal: Poll remote API for all redemptions and update local cache
     */
    private suspend fun pollRemoteRedemptions() {
        try {
            if (!isNetworkAvailable()) {
                Log.d("SYNC_TRACE", "pollRemoteRedemptions: no network, skipping")
                return
            }
            
            val deviceId = userRepo?.getDeviceId() ?: return
            val remoteRedemptions = try {
                api.getRedemptions(deviceId)
            } catch (e: Exception) {
                Log.e("RedeemPolling", "API call failed: ${e.message}")
                Log.d("SYNC_TRACE", "pollRemoteRedemptions: API call FAILED: ${e.message}")
                return
            }

            Log.d("SYNC_TRACE", "pollRemoteRedemptions: API returned ${remoteRedemptions.size} items")
            if (remoteRedemptions.isEmpty()) {
                Log.d("SYNC_TRACE", "pollRemoteRedemptions: empty response, skipping")
                return
            }
            remoteRedemptions.forEach { item ->
                Log.d("SYNC_TRACE", "pollRemoteRedemptions:   remote queue_id=${item.queue_id} status=${item.status} admin_reply='${item.admin_reply}'")
            }

            // Merge remote data with local cache
            val localPayments = readPayments().toMutableList()
            Log.d("SYNC_TRACE", "pollRemoteRedemptions: localPayments.size=${localPayments.size}")
            var hasChanges = false

            for (remoteItem in remoteRedemptions) {
                // Find matching local payment by transaction ID
                val remoteTransactionId = remoteItem.queue_id ?: remoteItem.request_id.toString()
                val localIndex = localPayments.indexOfFirst {
                    it.transactionId == remoteTransactionId ||
                    it.orderId == remoteTransactionId ||
                    it.transactionId == remoteItem.request_id.toString() ||
                    it.orderId == remoteItem.request_id.toString()
                }

                Log.d("SYNC_TRACE", "pollRemoteRedemptions: matching remote='$remoteTransactionId' localIndex=$localIndex")
                if (localIndex >= 0) {
                    val localPayment = localPayments[localIndex]
                    val newStatus = parsePaymentStatus(remoteItem.status)
                    
                    // Check if any fields have changed
                    if (localPayment.status != newStatus ||
                        localPayment.completedAt != remoteItem.completed_at ||
                        localPayment.failureReason != null) {
                        
                        // Update local payment with remote data
                        localPayments[localIndex] = localPayment.copy(
                            status = newStatus,
                            transactionId = remoteTransactionId ?: localPayment.transactionId,
                            completedAt = remoteItem.completed_at ?: localPayment.completedAt,
                            failureReason = localPayment.failureReason,
                            approvedAt = localPayment.approvedAt,
                            updatedAt = localPayment.updatedAt,
                            estimatedDelivery = localPayment.estimatedDelivery
                        )
                        hasChanges = true

                        if (localPayment.status != newStatus) {
                            val title = when (newStatus) {
                                PaymentStatus.APPROVED -> "Payment Processing"
                                PaymentStatus.COMPLETED -> "Payment Completed"
                                PaymentStatus.REJECTED -> "Payment Rejected"
                                else -> "Redeem Updated"
                            }
                            val message = when (newStatus) {
                                PaymentStatus.APPROVED -> "Your payment is being processed."
                                PaymentStatus.COMPLETED -> {
                                    val adminMsg = remoteItem.admin_reply
                                    if (!adminMsg.isNullOrBlank()) "Payment completed.\nAdmin Reply: $adminMsg"
                                    else "Payment completed."
                                }
                                PaymentStatus.REJECTED -> "Your redeem was rejected."
                                else -> "Your redeem status changed."
                            }
                            notificationRepo?.createNotification(
                                NotificationItem(
                                    id = "poll-${localPayment.id}-${System.currentTimeMillis()}",
                                    relatedId = localPayment.id,
                                    title = title,
                                    message = message,
                                    timestamp = timestamp(),
                                    priority = if (newStatus == PaymentStatus.REJECTED) NotificationPriority.CRITICAL else NotificationPriority.HIGH,
                                    category = NotificationCategory.REDEEM,
                                    actionType = NotificationType.REDEEM_APPROVED.name,
                                    sourceRepository = "REDEEM_PAYMENT_REPOSITORY",
                                    severity = "INFO",
                                    notificationType = NotificationType.REDEEM_APPROVED
                                )
                            )
                        }
                        
                        // Log status change
                        Log.d("RedeemPolling", "Status update: ${localPayment.id} ${localPayment.status} → $newStatus")
                    }
                }
            }

            Log.d("SYNC_TRACE", "pollRemoteRedemptions: hasChanges=$hasChanges (${remoteRedemptions.size} remote items, ${localPayments.size} local payments)")
            if (hasChanges) {
                Log.d("SYNC_TRACE", "pollRemoteRedemptions: calling mergeRemoteRedemptions with ${remoteRedemptions.size} items")
                persistPayments(localPayments)
                userRepo?.mergeRemoteRedemptions(remoteRedemptions)
                Log.d("RedeemPolling", "Local cache updated with ${localPayments.size} redemptions")
            } else {
                Log.d("SYNC_TRACE", "pollRemoteRedemptions: NO CHANGES detected, mergeRemoteRedemptions NOT called")
            }
        } catch (e: Exception) {
            Log.e("RedeemPolling", "Poll error: ${e.message}", e)
            Log.d("SYNC_TRACE", "pollRemoteRedemptions: EXCEPTION ${e::class.simpleName}: ${e.message}")
        }
    }

    private fun parsePaymentStatus(remoteStatus: String?): PaymentStatus {
        return when (remoteStatus?.uppercase()) {
            "PENDING" -> PaymentStatus.PENDING
            "PROCESSING" -> PaymentStatus.APPROVED
            "COMPLETED" -> PaymentStatus.COMPLETED
            "REJECTED" -> PaymentStatus.REJECTED
            else -> PaymentStatus.PENDING
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            userRepo?.context?.let { DeviceUtils.isOnline(it) } ?: false
        } catch (_: Exception) {
            false
        }
    }
}

data class RedeemPayment(
    val id: String,
    val orderId: String,
    val userUid: String,
    val username: String,
    val rewardPack: String,
    val rewardPackId: Int,
    val coinsRequired: Int,
    val cashAmount: Float,
    val upiId: String? = null,
    val mobileNumber: String? = null,
    val deliveryType: RedeemDeliveryType = RedeemDeliveryType.UPI,
    val redeemCode: String? = null,
    val trustScore: Int,
    val fraudScore: Int,
    val fraudRisk: String,
    val createdAt: String,
    val updatedAt: String,
    val status: PaymentStatus,
    val transactionId: String? = null,
    val backendRequestId: Int? = null,
    val estimatedDelivery: String? = null,
    val completedAt: String? = null,
    val failureReason: String? = null,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val paymentNote: String? = null
)

data class RedeemPaymentSnapshot(
    val payments: List<RedeemPayment> = emptyList(),
    val pendingPayments: List<RedeemPayment> = emptyList(),
    val completedPayments: List<RedeemPayment> = emptyList(),
    val failedPayments: List<RedeemPayment> = emptyList(),
    val statistics: PaymentStatistics = PaymentStatistics(),
    val latestPayment: RedeemPayment? = null
)

data class RedeemDestination(
    val upiId: String? = null,
    val mobileNumber: String? = null,
    val deliveryType: RedeemDeliveryType = RedeemDeliveryType.UPI,
    val redeemCode: String? = null
) {
    fun isValid(): Boolean {
        // Accept UPI or Mobile when deliveryType is unspecified or set to UPI/MOBILE.
        if (deliveryType == RedeemDeliveryType.REDEEM_CODE) {
            return redeemCode?.trim().orEmpty().isNotBlank()
        }
        val normalizedUpi = upiId?.trim().orEmpty()
        val normalizedMobile = mobileNumber?.trim().orEmpty()
        val isUpiValid = normalizedUpi.isNotEmpty() && normalizedUpi.contains("@") && normalizedUpi.length >= 3
        val isMobileValid = normalizedMobile.length == 10 && normalizedMobile.all { it.isDigit() }
        return isUpiValid || isMobileValid
    }

    fun displayLabel(): String = when (deliveryType) {
        RedeemDeliveryType.UPI -> "UPI"
        RedeemDeliveryType.MOBILE -> "mobile"
        RedeemDeliveryType.REDEEM_CODE -> "redeem code"
    }
}

enum class RedeemDeliveryType {
    UPI,
    MOBILE,
    REDEEM_CODE;

    fun displayLabel(): String = when (this) {
        UPI -> "UPI"
        MOBILE -> "mobile"
        REDEEM_CODE -> "redeem code"
    }
}

enum class PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED,
    CANCELLED
}

data class PaymentStatistics(
    val totalPayments: Int = 0,
    val pendingCount: Int = 0,
    val approvedCount: Int = 0,
    val rejectedCount: Int = 0,
    val completedCount: Int = 0,
    val cancelledCount: Int = 0,
    val failedCount: Int = 0,
    val totalCashAmount: Float = 0f,
    val totalCoinsUsed: Int = 0
)

data class PaymentValidation(
    val isValid: Boolean = true,
    val errors: List<String> = emptyList(),
    val message: String = "Validation successful.",
    val trustScore: Int = 100,
    val riskLevel: String = FraudRiskLevel.SAFE.name
)

data class PaymentHistory(
    val reward: String = "",
    val coinsUsed: Int = 0,
    val cashPaid: Float = 0f,
    val transactionId: String? = null,
    val status: String = PaymentStatus.PENDING.name,
    val date: String = "",
    val time: String = ""
)

data class PaymentApproval(
    val paymentId: String,
    val approvedBy: String = "CEO",
    val transactionId: String? = null,
    val paymentNote: String? = null,
    val approvedAt: String = "",
    val redeemCode: String? = null
)

data class PaymentCompletion(
    val paymentId: String,
    val transactionId: String,
    val paymentNote: String? = null,
    val completedAt: String = ""
)

data class PaymentSubmissionResult(
    val payment: RedeemPayment?,
    val validation: PaymentValidation,
    val success: Boolean,
    val message: String,
    val backendRequestId: String? = null,
    val backendTransactionId: String? = null
)
