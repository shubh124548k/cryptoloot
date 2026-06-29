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

    init {
        refreshFromPreferences()
    }

    fun refreshFromPreferences() {
        _transactions.value = readTransactions()
    }

    fun recordTransaction(record: TransactionRecord): TransactionRecord {
        val current = readTransactions().toMutableList()
        if (current.any { it.id == record.id }) {
            return record
        }
        current.add(0, record)
        prefs.transactionHistoryJson = transactionAdapter.toJson(current)
        userRepo.refreshFromPreferences()
        _transactions.value = current
        return record
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

    fun createTransaction(
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
                amount = (currentBalance - previousBalance).coerceAtLeast(0),
                status = status,
                timestamp = timestamp,
                message = description,
                updatedAt = timestamp
            )
        )
    }
}
