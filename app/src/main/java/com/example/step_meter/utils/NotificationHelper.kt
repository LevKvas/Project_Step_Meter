package com.example.step_meter.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.step_meter.MainActivity

object NotificationHelper {

    private const val CHANNEL_ID = "step_notifications"
    private const val CHANNEL_NAME = "Уведомления о шагах"

    fun sendStepNotification(context: Context, steps: Int, motivation: String) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        // create the channel for notifications
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о пройденных шагах"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open the app when clicking on the notification
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // create notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Ваша активность сегодня")
            .setContentText("Вы прошли $steps шагов. $motivation")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(1, notification)
    }
}