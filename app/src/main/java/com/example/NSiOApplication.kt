package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.lagradost.cloudstream3.setCloudstreamHttpClient
import com.lagradost.nicehttp.addGenericDns
import com.example.data.work.CatalogRefreshScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NSiOApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setCloudstreamHttpClient(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                // Resolve provider hosts through DNS-over-HTTPS to reduce ISP DNS
                // failures or poisoned DNS responses affecting CloudStream plugins.
                .addGenericDns(
                    "https://dns.google/dns-query",
                    listOf("8.8.8.8", "8.8.4.4")
                )
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
