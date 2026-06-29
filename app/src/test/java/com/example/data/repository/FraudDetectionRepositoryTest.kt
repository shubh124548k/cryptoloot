package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FraudDetectionRepositoryTest {

    @Test
    fun trustScoreCalculationClampsToRange() {
        val repo = FraudDetectionRepository()
        val report = repo.buildFraudReport(AppSnapshot(trustScore = 0))
        assertEquals(0, report.trustScore)
        assertTrue(report.riskLevel == FraudRiskLevel.CRITICAL)
    }

    @Test
    fun penaltyAndRecoveryAreDerivedFromSignals() {
        val repo = FraudDetectionRepository()
        val report = repo.buildFraudReport(
            AppSnapshot(
                trustScore = 100,
                transactionStats = TransactionStatistics(totalTransactions = 25, coinsEarnedLifetime = 1000, coinsRedeemedLifetime = 200),
                totalAdsWatched = 100
            )
        )
        assertTrue(report.penalty.amount > 0)
        assertTrue(report.recovery.amount >= 0)
    }

    @Test
    fun velocityCalculationsUseRepositoryData() {
        val repo = FraudDetectionRepository()
        val report = repo.buildFraudReport(
            AppSnapshot(
                trustScore = 100,
                transactionStats = TransactionStatistics(totalTransactions = 10, coinsEarnedLifetime = 500, coinsRedeemedLifetime = 200)
            )
        )
        assertEquals(45, report.statistics.rewardVelocity)
        assertEquals(18, report.statistics.redeemVelocity)
    }

    @Test
    fun riskLevelTransitionsFollowScoreAndSignals() {
        val repo = FraudDetectionRepository()
        val safe = repo.buildFraudReport(AppSnapshot(trustScore = 100))
        val medium = repo.buildFraudReport(AppSnapshot(trustScore = 70, transactionStats = TransactionStatistics(totalTransactions = 25)))
        assertTrue(safe.riskLevel == FraudRiskLevel.SAFE)
        assertTrue(medium.riskLevel == FraudRiskLevel.MEDIUM)
    }

    @Test
    fun zeroAndHundredBoundariesStayStable() {
        val repo = FraudDetectionRepository()
        val zero = repo.buildFraudReport(AppSnapshot(trustScore = 0))
        val hundred = repo.buildFraudReport(AppSnapshot(trustScore = 100))
        assertEquals(0, zero.trustScore)
        assertEquals(100, hundred.trustScore)
    }
}

