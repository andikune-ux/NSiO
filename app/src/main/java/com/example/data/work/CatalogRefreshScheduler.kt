package com.example.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CatalogRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "nsio_periodic_catalog_refresh"
    private const val STARTUP_WORK_NAME = "nsio_startup_catalog_refresh"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        val workManager = WorkManager.getInstance(context.applicationContext)
        // Run a connected sync after app launch instead of waiting up to six hours
        // for the first periodic window. KEEP prevents duplicate startup syncs.
        val startupRequest = OneTimeWorkRequestBuilder<CatalogRefreshWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            STARTUP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            startupRequest
        )
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
