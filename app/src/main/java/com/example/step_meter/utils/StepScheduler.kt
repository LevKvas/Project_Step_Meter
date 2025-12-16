package com.example.step_meter.utils

import android.content.Context
import androidx.work.*
import com.example.step_meter.worker.StepNotificationWorker
import java.util.concurrent.TimeUnit

object StepScheduler {

    fun scheduleHourlyNotifications(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val notificationWork = PeriodicWorkRequestBuilder<StepNotificationWorker>(
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "hourly_step_notification",
            ExistingPeriodicWorkPolicy.KEEP,
            notificationWork
        )
    }

    fun cancelNotifications(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("hourly_step_notification")
    }
}