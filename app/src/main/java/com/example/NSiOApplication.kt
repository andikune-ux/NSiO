package com.example

import android.app.Application
import com.lagradost.cloudstream3.setCloudstreamHttpClient
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
    }
}
