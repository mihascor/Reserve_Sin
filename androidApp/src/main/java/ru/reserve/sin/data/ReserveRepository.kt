package ru.reserve.sin.data

import ru.reserve.sin.data.local.HomeCategoryRow
import ru.reserve.sin.data.local.HomeDao
import ru.reserve.sin.data.local.ReserveDatabase
import kotlinx.coroutines.flow.Flow

class ReserveRepository(database: ReserveDatabase) {
    private val homeDao: HomeDao = database.homeDao()

    fun observeHomeCategories(): Flow<List<HomeCategoryRow>> = homeDao.observeHomeCategories()

    fun observeTotalBalance(): Flow<Long> = homeDao.observeTotalBalance()

    fun observePendingTransactionCount(): Flow<Int> = homeDao.observePendingTransactionCount()

    fun observeLastSuccessfulSyncAt(): Flow<String?> = homeDao.observeLastSuccessfulSyncAt()
}
