package com.example.step_meter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.step_meter.service.StepTrackingService
import com.example.step_meter.utils.StepScheduler

// Recover important data after reboot

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart the service
            val serviceIntent = Intent(context, StepTrackingService::class.java)
            context.startService(serviceIntent)

            // Restart the notification scheduler
            StepScheduler.scheduleHourlyNotifications(context)
        }
    }
}