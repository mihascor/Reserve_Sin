package ru.reserve.sin.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.ReserveDatabase
import ru.reserve.sin.data.settings.ServerSettingsRepository

class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settingsRepository = ServerSettingsRepository(applicationContext)
        val settings = settingsRepository.settings.first()
        val token = settingsRepository.token()
        if (token.isNullOrBlank()) return Result.success()

        return runCatching {
            ReserveRepository(ReserveDatabase.create(applicationContext)).sync(settings.serverUrl, token)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}

object SyncWorkScheduler {
    private const val IMMEDIATE_SYNC_WORK = "reserve-sin-immediate-sync"
    private const val PERIODIC_SYNC_WORK = "reserve-sin-periodic-sync"
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val immediateRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(IMMEDIATE_SYNC_WORK, ExistingWorkPolicy.KEEP, immediateRequest)
        workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodicRequest)
    }
}
