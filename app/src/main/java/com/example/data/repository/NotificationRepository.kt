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

interface NotificationStorage {
    fun readNotifications(): List<NotificationItem>
    fun writeNotifications(list: List<NotificationItem>)
}

class PreferencesNotificationStorage(
    private val prefs: UserPreferences
) : NotificationStorage {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val notificationAdapter = moshi.adapter<List<NotificationItem>>(
        Types.newParameterizedType(List::class.java, NotificationItem::class.java)
    )

    override fun readNotifications(): List<NotificationItem> {
        val json = prefs.notificationsJson.orEmpty()
        if (json.isEmpty()) return emptyList()
        return try {
            notificationAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun writeNotifications(list: List<NotificationItem>) {
        prefs.notificationsJson = notificationAdapter.toJson(list)
    }
}

class NotificationRepository(
    storage: NotificationStorage,
    private val settings: NotificationSettings = NotificationSettings()
) {
    private val storage: NotificationStorage = storage
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _notificationSnapshot = MutableStateFlow(NotificationSnapshot())
    val notificationSnapshotState: StateFlow<NotificationSnapshot> = _notificationSnapshot.asStateFlow()

    init {
        refreshFromPreferences()
    }

    fun refreshFromPreferences() {
        _notifications.value = storage.readNotifications()
        emitSnapshot()
    }

    fun refreshNotifications(snapshot: AppSnapshot) {
        val current = storage.readNotifications().toMutableList()
        val timestamp = timestamp()
        val previousCount = current.size

        if (snapshot.coinBalance > 0 && previousCount == 0) {
            current.add(0, makeNotification(NotificationType.COINS_ADDED, "Coins ready", "Coin balance is now available.", snapshot, timestamp))
        }
        if (snapshot.successfulRedeems > 0) {
            current.add(0, makeNotification(NotificationType.COINS_REDEEMED, "Redeem completed", "A redeem request has been processed.", snapshot, timestamp))
        }
        if (snapshot.trustScore != 100) {
            val type = if (snapshot.trustScore < 100) NotificationType.TRUST_SCORE_DECREASED else NotificationType.TRUST_SCORE_INCREASED
            current.add(0, makeNotification(type, "Trust score updated", "Trust score changed to ${snapshot.trustScore}.", snapshot, timestamp))
        }
        if (snapshot.leaderboardState.stats.currentRank > 0 && snapshot.leaderboardState.stats.rankDifference != 0) {
            val type = if (snapshot.leaderboardState.stats.rankDifference > 0) NotificationType.LEADERBOARD_PROMOTION else NotificationType.LEADERBOARD_DEMOTION
            current.add(0, makeNotification(type, "Leaderboard update", "Your rank changed by ${snapshot.leaderboardState.stats.rankDifference}.", snapshot, timestamp))
        }
        if (snapshot.transactionStats.totalTransactions > 20) {
            current.add(0, makeNotification(NotificationType.FRAUD_WARNING, "Fraud warning", "Unusual activity was detected.", snapshot, timestamp))
        }
        if (snapshot.dailyAdsWatched > 0 && snapshot.dailyAdsWatched % 5 == 0) {
            current.add(0, makeNotification(NotificationType.DAILY_REWARD, "Daily reward", "You reached another daily milestone.", snapshot, timestamp))
        }

        val trimmed = current.take(settings.maxStoredNotifications).distinctBy { it.id }
        storage.writeNotifications(trimmed)
        _notifications.value = trimmed
        emitSnapshot()
    }

    fun refreshFromSnapshot(snapshot: AppSnapshot) {
        refreshNotifications(snapshot)
    }

    fun notificationSnapshot(): NotificationSnapshot = _notificationSnapshot.value
    fun notificationFlow(): StateFlow<NotificationSnapshot> = notificationSnapshotState

    fun createNotification(item: NotificationItem) {
        val current = storage.readNotifications().toMutableList()
        current.add(0, item)
        val trimmed = current.take(settings.maxStoredNotifications).distinctBy { it.id }
        storage.writeNotifications(trimmed)
        _notifications.value = trimmed
        emitSnapshot()
    }

    fun markAsRead(id: String) {
        val updated = storage.readNotifications().map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        storage.writeNotifications(updated)
        _notifications.value = updated
        emitSnapshot()
    }

    fun markAllAsRead() {
        val updated = storage.readNotifications().map { it.copy(isRead = true) }
        storage.writeNotifications(updated)
        _notifications.value = updated
        emitSnapshot()
    }

    fun clearAllNotifications() {
        storage.writeNotifications(emptyList())
        refreshFromPreferences()
    }

    fun clearExpired() {
        val now = System.currentTimeMillis()
        val filtered = storage.readNotifications().filter { item ->
            val expiresAt = item.expiresAt ?: return@filter true
            val expires = expiresAt.toLongOrNull()
            if (expires == null) {
                val parsed = parseTimestamp(expiresAt)
                parsed == null || parsed > now
            } else {
                expires > now
            }
        }
        storage.writeNotifications(filtered)
        _notifications.value = filtered
        emitSnapshot()
    }

    fun deleteNotification(id: String) {
        val filtered = storage.readNotifications().filterNot { it.id == id }
        storage.writeNotifications(filtered)
        _notifications.value = filtered
        emitSnapshot()
    }

    fun unreadCount(): Int = notificationSnapshot().unreadCounter.count
    fun latestNotification(): NotificationItem? = notificationSnapshot().summary.latestNotification
    fun notificationStatistics(): NotificationStats = notificationSnapshot().stats

    private fun emitSnapshot() {
        val items = storage.readNotifications()
        val unread = items.count { !it.isRead }
        val today = items.count { item ->
            val stamp = item.timestamp.substring(0, 10)
            stamp == dateString()
        }
        val weekly = items.count { item ->
            item.timestamp.length >= 10
        }
        val stats = NotificationStats(
            totalNotifications = items.size,
            unreadNotifications = unread,
            readNotifications = items.size - unread,
            criticalNotifications = items.count { it.priority == NotificationPriority.CRITICAL },
            warnings = items.count { it.priority == NotificationPriority.HIGH || it.category == NotificationCategory.FRAUD || it.category == NotificationCategory.BUDGET },
            informationMessages = items.count { it.priority == NotificationPriority.LOW || it.priority == NotificationPriority.MEDIUM },
            todaysNotifications = today,
            weeklyNotifications = weekly
        )
        val summary = NotificationSummary(
            unreadCount = unread,
            latestNotification = items.firstOrNull(),
            recentActivitySummary = if (items.isEmpty()) "No recent activity" else "${items.size} notifications received"
        )
        _notificationSnapshot.value = NotificationSnapshot(
            items = items,
            stats = stats,
            unreadCounter = UnreadCounter(count = unread, hasUnread = unread > 0),
            summary = summary,
            settings = settings
        )
    }

    private fun makeNotification(
        type: NotificationType,
        title: String,
        message: String,
        snapshot: AppSnapshot,
        timestamp: String
    ): NotificationItem {
        val priority = when (type) {
            NotificationType.FRAUD_WARNING, NotificationType.BUDGET_WARNING, NotificationType.REDEEM_REJECTED -> NotificationPriority.CRITICAL
            NotificationType.LEADERBOARD_PROMOTION, NotificationType.TRUST_SCORE_INCREASED, NotificationType.REDEEM_APPROVED -> NotificationPriority.HIGH
            else -> NotificationPriority.MEDIUM
        }
        val category = when (type) {
            NotificationType.REWARD_EARNED, NotificationType.COINS_ADDED, NotificationType.DAILY_REWARD -> NotificationCategory.REWARD
            NotificationType.COINS_REDEEMED, NotificationType.REDEEM_APPROVED, NotificationType.REDEEM_REJECTED -> NotificationCategory.REDEEM
            NotificationType.LEADERBOARD_PROMOTION, NotificationType.LEADERBOARD_DEMOTION -> NotificationCategory.LEADERBOARD
            NotificationType.TRUST_SCORE_INCREASED, NotificationType.TRUST_SCORE_DECREASED -> NotificationCategory.TRUST
            NotificationType.BUDGET_WARNING -> NotificationCategory.BUDGET
            NotificationType.FRAUD_WARNING -> NotificationCategory.FRAUD
            else -> NotificationCategory.GENERAL
        }
        return NotificationItem(
            id = "notif-${System.currentTimeMillis()}-${snapshot.coinBalance}-${snapshot.trustScore}",
            relatedId = null,
            title = title,
            message = message,
            timestamp = timestamp,
            priority = priority,
            category = category,
            actionType = type.name,
            expiresAt = (System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000).toString(),
            sourceRepository = "USER_REPOSITORY",
            severity = if (priority == NotificationPriority.CRITICAL) "CRITICAL" else "INFO",
            notificationType = type,
            type = type.name
        )
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    private fun dateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun parseTimestamp(value: String): Long? {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(value)?.time
        } catch (_: Exception) {
            null
        }
    }
}
