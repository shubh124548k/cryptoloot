package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyBudgetRepositoryTest {

    @Test
    fun budgetCalculationUsesSnapshotDerivedValues() {
        val snapshot = AppSnapshot(
            coinBalance = 250,
            totalCoinsEarned = 1000,
            successfulRedeems = 5,
            transactionStats = TransactionStatistics(
                totalTransactions = 6,
                coinsEarnedLifetime = 1000,
                coinsRedeemedLifetime = 120,
                lastTransactionTime = "2026-06-20 10:00"
            )
        )

        val repo = MonthlyBudgetRepository()
        val budget = repo.calculateBudget(snapshot)

        assertEquals(1000, budget.monthlyBudget.coinsIssued)
        assertEquals(120, budget.monthlyBudget.coinsRedeemed)
        assertEquals(4120, budget.remainingBudget)
        assertTrue(budget.status == BudgetStatus.NORMAL)
    }

    @Test
    fun projectionCalculationUsesDailySpendAndDaysRemaining() {
        val snapshot = AppSnapshot(
            coinBalance = 400,
            totalCoinsEarned = 5000,
            successfulRedeems = 10,
            transactionStats = TransactionStatistics(
                totalTransactions = 12,
                coinsEarnedLifetime = 5000,
                coinsRedeemedLifetime = 1000,
                lastTransactionTime = "2026-06-20 10:00"
            )
        )

        val repo = MonthlyBudgetRepository()
        val budget = repo.calculateBudget(snapshot)

        assertEquals(5000, budget.monthlyBudget.coinsIssued)
        assertEquals(1000, budget.monthlyBudget.coinsRedeemed)
        assertEquals(1000f, budget.forecast.projectedSpend, 0.1f)
        assertEquals(4000f, budget.projection.projectedMonthEndSpend, 0.1f)
        assertEquals(2000, budget.forecast.safeCoinIssuance)
    }

    @Test
    fun warningAndCriticalStatesTriggerAtThresholds() {
        val warningSnapshot = AppSnapshot(
            totalCoinsEarned = 10000,
            transactionStats = TransactionStatistics(
                totalTransactions = 8,
                coinsEarnedLifetime = 10000,
                coinsRedeemedLifetime = 2000
            )
        )
        val criticalSnapshot = AppSnapshot(
            totalCoinsEarned = 10000,
            transactionStats = TransactionStatistics(
                totalTransactions = 10,
                coinsEarnedLifetime = 10000,
                coinsRedeemedLifetime = 500
            )
        )

        val warningRepo = MonthlyBudgetRepository()
        val criticalRepo = MonthlyBudgetRepository()
        val warningBudget = warningRepo.calculateBudget(warningSnapshot)
        val criticalBudget = criticalRepo.calculateBudget(criticalSnapshot)

        assertTrue(warningBudget.status == BudgetStatus.WARNING)
        assertTrue(criticalBudget.status == BudgetStatus.CRITICAL)
        assertTrue(warningBudget.alert.isWarning)
        assertTrue(criticalBudget.alert.isCritical)
    }
}
