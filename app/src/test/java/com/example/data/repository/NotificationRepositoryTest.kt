package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {

    @Test
    fun generatesNotificationsFromRepositoryEvents() {
        val repo = NotificationRepository(InMemoryNotificationStorage())
        repo.refreshNotifications(AppSnapshot(coinBalance = 100))
        repo.refreshNotifications(AppSnapshot(coinBalance = 150, trustScore = 95))

        val snapshot = repo.notificationSnapshot()
        assertTrue(snapshot.stats.totalNotifications >= 2)
        assertTrue(snapshot.unreadCounter.count > 0)
    }

    @Test
    fun marksNotificationsAsReadAndUpdatesCounters() {
        val repo = NotificationRepository(InMemoryNotificationStorage())
        repo.refreshNotifications(AppSnapshot(coinBalance = 100))
        val first = repo.notifications.value.first()

        repo.markAsRead(first.id)
        val snapshot = repo.notificationSnapshot()

        assertEquals(0, snapshot.unreadCounter.count)
        assertEquals(1, snapshot.stats.readNotifications)
    }

    @Test
    fun clearsExpiredNotificationsAndTracksStatistics() {
        val repo = NotificationRepository(InMemoryNotificationStorage())
        val expired = NotificationItem(
            id = "expired",
            title = "Expired",
            message = "Old event",
            timestamp = "2024-01-01 00:00",
            expiresAt = "2024-01-01 00:01",
            notificationType = NotificationType.GENERAL_INFORMATION
        )
        repo.createNotification(expired)
        repo.clearExpired()

        assertTrue(repo.notificationSnapshot().stats.totalNotifications == 0)
    }

    @Test
    fun exposesLatestNotificationAndSummary() {
        val repo = NotificationRepository(InMemoryNotificationStorage())
        repo.refreshNotifications(AppSnapshot(coinBalance = 100))

        val latest = repo.latestNotification()
        assertNotNull(latest)
        assertTrue(repo.notificationSnapshot().summary.recentActivitySummary.isNotEmpty())
    }

    @Test
    fun buildsStatisticsFromStoredNotifications() {
        val repo = NotificationRepository(InMemoryNotificationStorage())
        repo.createNotification(
            NotificationItem(
                id = "critical",
                title = "Critical",
                message = "Need attention",
                timestamp = "2024-01-01 00:00",
                priority = NotificationPriority.CRITICAL,
                category = NotificationCategory.FRAUD,
                notificationType = NotificationType.FRAUD_WARNING
            )
        )
        repo.createNotification(
            NotificationItem(
                id = "info",
                title = "Info",
                message = "General update",
                timestamp = "2024-01-01 00:00",
                priority = NotificationPriority.LOW,
                category = NotificationCategory.GENERAL,
                notificationType = NotificationType.GENERAL_INFORMATION
            )
        )

        val stats = repo.notificationStatistics()
        assertTrue(stats.totalNotifications >= 2)
        assertTrue(stats.criticalNotifications >= 1)
        assertTrue(stats.informationMessages >= 1)
    }
}

private class InMemoryNotificationStorage : NotificationStorage {
    private var notifications: List<NotificationItem> = emptyList()

    override fun readNotifications(): List<NotificationItem> = notifications

    override fun writeNotifications(list: List<NotificationItem>) {
        notifications = list
    }
}
