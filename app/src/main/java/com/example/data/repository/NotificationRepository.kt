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

class NotificationRepository(
    private val prefs: UserPreferences
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val notificationAdapter = moshi.adapter<List<NotificationItem>>(
        Types.newParameterizedType(List::class.java, NotificationItem::class.java)
    )

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    init {
        refreshFromPreferences()
    }

    fun refreshFromPreferences() {
        _notifications.value = readNotifications()
    }

    fun refreshFromSnapshot(snapshot: AppSnapshot) {
        // Keep notifications stable and repository-driven; no UI changes required.
        val current = readNotifications().toMutableList()
        if (current.isEmpty()) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            current.add(
                NotificationItem(
                    id = "notif-system",
                    type = "System message",
                    title = "Repository ready",
                    message = "Sync layer prepared for MongoDB-backed updates.",
                    timestamp = timestamp,
                    isRead = false
                )
            )
            persistNotifications(current)
        }
        _notifications.value = current
    }

    fun addNotification(item: NotificationItem) {
        val current = readNotifications().toMutableList()
        current.add(0, item)
        persistNotifications(current)
        _notifications.value = current
    }

    private fun readNotifications(): List<NotificationItem> {
        val json = prefs.notificationsJson.orEmpty()
        if (json.isEmpty()) return emptyList()
        return try {
            notificationAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistNotifications(list: List<NotificationItem>) {
        prefs.notificationsJson = notificationAdapter.toJson(list)
    }
}
