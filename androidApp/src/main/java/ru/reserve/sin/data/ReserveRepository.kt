package ru.reserve.sin.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import ru.reserve.sin.data.local.HomeCategoryRow
import ru.reserve.sin.data.local.HomeDao
import ru.reserve.sin.data.local.ReserveDatabase
import ru.reserve.sin.data.local.CategoryEntity
import ru.reserve.sin.data.local.SyncStatus
import ru.reserve.sin.data.local.TransactionEntity

class ReserveRepository(database: ReserveDatabase) {
    private val homeDao: HomeDao = database.homeDao()

    fun observeHomeCategories(): Flow<List<HomeCategoryRow>> = homeDao.observeHomeCategories()

    fun observeTotalBalance(): Flow<Long> = homeDao.observeTotalBalance()

    fun observePendingTransactionCount(): Flow<Int> = homeDao.observePendingTransactionCount()

    fun observeLastSuccessfulSyncAt(): Flow<String?> = homeDao.observeLastSuccessfulSyncAt()

    fun observeCategories(): Flow<List<CategoryEntity>> = homeDao.observeCategories()

    fun observeActiveCategories(): Flow<List<CategoryEntity>> = homeDao.observeActiveCategories()

    suspend fun createCategory(name: String, targetAmountRub: Long?) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Введите название категории" }
        require(targetAmountRub == null || targetAmountRub >= 0) { "Цель не может быть отрицательной" }
        val timestamp = nowUtc()
        homeDao.insertCategory(
            CategoryEntity(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                currency = "RUB",
                targetAmountRub = targetAmountRub,
                sortOrder = (homeDao.lastCategorySortOrder() ?: -1) + 1,
                isArchived = false,
                isVisibleOnHome = true,
                createdAt = timestamp,
                updatedAt = timestamp,
                revision = 0,
            ),
        )
    }

    suspend fun updateCategory(category: CategoryEntity, name: String, targetAmountRub: Long?) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Введите название категории" }
        require(targetAmountRub == null || targetAmountRub >= 0) { "Цель не может быть отрицательной" }
        homeDao.updateCategory(
            category.copy(name = normalizedName, targetAmountRub = targetAmountRub, updatedAt = nowUtc()),
        )
    }

    suspend fun setCategoryArchived(category: CategoryEntity, isArchived: Boolean) {
        homeDao.updateCategory(category.copy(isArchived = isArchived, updatedAt = nowUtc()))
    }

    suspend fun createTransactions(date: String, comment: String, rows: List<OperationLine>) {
        require(isDate(date)) { "Введите дату в формате ГГГГ-ММ-ДД" }
        require(rows.isNotEmpty()) { "Добавьте хотя бы одну строку" }
        require(rows.all { it.categoryId.isNotBlank() && it.amountRub != 0L }) {
            "Укажите категорию и ненулевую сумму в каждой строке"
        }
        val timestamp = nowUtc()
        val batchId = if (rows.size > 1) UUID.randomUUID().toString() else null
        homeDao.insertTransactions(
            rows.map { row ->
                TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    categoryId = row.categoryId,
                    labelId = null,
                    batchId = batchId,
                    amountRub = row.amountRub,
                    comment = comment.trim().ifEmpty { null },
                    occurredAt = "${date}T00:00:00Z",
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    clientOperationId = UUID.randomUUID().toString(),
                    isCancelled = false,
                    syncStatus = SyncStatus.PENDING,
                    revision = null,
                )
            },
        )
    }

    private fun nowUtc(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    private fun isDate(value: String): Boolean = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
    }.getOrNull() != null
}

data class OperationLine(val categoryId: String, val amountRub: Long)
