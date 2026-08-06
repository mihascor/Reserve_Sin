package ru.reserve.sin.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.reserve.sin.data.local.HomeCategoryRow

class HomeUiStateFactoryTest {
    @Test
    fun `creates home state from local summary`() {
        val state = HomeUiStateFactory.create(
            categories = listOf(
                HomeCategoryRow(
                    id = "pillow",
                    name = "Подушка",
                    targetAmountRub = 100_000,
                    balanceRub = 92_600,
                    hasPendingChanges = true,
                ),
            ),
            totalBalanceRub = 92_600,
            pendingTransactionCount = 1,
            lastSuccessfulSyncAt = null,
        )

        assertFalse(state.isLoading)
        assertEquals(92_600, state.totalBalanceRub)
        assertEquals("Подушка", state.categories.single().name)
        assertEquals(92, state.categories.single().progressPercent)
        assertTrue(state.categories.single().hasPendingChanges)
        assertEquals(1, state.pendingTransactionCount)
    }
}
