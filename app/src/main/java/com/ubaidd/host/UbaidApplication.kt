package com.ubaidd.host

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.ubaidd.host.service.HostWatchdogWorker
import org.webrtc.PeerConnectionFactory
import java.util.concurrent.TimeUnit

class UbaidApplication : Application() {

    companion object {
        private const val TAG = "UbaidApp"
        private const val WATCHDOG_WORK_NAME = "ubaid_host_watchdog_work"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Firebase gracefully with error handling
        try {
            val app = FirebaseApp.initializeApp(this)
            Log.i(TAG, "Firebase initialized successfully: ${app?.name ?: "default"}")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
        }

        // 2. Initialize WebRTC FieldTrials and native library loader
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)
            Log.i(TAG, "WebRTC PeerConnectionFactory native layer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC native initialization failed", e)
        }

        // 3. Schedule Watchdog WorkManager task
        try {
            val watchdogRequest = PeriodicWorkRequestBuilder<HostWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WATCHDOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                watchdogRequest
            )
            Log.i(TAG, "Enqueued periodic host watchdog worker")
        } catch (e: Exception) {
            Log.e(TAG, "Failed enqueuing watchdog worker", e)
        }
    }
}
