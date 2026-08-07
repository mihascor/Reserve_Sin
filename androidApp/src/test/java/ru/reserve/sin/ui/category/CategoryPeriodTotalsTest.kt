package ru.reserve.sin.ui.category

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.reserve.sin.data.local.HistoryTransactionRow
import ru.reserve.sin.data.local.SyncStatus

class CategoryPeriodTotalsTest {
    @Test
    fun `separates income and expenses`() {
        val totals = categoryPeriodTotals(listOf(transaction(1_500), transaction(-400), transaction(-100)))

        assertEquals(1_500, totals.incomeRub)
        assertEquals(-500, totals.expenseRub)
    }

    private fun transaction(amountRub: Long) = HistoryTransactionRow(
        id = amountRub.toString(),
        categoryId = "category",
        categoryName = "Подушка",
        labelId = null,
        labelName = null,
        batchId = null,
        amountRub = amountRub,
        comment = null,
        occurredAt = "2026-08-07T00:00:00Z",
        isCancelled = false,
        syncStatus = SyncStatus.SYNCED,
    )
}
