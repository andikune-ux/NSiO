package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.lagradost.cloudstream3.setCloudstreamHttpClient
import com.example.data.work.CatalogRefreshScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NSiOApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setCloudstreamHttpClient(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        CrashLogStore.install(this)

        // WorkManager's AndroidX Startup initializer normally runs before Application.onCreate.
        // Explicitly initialize as a fallback for hosts/tests where that provider is not run.
        try {
            WorkManager.initialize(this, Configuration.Builder().build())
        } catch (_: IllegalStateException) {
            // Already initialized by AndroidX Startup; keep the existing configuration.
        }
        CatalogRefreshScheduler.schedule(this)
    }
}
