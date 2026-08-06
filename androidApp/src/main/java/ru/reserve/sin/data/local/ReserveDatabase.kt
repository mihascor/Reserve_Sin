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
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [CategoryEntity::class, LabelEntity::class, TransactionEntity::class, SyncMetadataEntity::class],
    version = 1,
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
        ).build()
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
            ) AS hasPendingChanges
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncMetadata(metadata: SyncMetadataEntity)
}
