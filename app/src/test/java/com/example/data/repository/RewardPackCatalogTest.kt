package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardPackCatalogTest {

    @Test
    fun `builds deterministic progress from coin balance`() {
        val packs = RewardPackCatalog.fromBalance(980)

        val bronze = packs.first { it.id == 1 }
        val gold = packs.first { it.id == 3 }
        val diamond = packs.first { it.id == 4 }
        val elite = packs.first { it.id == 5 }

        assertEquals("Bronze Pack", bronze.name)
        assertTrue(bronze.isUnlocked)
        assertEquals(300, bronze.currentProgress)
        assertEquals(100, bronze.progressPercentage)

        assertEquals(980, gold.currentProgress)
        assertEquals(65, gold.progressPercentage)
        assertEquals(false, gold.isUnlocked)

        assertEquals(980, diamond.currentProgress)
        assertEquals(28, diamond.progressPercentage)

        assertEquals(980, elite.currentProgress)
        assertEquals(14, elite.progressPercentage)
    }
}
