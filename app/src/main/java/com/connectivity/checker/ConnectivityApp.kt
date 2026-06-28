package com.connectivity.checker

import android.app.Application
import com.connectivity.checker.metrics.MetricsSyncWorker
import com.google.android.material.color.DynamicColors

class ConnectivityApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply Material You dynamic colour on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Schedule background metric flush (WorkManager)
        MetricsSyncWorker.schedule(this)
    }
}
