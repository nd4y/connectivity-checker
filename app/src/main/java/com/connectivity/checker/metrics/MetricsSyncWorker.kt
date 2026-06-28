package com.connectivity.checker.metrics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.connectivity.checker.settings.SettingsRepository
import java.util.concurrent.TimeUnit

class MetricsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = MetricsDatabase.getInstance(applicationContext)
        val repo = MetricsRepository(db.dao(), SettingsRepository(applicationContext))
        repo.flushBuffer()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "metrics_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MetricsSyncWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
