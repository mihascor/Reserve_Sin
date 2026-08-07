package ru.reserve.sin.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.reserve.sin.data.local.CategoryEntity
import ru.reserve.sin.data.local.SyncStatus

class CategorySyncActionTest {
    @Test
    fun `uses create for a category without server id`() {
        assertEquals(CategorySyncAction.CREATE, categorySyncAction(category(remoteId = null)))
    }

    @Test
    fun `uses update for an already synchronized category`() {
        assertEquals(CategorySyncAction.UPDATE, categorySyncAction(category(remoteId = "server-category")))
    }

    private fun category(remoteId: String?) = CategoryEntity(
        id = "local-category",
        name = "Категория",
        currency = "RUB",
        targetAmountRub = null,
        sortOrder = 0,
        isArchived = false,
        isVisibleOnHome = true,
        createdAt = "2026-08-07T00:00:00Z",
        updatedAt = "2026-08-07T00:00:00Z",
        revision = 1,
        remoteId = remoteId,
        syncStatus = SyncStatus.SYNCED,
    )
}
