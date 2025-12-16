package com.example.step_meter.service

import android.app.*
import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.step_meter.MainActivity
import com.example.step_meter.R
import java.util.*

class StepTrackingService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "STEP_TRACKER"
        private const val PREFS_NAME = "step_prefs"
        private const val KEY_LAST_STEP_COUNT = "last_step_count"
        private const val KEY_SAVED_TOTAL = "saved_total"
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var stepDetector: StepDetector
    private var useStepDetector = false

    private var stepSensor: Sensor? = null
    private var lastStepCounterValue = 0f
    private var appTotalSteps = 0

    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "RESET_STEPS_ACTION") {
                Log.e(TAG, "🔄 ПОЛУЧЕНА КОМАНДА СБРОСА!")
                resetStepCounter()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "🔥 onCreate() вызван")

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Загружаем сохраненные значения
        appTotalSteps = sharedPrefs.getInt(KEY_SAVED_TOTAL, 0)
        lastStepCounterValue = sharedPrefs.getFloat(KEY_LAST_STEP_COUNT, 0f)

        Log.e(TAG, "📊 Загружено: steps=$appTotalSteps, last=$lastStepCounterValue")

        // Регистрируем receiver для сброса
        val filter = IntentFilter("RESET_STEPS_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resetReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(resetReceiver, filter)
        }

        sendStepsToApp(appTotalSteps)
        showNotification("Запуск отслеживания...")
        initSensors()
    }

    private fun initSensors() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

            // ★★ ПЕРВЫЙ ВЫБОР: STEP_COUNTER (самый точный)
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            if (stepSensor != null) {
                Log.e(TAG, "✅ Найден STEP_COUNTER: ${stepSensor!!.name}")
                useStepDetector = false

                val success = sensorManager.registerListener(
                    this,
                    stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )

                if (success) {
                    Log.e(TAG, "✅ STEP_COUNTER зарегистрирован")
                    showNotification("Счетчик шагов активен")
                    return
                }
            }

            // ★★ ВТОРОЙ ВЫБОР: STEP_DETECTOR (встроенный Android)
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

            if (stepSensor != null) {
                Log.e(TAG, "✅ Найден STEP_DETECTOR: ${stepSensor!!.name}")
                useStepDetector = false

                val success = sensorManager.registerListener(
                    this,
                    stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )

                if (success) {
                    Log.e(TAG, "✅ STEP_DETECTOR зарегистрирован")
                    showNotification("Детектор шагов активен")
                    return
                }
            }

            // ★★ ТРЕТИЙ ВЫБОР: StepDetector (наш, через акселерометр)
            Log.e(TAG, "⚠ Нет встроенных датчиков, использую StepDetector")
            useStepDetector = true
            initCustomStepDetector()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации: ${e.message}")
            showNotification("Ошибка датчиков")
        }
    }

    private fun initCustomStepDetector() {
        stepDetector = StepDetector().apply {
            setStepListener(object : StepDetector.StepListener {
                override fun onStep(count: Int) {
                    // Для StepDetector count - это общее количество шагов
                    handleStepDetectorEvent(count)
                }
            })
        }

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Log.e(TAG, "❌ Нет даже акселерометра!")
            return
        }

        val success = sensorManager.registerListener(
            stepDetector,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        if (success) {
            Log.e(TAG, "✅ StepDetector зарегистрирован")
            showNotification("Алгоритм подсчета активен")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    handleStepCounter(event.values[0])
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    if (event.values[0] == 1.0f) {
                        handleStepDetector()
                    }
                }
            }
        }
    }

    private fun handleStepCounter(currentSensorValue: Float) {
        Log.e(TAG, "📈 STEP_COUNTER: $currentSensorValue")

        if (lastStepCounterValue == 0f) {
            // Первое получение
            lastStepCounterValue = currentSensorValue
            saveLastStepValue(currentSensorValue)

            // Начинаем с 0
            appTotalSteps = 0
            Log.e(TAG, "📌 Первое значение: $currentSensorValue")

        } else {
            // Вычисляем разницу
            val difference = currentSensorValue - lastStepCounterValue

            if (difference > 0) {
                appTotalSteps += difference.toInt()
                lastStepCounterValue = currentSensorValue

                Log.e(TAG, "🆕 +${difference.toInt()} шагов, всего: $appTotalSteps")

                // Сохраняем и отправляем
                saveLastStepValue(currentSensorValue)
                saveTotalSteps(appTotalSteps)
                sendStepsToApp(appTotalSteps)
                updateNotification()
            }
        }
    }

    private fun handleStepDetector() {
        appTotalSteps++
        saveTotalSteps(appTotalSteps)

        Log.e(TAG, "👣 STEP_DETECTOR: Шаг! Всего: $appTotalSteps")

        sendStepsToApp(appTotalSteps)
        updateNotification()
    }

    private fun handleStepDetectorEvent(count: Int) {
        // Для нашего StepDetector count - это общее количество
        // Нужно вычислить разницу
        val newSteps = count
        val difference = newSteps - appTotalSteps

        if (difference > 0) {
            appTotalSteps = newSteps
            saveTotalSteps(appTotalSteps)

            Log.e(TAG, "📱 StepDetector: +$difference шагов, всего: $appTotalSteps")

            sendStepsToApp(appTotalSteps)
            updateNotification()
        }
    }

    private fun resetStepCounter() {
        Log.e(TAG, "🔄 ВЫПОЛНЯЕТСЯ СБРОС ШАГОВ!")

        appTotalSteps = 0

        if (useStepDetector) {
            stepDetector.resetSteps()
        } else {
            lastStepCounterValue = 0f
        }

        saveTotalSteps(0)
        saveLastStepValue(0f)

        sendStepsToApp(0)
        updateNotification()

        Log.e(TAG, "✅ Счетчик сброшен до 0")
    }

    private fun saveLastStepValue(value: Float) {
        if (!useStepDetector) {  // Только для STEP_COUNTER
            sharedPrefs.edit().putFloat(KEY_LAST_STEP_COUNT, value).apply()
        }
    }

    private fun saveTotalSteps(steps: Int) {
        sharedPrefs.edit().putInt(KEY_SAVED_TOTAL, steps).apply()
    }

    private fun sendStepsToApp(steps: Int) {
        try {
            Log.e(TAG, "📡 Отправка в приложение: $steps шагов")

            // ★★ ВАЖНО: Синхронизация уведомления и приложения
            updateNotification("Шагов: $steps")

            val broadcastIntent = Intent("STEP_UPDATE_ACTION").apply {
                putExtra("steps", steps)
                setPackage(applicationContext.packageName)
            }
            sendBroadcast(broadcastIntent)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка отправки: ${e.message}")
        }
    }

    private fun showNotification(text: String) {
        updateNotification(text)
    }

    private fun updateNotification(customText: String? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "step_channel",
                    "Шагомер",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Отслеживание шагов"
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel("step_channel") == null) {
                    manager.createNotificationChannel(channel)
                }
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val source = when {
                useStepDetector -> "(алгоритм)"
                stepSensor?.type == Sensor.TYPE_STEP_COUNTER -> "(счетчик)"
                else -> "(детектор)"
            }

            val notificationText = customText ?: "Шагов: $appTotalSteps $source"

            val notification = NotificationCompat.Builder(this, "step_channel")
                .setContentTitle("Шагомер")
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_walk)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(false)
                .build()

            startForeground(1, notification)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка уведомления: ${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не нужно логировать
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "▶ onStartCommand()")
        sendStepsToApp(appTotalSteps)  // ★ Синхронизация при старте
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.e(TAG, "🛑 Сервис остановлен")
        try {
            sensorManager.unregisterListener(this)
            if (useStepDetector) {
                sensorManager.unregisterListener(stepDetector)
            }
            unregisterReceiver(resetReceiver)
        } catch (e: Exception) {
            // Игнорируем
        }
        super.onDestroy()
    }
}