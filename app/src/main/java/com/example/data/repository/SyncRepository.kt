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

class SyncRepository(
    private val prefs: UserPreferences,
    private val userRepo: UserRepository,
    private val rewardRepo: RewardRepository,
    private val transactionRepo: TransactionRepository,
    private val leaderboardRepo: LeaderboardRepository,
    private val notificationRepo: NotificationRepository
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val queueAdapter = moshi.adapter<List<SyncQueueItem>>(
        Types.newParameterizedType(List::class.java, SyncQueueItem::class.java)
    )
    private val stateAdapter = moshi.adapter(SyncState::class.java)

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        refreshSyncState()
    }

    fun refreshSyncState() {
        val pendingQueue = readQueue()
        val state = SyncState(
            isSyncing = false,
            isOnline = true,
            pendingCount = pendingQueue.size,
            lastSyncAt = prefs.syncStateJson?.let { "cached" },
            lastError = null
        )
        _syncState.value = state
        prefs.syncStateJson = stateAdapter.toJson(state)
    }

    fun queueSync(entityType: String, action: String, payloadJson: String) {
        val current = readQueue().toMutableList()
        current.add(
            0,
            SyncQueueItem(
                id = "sync-${System.currentTimeMillis()}",
                entityType = entityType,
                action = action,
                payloadJson = payloadJson,
                createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            )
        )
        prefs.syncStateJson = queueAdapter.toJson(current)
        refreshSyncState()
    }

    fun applyRepositoryChange(snapshot: AppSnapshot) {
        rewardRepo.refreshRewards(snapshot.coinBalance)
        leaderboardRepo.refreshLeaderboard(snapshot)
        notificationRepo.refreshFromSnapshot(snapshot)
        queueSync("user", "update", "snapshot")
    }

    private fun readQueue(): List<SyncQueueItem> {
        val json = prefs.syncStateJson.orEmpty()
        if (json.isEmpty()) return emptyList()
        return try {
            queueAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
