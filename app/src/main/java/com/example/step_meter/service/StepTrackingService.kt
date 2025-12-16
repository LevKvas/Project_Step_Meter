package com.example.step_meter.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.step_meter.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.step_meter.data.database.repository.StepRepository
import java.util.*

class StepTrackingService : Service() {

    private lateinit var sensorManager: SensorManager
    private lateinit var stepDetector: StepDetector
    private lateinit var wakeLock: PowerManager.WakeLock
    private var stepsJob: Job? = null

    private val stepRepository by lazy {
        StepRepository.getInstance(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()

        // Инициализация WakeLock для работы в фоне
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "StepTracker::StepTrackingService"
        )
        wakeLock.acquire()

        // Настройка уведомления для foreground service
        startForegroundService()

        // Инициализация детектора шагов
        initStepDetector()
    }

    private fun startForegroundService() {
        val channelId = "step_tracker_channel"
        val channelName = "Step Tracker Service"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Step Tracker")
                .setContentText("Отслеживание шагов активно")
                .setSmallIcon(android.R.drawable.ic_dialog_info) // временно
                .setContentIntent(pendingIntent) // ← работает здесь
                .setPriority(Notification.PRIORITY_LOW)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Step Tracker")
                .setContentText("Отслеживание шагов активно")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_LOW)
                .build()
        }

        startForeground(1, notification)
    }

    private fun initStepDetector() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepDetector = StepDetector()

        stepDetector.setStepListener(object : StepDetector.StepListener {
            override fun onStep(count: Int) {
                saveStepToDatabase(count)
            }
        })

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(
            stepDetector,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    private fun saveStepToDatabase(count: Int) {
        stepsJob?.cancel()
        stepsJob = CoroutineScope(Dispatchers.IO).launch {
            val calendar = Calendar.getInstance()
            val currentDate = calendar.time
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            // Сохраняем шаги для текущего часа
            stepRepository.saveStep(currentDate, currentHour, count)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stepsJob?.cancel()
        sensorManager.unregisterListener(stepDetector)
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}