package com.example.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals

class RedeemPaymentRepositoryTest {

    @Test
    fun `valid upi destination passes validation`() {
        val destination = RedeemDestination(upiId = "user@upi")
        assertTrue(destination.isValid())
    }

    @Test
    fun `mobile destination passes validation`() {
        val destination = RedeemDestination(mobileNumber = "9876543210")
        assertTrue(destination.isValid())
    }

    @Test
    fun `invalid destination is rejected`() {
        val destination = RedeemDestination(upiId = "invalid")
        assertFalse(destination.isValid())
    }

    @Test
    fun `redeem code destination accepts a code`() {
        val destination = RedeemDestination(
            deliveryType = RedeemDeliveryType.REDEEM_CODE,
            redeemCode = "KL-ABC-123"
        )
        assertTrue(destination.isValid())
    }

    @Test
    fun `redeem code destination rejects blank code`() {
        val destination = RedeemDestination(deliveryType = RedeemDeliveryType.REDEEM_CODE)
        assertFalse(destination.isValid())
    }

    @Test
    fun `pending payment lifecycle is tracked`() {
        val payment = RedeemPayment(
            id = "pay-1",
            orderId = "ORD-1",
            userUid = "user-1",
            username = "Tester",
            rewardPack = "Bronze Pack",
            rewardPackId = 1,
            coinsRequired = 300,
            cashAmount = 10f,
            upiId = "user@upi",
            trustScore = 90,
            fraudScore = 10,
            fraudRisk = "SAFE",
            createdAt = "2025-01-01 10:00",
            updatedAt = "2025-01-01 10:00",
            status = PaymentStatus.PENDING
        )
        assertTrue(payment.status == PaymentStatus.PENDING)
    }

    @Test
    fun `submitting a redeem request creates one notification with request details`() {
        val notificationStorage = TestNotificationStorage()
        val notificationRepo = NotificationRepository(notificationStorage)
        val paymentRepo = RedeemPaymentRepository(
            storage = InMemoryRedeemPaymentStorage(),
            notificationRepo = notificationRepo
        )

        val payment = RedeemPayment(
            id = "pay-2",
            orderId = "ORD-2",
            userUid = "user-2",
            username = "Tester",
            rewardPack = "Silver Pack",
            rewardPackId = 2,
            coinsRequired = 300,
            cashAmount = 20f,
            upiId = "user@upi",
            trustScore = 90,
            fraudScore = 10,
            fraudRisk = "SAFE",
            createdAt = "2025-01-01 10:00",
            updatedAt = "2025-01-01 10:00",
            status = PaymentStatus.PENDING
        )

        paymentRepo.submitRedeem(payment)

        val notifications = notificationStorage.readNotifications()
        assertEquals(1, notifications.size)
        assertTrue(notifications.first().message.contains("Request ID"))
        assertTrue(notifications.first().message.contains("Reward Pack"))
        assertTrue(notifications.first().message.contains("Status"))
        assertTrue(notifications.first().message.contains("Timestamp"))
    }
}

private class TestNotificationStorage : NotificationStorage {
    private var notifications: List<NotificationItem> = emptyList()

    override fun readNotifications(): List<NotificationItem> = notifications

    override fun writeNotifications(list: List<NotificationItem>) {
        notifications = list
    }
}
