package ru.reserve.sin.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.reserve.sin.data.local.HistoryTransactionRow
import ru.reserve.sin.data.local.SyncStatus

class HistoryUiStateFactoryTest {
    @Test
    fun `groups batch rows and retains a single operation separately`() {
        val groups = historyGroups(
            listOf(
                transaction(id = "one", batchId = "batch", amountRub = -5_000, occurredAt = "2026-08-07T00:00:00Z"),
                transaction(id = "two", batchId = "batch", amountRub = 5_000, occurredAt = "2026-08-07T00:00:00Z"),
                transaction(id = "three", batchId = null, amountRub = 1_000, occurredAt = "2026-08-06T00:00:00Z"),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals("batch", groups.first().id)
        assertEquals(2, groups.first().transactions.size)
        assertEquals(0, groups.first().totalAmountRub)
        assertEquals("three", groups.last().id)
    }

    @Test
    fun `marks a group as cancelled only when every row is cancelled`() {
        val group = historyGroups(
            listOf(
                transaction(id = "one", batchId = "batch", amountRub = 1_000, isCancelled = true),
                transaction(id = "two", batchId = "batch", amountRub = -100, isCancelled = false, syncStatus = SyncStatus.PENDING),
            ),
        ).single()

        assertFalse(group.isCancelled)
        assertTrue(group.hasUnsyncedChanges)
    }

    private fun transaction(
        id: String,
        batchId: String?,
        amountRub: Long,
        occurredAt: String = "2026-08-07T00:00:00Z",
        isCancelled: Boolean = false,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ) = HistoryTransactionRow(
        id = id,
        categoryId = "category",
        categoryName = "Подушка",
        labelId = null,
        labelName = null,
        batchId = batchId,
        amountRub = amountRub,
        comment = null,
        occurredAt = occurredAt,
        isCancelled = isCancelled,
        syncStatus = syncStatus,
    )
}
