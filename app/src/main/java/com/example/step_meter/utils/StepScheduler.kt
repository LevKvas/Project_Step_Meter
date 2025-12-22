package com.example.step_meter.utils

import android.content.Context
import androidx.work.*
import com.example.step_meter.worker.StepNotificationWorker
import java.util.concurrent.TimeUnit

// reminds the user of their progress once an hour

object StepScheduler {

    fun scheduleHourlyNotifications(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true) // the battery is not low
            .build()

        // Create a periodic task
        val notificationWork = PeriodicWorkRequestBuilder<StepNotificationWorker>(
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        // Running the task
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