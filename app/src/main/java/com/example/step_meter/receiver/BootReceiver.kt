package com.example.step_meter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.step_meter.service.StepTrackingService
import com.example.step_meter.utils.StepScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Перезапускаем сервис
            val serviceIntent = Intent(context, StepTrackingService::class.java)
            context.startService(serviceIntent)

            // Перезапускаем планировщик уведомлений
            StepScheduler.scheduleHourlyNotifications(context)
        }
    }
}