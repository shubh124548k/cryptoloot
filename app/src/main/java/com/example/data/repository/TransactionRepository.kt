package com.example.data.repository

import com.example.data.local.UserPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionRepository(
    private val prefs: UserPreferences,
    private val userRepo: UserRepository
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val transactionAdapter = moshi.adapter<List<TransactionRecord>>(
        Types.newParameterizedType(List::class.java, TransactionRecord::class.java)
    )

    private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactions: StateFlow<List<TransactionRecord>> = _transactions.asStateFlow()

    private val _latestTransaction = MutableStateFlow<TransactionRecord?>(null)
    val latestTransaction: StateFlow<TransactionRecord?> = _latestTransaction.asStateFlow()

    private val _statistics = MutableStateFlow(TransactionStatistics())
    val statistics: StateFlow<TransactionStatistics> = _statistics.asStateFlow()

    init {
        refreshFromPreferences()
    }

    fun refreshFromPreferences() {
        val current = readTransactions()
        _transactions.value = current
        _latestTransaction.value = current.firstOrNull()
        _statistics.value = buildStatistics(current)
    }

    fun getTransactionsSnapshot(): List<TransactionRecord> = _transactions.value

    fun getLatestTransactionSnapshot(): TransactionRecord? = _latestTransaction.value

    fun getStatisticsSnapshot(): TransactionStatistics = _statistics.value

    fun insertTransaction(record: TransactionRecord): TransactionRecord {
        val current = readTransactions().toMutableList()
        val indexById = current.indexOfFirst { it.id == record.id }
        val indexByQueueId = if (indexById >= 0) indexById else record.queueId?.let { queueId ->
            current.indexOfFirst { it.queueId == queueId }
        } ?: -1

        if (indexByQueueId >= 0) {
            val existing = current[indexByQueueId]
            val updated = existing.copy(
                userUid = if (record.userUid.isNotEmpty()) record.userUid else existing.userUid,
                username = if (record.username.isNotEmpty()) record.username else existing.username,
                transactionType = record.transactionType,
                type = if (record.type.isNotEmpty()) record.type else existing.type,
                coinsBefore = record.coinsBefore,
                amount = record.amount,
                coinsChanged = record.coinsChanged,
                coinsAfter = record.coinsAfter,
                rewardPack = record.rewardPack ?: existing.rewardPack,
                cashAmount = record.cashAmount ?: existing.cashAmount,
                queueId = record.queueId ?: existing.queueId,
                status = if (record.status.isNotEmpty()) record.status else existing.status,
                description = if (record.description.isNotEmpty()) record.description else existing.description,
                timestamp = if (record.timestamp.isNotEmpty()) record.timestamp else existing.timestamp,
                completedTimestamp = record.completedTimestamp ?: existing.completedTimestamp,
                deviceId = record.deviceId ?: existing.deviceId,
                serverSyncFlag = record.serverSyncFlag,
                versionNumber = record.versionNumber,
                message = record.message ?: existing.message,
                rewardName = record.rewardName ?: existing.rewardName,
                rewardAmount = record.rewardAmount ?: existing.rewardAmount,
                packId = record.packId ?: existing.packId,
                updatedAt = record.updatedAt ?: existing.updatedAt,
                action = if (record.action.isNotEmpty()) record.action else existing.action,
                previousBalance = record.previousBalance ?: existing.previousBalance,
                currentBalance = record.currentBalance ?: existing.currentBalance,
                legacyDescription = record.legacyDescription ?: existing.legacyDescription
            )
            current[indexByQueueId] = updated
            persistTransactions(current)
            return updated
        }
        current.add(0, record)
        persistTransactions(current)
        return record
    }

    fun recordTransaction(record: TransactionRecord): TransactionRecord = insertTransaction(record)

    fun recordTransactionForBalanceChange(
        userUid: String,
        username: String,
        transactionType: TransactionType,
        previousBalance: Int,
        currentBalance: Int,
        status: String,
        description: String,
        rewardPack: String? = null,
        cashAmount: Float? = null,
        queueId: String? = null,
        completedTimestamp: String? = null,
        deviceId: String? = null
    ): TransactionRecord {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val record = TransactionRecord(
            id = "txn-${System.currentTimeMillis()}",
            userUid = userUid,
            username = username,
            transactionType = transactionType,
            type = transactionType.name,
            coinsBefore = previousBalance,
            amount = (currentBalance - previousBalance).coerceAtLeast(0),
            coinsChanged = currentBalance - previousBalance,
            coinsAfter = currentBalance,
            rewardPack = rewardPack,
            cashAmount = cashAmount,
            queueId = queueId,
            status = status,
            description = description,
            timestamp = timestamp,
            completedTimestamp = completedTimestamp,
            deviceId = deviceId,
            serverSyncFlag = false,
            versionNumber = 1,
            message = description,
            rewardName = rewardPack,
            rewardAmount = cashAmount?.toInt(),
            updatedAt = completedTimestamp ?: timestamp,
            previousBalance = previousBalance,
            currentBalance = currentBalance,
            legacyDescription = description
        )
        return recordTransaction(record)
    }

    fun getTransactionsByUser(userUid: String): List<TransactionRecord> =
        _transactions.value.filter { it.userUid == userUid }

    fun getTransactionsByType(type: TransactionType): List<TransactionRecord> =
        _transactions.value.filter { it.transactionType == type }

    fun getRedeemTransactions(): List<TransactionRecord> =
        _transactions.value.filter { it.transactionType.isRedeemEvent() }

    fun getEarningTransactions(): List<TransactionRecord> =
        _transactions.value.filter {
            it.transactionType == TransactionType.WATCH_REWARD || it.transactionType == TransactionType.COIN_BONUS
        }

    fun getTransactions(filter: TransactionFilter): List<TransactionRecord> = when (filter) {
        TransactionFilter.ALL -> _transactions.value
        TransactionFilter.REWARDS -> getEarningTransactions()
        TransactionFilter.REDEEMS -> getRedeemTransactions()
        TransactionFilter.APPROVALS -> _transactions.value.filter { it.transactionType == TransactionType.REDEEM_APPROVED }
        TransactionFilter.REJECTED -> _transactions.value.filter { it.transactionType == TransactionType.REDEEM_REJECTED }
        TransactionFilter.SYSTEM -> _transactions.value.filter { it.transactionType == TransactionType.SYSTEM }
    }

    fun clearLocalCache() {
        prefs.transactionHistoryJson = null
        refreshFromPreferences()
    }

    fun recordSystemTransaction(
        action: String,
        previousBalance: Int,
        currentBalance: Int,
        description: String,
        status: String = "PENDING",
        id: String = "txn-${System.currentTimeMillis()}"
    ): TransactionRecord {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return recordTransaction(
            TransactionRecord(
                id = id,
                type = action,
                transactionType = TransactionType.SYSTEM,
                coinsBefore = previousBalance,
                amount = (currentBalance - previousBalance).coerceAtLeast(0),
                coinsChanged = currentBalance - previousBalance,
                coinsAfter = currentBalance,
                status = status,
                description = description,
                timestamp = timestamp,
                updatedAt = timestamp,
                message = description,
                previousBalance = previousBalance,
                currentBalance = currentBalance,
                legacyDescription = description
            )
        )
    }

    private fun persistTransactions(list: List<TransactionRecord>) {
        prefs.transactionHistoryJson = transactionAdapter.toJson(list)
        _transactions.value = list
        _latestTransaction.value = list.firstOrNull()
        _statistics.value = buildStatistics(list)
        userRepo.refreshFromPreferences()
    }

    private fun readTransactions(): List<TransactionRecord> {
        val json = prefs.transactionHistoryJson.orEmpty()
        if (json.isEmpty()) return emptyList()
        return try {
            transactionAdapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildStatistics(records: List<TransactionRecord>): TransactionStatistics {
        val ordered = records.sortedByDescending { it.timestamp }
        val rewardTransactions = records.filter {
            it.transactionType == TransactionType.WATCH_REWARD || it.transactionType == TransactionType.COIN_BONUS
        }
        val redeemEvents = records
            .filter { it.transactionType.isRedeemEvent() }
            .distinctBy { it.queueId ?: it.id }
        val coinsEarned = rewardTransactions.sumOf { it.coinsChanged.coerceAtLeast(0) }
        val coinsRedeemed = redeemEvents.sumOf { (it.coinsBefore - it.coinsAfter).coerceAtLeast(0) }
        val latestRedeem = redeemEvents.firstOrNull()
        val averageReward = if (rewardTransactions.isNotEmpty()) {
            coinsEarned.toFloat() / rewardTransactions.size.toFloat()
        } else {
            0f
        }

        return TransactionStatistics(
            totalTransactions = records.size,
            coinsEarnedLifetime = coinsEarned,
            coinsRedeemedLifetime = coinsRedeemed,
            lastTransactionTime = ordered.firstOrNull()?.timestamp,
            lastRedeemTime = latestRedeem?.completedTimestamp ?: latestRedeem?.timestamp,
            averageCoinsPerReward = averageReward,
            rewardTransactions = rewardTransactions.size,
            redeemTransactions = redeemEvents.size,
            approvalTransactions = records.count { it.transactionType == TransactionType.REDEEM_APPROVED },
            rejectedTransactions = records.count { it.transactionType == TransactionType.REDEEM_REJECTED },
            systemTransactions = records.count { it.transactionType == TransactionType.SYSTEM }
        )
    }

    private fun TransactionType.isRedeemEvent(): Boolean = when (this) {
        TransactionType.REDEEM_REQUEST,
        TransactionType.REDEEM_PROCESSING,
        TransactionType.REDEEM_APPROVED,
        TransactionType.REDEEM_REJECTED,
        TransactionType.QUEUE_COMPLETED -> true
        else -> false
    }
}
