package ru.reserve.sin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [CategoryEntity::class, LabelEntity::class, TransactionEntity::class, SyncMetadataEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(SyncStatusConverter::class)
abstract class ReserveDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao

    companion object {
        fun create(context: Context): ReserveDatabase = Room.databaseBuilder(
            context,
            ReserveDatabase::class.java,
            "reserve-sin.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE categories ADD COLUMN remoteId TEXT")
                database.execSQL("ALTER TABLE labels ADD COLUMN remoteId TEXT")
                database.execSQL("ALTER TABLE transactions ADD COLUMN remoteId TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE categories ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                database.execSQL("UPDATE categories SET syncStatus = CASE WHEN remoteId IS NULL THEN 'PENDING' ELSE 'SYNCED' END")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE labels ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                database.execSQL("UPDATE labels SET syncStatus = CASE WHEN remoteId IS NULL THEN 'PENDING' ELSE 'SYNCED' END")
            }
        }
    }
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val targetAmountRub: Long?,
    val sortOrder: Long,
    val isArchived: Boolean,
    val isVisibleOnHome: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val revision: Long,
    val remoteId: String?,
    val syncStatus: SyncStatus,
)

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Long,
    val isArchived: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val revision: Long,
    val remoteId: String?,
    val syncStatus: SyncStatus,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val labelId: String?,
    val batchId: String?,
    val amountRub: Long,
    val comment: String?,
    val occurredAt: String,
    val createdAt: String,
    val updatedAt: String,
    val clientOperationId: String,
    val isCancelled: Boolean,
    val syncStatus: SyncStatus,
    val revision: Long?,
    val remoteId: String?,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastServerRevision: Long,
    val lastSuccessfulSyncAt: String?,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

enum class SyncStatus {
    PENDING,
    SYNCED,
    ERROR,
    CANCEL_PENDING,
}

class SyncStatusConverter {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}

data class HomeCategoryRow(
    val id: String,
    val name: String,
    val targetAmountRub: Long?,
    val balanceRub: Long,
    val hasPendingChanges: Boolean,
)

data class HistoryTransactionRow(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val labelId: String?,
    val labelName: String?,
    val batchId: String?,
    val amountRub: Long,
    val comment: String?,
    val occurredAt: String,
    val isCancelled: Boolean,
    val syncStatus: SyncStatus,
)

@Dao
interface HomeDao {
    @Query(
        """
        SELECT
            c.id,
            c.name,
            c.targetAmountRub,
            COALESCE(SUM(CASE WHEN t.isCancelled = 0 THEN t.amountRub ELSE 0 END), 0) AS balanceRub,
            EXISTS(
                SELECT 1
                FROM transactions pending
                WHERE pending.categoryId = c.id AND pending.syncStatus != 'SYNCED'
            ) OR c.syncStatus != 'SYNCED' AS hasPendingChanges
        FROM categories c
        LEFT JOIN transactions t ON t.categoryId = c.id
        WHERE c.isArchived = 0 AND c.isVisibleOnHome = 1
        GROUP BY c.id
        ORDER BY c.sortOrder, c.id
        """,
    )
    fun observeHomeCategories(): Flow<List<HomeCategoryRow>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.isCancelled = 0 THEN t.amountRub ELSE 0 END), 0)
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE c.isArchived = 0
        """,
    )
    fun observeTotalBalance(): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE syncStatus != 'SYNCED'")
    fun observePendingTransactionCount(): Flow<Int>

    @Query("SELECT lastSuccessfulSyncAt FROM sync_metadata WHERE id = 1")
    fun observeLastSuccessfulSyncAt(): Flow<String?>

    @Query("SELECT * FROM sync_metadata WHERE id = 1")
    suspend fun syncMetadata(): SyncMetadataEntity?

    @Query("SELECT * FROM categories ORDER BY isArchived, sortOrder, id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM labels ORDER BY isArchived, sortOrder, id")
    fun observeLabels(): Flow<List<LabelEntity>>

    @Query("SELECT MAX(sortOrder) FROM labels") suspend fun lastLabelSortOrder(): Long?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLabel(label: LabelEntity)
    @Update suspend fun updateLabel(label: LabelEntity)
    @Query("SELECT * FROM labels WHERE syncStatus IN ('PENDING', 'ERROR') ORDER BY sortOrder, id") suspend fun pendingLabels(): List<LabelEntity>
    @Query("UPDATE labels SET remoteId=:remoteId, revision=:revision, syncStatus='SYNCED', updatedAt=:updatedAt WHERE id=:localId") suspend fun markLabelSynced(localId: String, remoteId: String, revision: Long, updatedAt: String)
    @Query("UPDATE labels SET syncStatus='ERROR' WHERE id=:localId") suspend fun markLabelError(localId: String)
    @Query("SELECT remoteId FROM labels WHERE id=:localId") suspend fun labelRemoteId(localId: String): String?

    @Query(
        """
        SELECT
            t.id,
            t.categoryId,
            c.name AS categoryName,
            t.labelId,
            l.name AS labelName,
            t.batchId,
            t.amountRub,
            t.comment,
            t.occurredAt,
            t.isCancelled,
            t.syncStatus
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        LEFT JOIN labels l ON l.id = t.labelId
        WHERE (:after IS NULL OR substr(t.occurredAt, 1, 10) >= :after)
          AND (:before IS NULL OR substr(t.occurredAt, 1, 10) <= :before)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:labelId IS NULL OR t.labelId = :labelId)
          AND (:direction IS NULL OR (:direction = 'INCOME' AND t.amountRub > 0) OR (:direction = 'EXPENSE' AND t.amountRub < 0))
          AND (:includeCancelled = 1 OR t.isCancelled = 0)
          AND (:onlyUnsynced = 0 OR t.syncStatus != 'SYNCED')
        ORDER BY t.occurredAt DESC, t.createdAt DESC, t.id DESC
        """,
    )
    fun observeHistoryTransactions(
        after: String?,
        before: String?,
        categoryId: String?,
        labelId: String?,
        direction: String?,
        includeCancelled: Boolean,
        onlyUnsynced: Boolean,
    ): Flow<List<HistoryTransactionRow>>

    @Query("SELECT MAX(sortOrder) FROM categories")
    suspend fun lastCategorySortOrder(): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query(
        """
        DELETE FROM categories
        WHERE id = :categoryId
          AND NOT EXISTS (SELECT 1 FROM transactions WHERE categoryId = :categoryId)
        """,
    )
    suspend fun deleteCategoryWithoutTransactions(categoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM categories WHERE syncStatus IN ('PENDING', 'ERROR') ORDER BY sortOrder, id")
    suspend fun pendingCategories(): List<CategoryEntity>

    @Query("UPDATE categories SET remoteId = :remoteId, revision = :revision, syncStatus = 'SYNCED', updatedAt = :updatedAt WHERE id = :localId")
    suspend fun markCategorySynced(localId: String, remoteId: String, revision: Long, updatedAt: String)

    @Query("UPDATE categories SET syncStatus = 'ERROR' WHERE id = :localId")
    suspend fun markCategoryError(localId: String)

    @Query("SELECT * FROM transactions WHERE syncStatus IN ('PENDING', 'ERROR') AND isCancelled = 0 ORDER BY occurredAt, id")
    suspend fun pendingTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE syncStatus = 'CANCEL_PENDING' AND remoteId IS NOT NULL ORDER BY occurredAt, id")
    suspend fun pendingCancellations(): List<TransactionEntity>

    @Query("SELECT remoteId FROM categories WHERE id = :localId")
    suspend fun categoryRemoteId(localId: String): String?

    @Query("UPDATE transactions SET remoteId = :remoteId, revision = :revision, syncStatus = 'SYNCED', updatedAt = :updatedAt WHERE clientOperationId = :clientOperationId")
    suspend fun markTransactionSynced(clientOperationId: String, remoteId: String, revision: Long, updatedAt: String)

    @Query("UPDATE transactions SET syncStatus = 'ERROR' WHERE clientOperationId IN (:clientOperationIds)")
    suspend fun markTransactionsError(clientOperationIds: List<String>)

    @Query("UPDATE transactions SET isCancelled = 1, syncStatus = CASE WHEN remoteId IS NULL THEN 'SYNCED' ELSE 'CANCEL_PENDING' END, updatedAt = :updatedAt WHERE id IN (:transactionIds) AND isCancelled = 0")
    suspend fun cancelTransactions(transactionIds: List<String>, updatedAt: String): Int

    @Query("UPDATE transactions SET syncStatus = 'SYNCED', revision = :revision, updatedAt = :updatedAt WHERE id = :localId")
    suspend fun markCancellationSynced(localId: String, revision: Long, updatedAt: String)

    @Query("UPDATE transactions SET syncStatus = 'ERROR' WHERE id IN (:transactionIds)")
    suspend fun markCancellationsError(transactionIds: List<String>)

    @Query("SELECT * FROM categories WHERE remoteId = :remoteId LIMIT 1")
    suspend fun categoryByRemoteId(remoteId: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun categoryById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("SELECT * FROM labels WHERE remoteId = :remoteId LIMIT 1")
    suspend fun labelByRemoteId(remoteId: String): LabelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLabel(label: LabelEntity)

    @Query("SELECT * FROM transactions WHERE clientOperationId = :clientOperationId LIMIT 1")
    suspend fun transactionByClientOperationId(clientOperationId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncMetadata(metadata: SyncMetadataEntity)
}
