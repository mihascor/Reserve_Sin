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
import ru.reserve.sin.data.remote.ServerSyncClient
import ru.reserve.sin.data.remote.RemoteChanges
import ru.reserve.sin.data.local.LabelEntity
import ru.reserve.sin.data.local.SyncMetadataEntity

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
                remoteId = null,
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

    suspend fun deleteCategory(category: CategoryEntity) {
        require(category.remoteId == null) {
            "Синхронизированную категорию нельзя удалить. Переместите её в архив."
        }
        require(homeDao.deleteCategoryWithoutTransactions(category.id) == 1) {
            "Категорию с операциями нельзя удалить. Переместите её в архив."
        }
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
                    remoteId = null,
                )
            },
        )
    }

    suspend fun sync(serverUrl: String, token: String): SyncResult {
        val client = ServerSyncClient()
        var categoriesSynced = 0
        var transactionsSynced = 0
        try {
            homeDao.pendingCategories().forEach { category ->
                val remote = client.createCategory(serverUrl, token, category)
                homeDao.markCategorySynced(category.id, remote.id, remote.revision, remote.updatedAt)
                categoriesSynced++
            }
            val pending = homeDao.pendingTransactions()
            val transactionGroups = pending.filter { it.batchId == null }.map { listOf(it) } +
                pending.filter { it.batchId != null }.groupBy { it.batchId }.values
            transactionGroups.forEach { transactions ->
                try {
                    val remoteTransactions = if (transactions.singleOrNull() != null) {
                        listOf(client.createTransaction(serverUrl, token, transactions.single(), remoteCategoryId(transactions.single())))
                    } else {
                        client.createBatch(serverUrl, token, transactions, transactions.associate { it.clientOperationId to remoteCategoryId(it) })
                    }
                    remoteTransactions.forEach { remote ->
                        homeDao.markTransactionSynced(remote.clientOperationId, remote.id, remote.revision, remote.updatedAt)
                        transactionsSynced++
                    }
                } catch (error: Exception) {
                    homeDao.markTransactionsError(transactions.map { it.clientOperationId })
                    throw error
                }
            }
            val metadata = homeDao.syncMetadata()
            val changes = client.changes(serverUrl, token, metadata?.lastServerRevision ?: 0)
            applyChanges(changes)
            homeDao.saveSyncMetadata(SyncMetadataEntity(lastServerRevision = changes.revision, lastSuccessfulSyncAt = nowUtc()))
            return SyncResult(categoriesSynced, transactionsSynced, changes.categories.size + changes.labels.size + changes.transactions.size)
        } finally {
            client.close()
        }
    }

    private suspend fun remoteCategoryId(transaction: TransactionEntity): String =
        requireNotNull(homeDao.categoryRemoteId(transaction.categoryId)) { "Категория ещё не синхронизирована" }

    private suspend fun applyChanges(changes: RemoteChanges) {
        changes.categories.forEach { remote ->
            val existing = remote.clientCategoryId?.let { homeDao.categoryById(it) } ?: homeDao.categoryByRemoteId(remote.id)
            homeDao.upsertCategory(
                CategoryEntity(
                    id = existing?.id ?: remote.clientCategoryId ?: remote.id,
                    name = remote.name,
                    currency = remote.currency,
                    targetAmountRub = remote.targetAmountRub,
                    sortOrder = remote.sortOrder,
                    isArchived = remote.isArchived,
                    isVisibleOnHome = remote.isVisibleOnHome,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    revision = remote.revision,
                    remoteId = remote.id,
                ),
            )
        }
        changes.labels.forEach { remote ->
            val existing = homeDao.labelByRemoteId(remote.id)
            homeDao.upsertLabel(LabelEntity(existing?.id ?: remote.id, remote.name, remote.sortOrder, remote.isArchived, remote.createdAt, remote.updatedAt, remote.revision, remote.id))
        }
        changes.transactions.forEach { remote ->
            val existing = homeDao.transactionByClientOperationId(remote.clientOperationId)
            val category = requireNotNull(homeDao.categoryByRemoteId(remote.categoryId)) { "Сервер вернул неизвестную категорию" }
            val labelId = remote.labelId?.let { homeDao.labelByRemoteId(it)?.id }
            homeDao.upsertTransaction(
                TransactionEntity(existing?.id ?: remote.id, category.id, labelId, remote.batchId, remote.amountRub, remote.comment, remote.occurredAt, remote.createdAt, remote.updatedAt, remote.clientOperationId, remote.isCancelled, SyncStatus.SYNCED, remote.revision, remote.id),
            )
        }
    }

    private fun nowUtc(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    private fun isDate(value: String): Boolean = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
    }.getOrNull() != null
}

data class OperationLine(val categoryId: String, val amountRub: Long)

data class SyncResult(val categoriesSynced: Int, val transactionsSynced: Int, val changesReceived: Int)
