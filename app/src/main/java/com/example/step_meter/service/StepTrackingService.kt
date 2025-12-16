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

class StepTrackingService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "STEP_TRACKER"
        private const val PREFS_NAME = "step_prefs"
        private const val KEY_LAST_STEP_COUNT = "last_step_count"
        private const val KEY_SAVED_TOTAL = "saved_total"
        private const val KEY_IS_RESET = "is_reset_requested"
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var sharedPrefs: SharedPreferences

    // ★ Добавляем BroadcastReceiver для сброса
    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "RESET_STEPS_ACTION") {
                Log.e(TAG, "🔄 ПОЛУЧЕНА КОМАНДА СБРОСА!")
                resetStepCounter()
            }
        }
    }

    private var stepSensor: Sensor? = null
    private var lastStepCounterValue = 0f
    private var appTotalSteps = 0
    private var isResetRequested = false

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "🔥 onCreate() вызван")

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Загружаем сохраненные значения
        appTotalSteps = sharedPrefs.getInt(KEY_SAVED_TOTAL, 0)
        lastStepCounterValue = sharedPrefs.getFloat(KEY_LAST_STEP_COUNT, 0f)
        isResetRequested = sharedPrefs.getBoolean(KEY_IS_RESET, false)

        Log.e(TAG, "📊 Загружено: steps=$appTotalSteps, last=$lastStepCounterValue, reset=$isResetRequested")

        // ★ Регистрируем receiver для сброса
        val filter = IntentFilter("RESET_STEPS_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resetReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(resetReceiver, filter)
        }

        // Отправляем текущее значение
        sendStepsToApp(appTotalSteps)
        showNotification("Запуск отслеживания...")
        initSensors()
    }

    private fun resetStepCounter() {
        Log.e(TAG, "🔄 ВЫПОЛНЯЕТСЯ СБРОС ШАГОВ!")

        // 1. Обнуляем счетчик в приложении
        appTotalSteps = 0

        // 2. Сбрасываем сохраненное значение датчика
        // Это важно! При следующем получении события датчика будем считать от 0
        lastStepCounterValue = 0f

        // 3. Сохраняем в SharedPreferences
        saveTotalSteps(0)
        saveLastStepValue(0f)
        saveResetFlag(true)

        // 4. Уведомляем UI
        sendStepsToApp(0)
        updateNotification()

        Log.e(TAG, "✅ Счетчик сброшен до 0")
    }

    private fun initSensors() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

            // ★ Если был запрос сброса, игнорируем предыдущее значение датчика
            if (isResetRequested) {
                Log.e(TAG, "⚠ Был запрос сброса, игнорируем сохраненное значение датчика")
                lastStepCounterValue = 0f
                saveResetFlag(false) // сбрасываем флаг
            }

            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

            if (stepSensor == null) {
                Log.e(TAG, "⚠ Нет датчиков шагов!")
                showNotification("Нет датчиков шагов")
                return
            }

            Log.e(TAG, "✅ Датчик: ${stepSensor!!.name} (тип: ${stepSensor!!.type})")

            val success = sensorManager.registerListener(
                this,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )

            if (success) {
                Log.e(TAG, "✅ Слушатель зарегистрирован")
                showNotification("Шагов: $appTotalSteps")
            } else {
                Log.e(TAG, "❌ Не удалось зарегистрировать слушатель")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации: ${e.message}")
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
        Log.e(TAG, "🔄 Датчик: $currentSensorValue, Предыдущее: $lastStepCounterValue")

        // ★ ВАЖНОЕ ИЗМЕНЕНИЕ: Правильная логика обработки
        if (lastStepCounterValue == 0f) {
            // Первое получение значения после сброса
            lastStepCounterValue = currentSensorValue
            saveLastStepValue(currentSensorValue)

            // Начинаем с 0 в любом случае
            appTotalSteps = 0
            Log.e(TAG, "📌 Первое значение датчика после сброса: $currentSensorValue")

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

        // Всегда сохраняем текущее состояние
        saveTotalSteps(appTotalSteps)
    }

    private fun handleStepDetector() {
        appTotalSteps++
        saveTotalSteps(appTotalSteps)

        Log.e(TAG, "👣 STEP_DETECTOR: Шаг! Всего: $appTotalSteps")

        sendStepsToApp(appTotalSteps)
        updateNotification()
    }

    private fun saveLastStepValue(value: Float) {
        sharedPrefs.edit().putFloat(KEY_LAST_STEP_COUNT, value).apply()
    }

    private fun saveTotalSteps(steps: Int) {
        sharedPrefs.edit().putInt(KEY_SAVED_TOTAL, steps).apply()
    }

    private fun saveResetFlag(isReset: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_IS_RESET, isReset).apply()
    }

    private fun sendStepsToApp(steps: Int) {
        try {
            Log.e(TAG, "📡 Отправка в приложение: $steps шагов")

            // Основной способ
            val broadcastIntent = Intent("STEP_UPDATE_ACTION").apply {
                putExtra("steps", steps)
                // ★ Добавляем пакет для безопасности
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

            val notificationText = customText ?: "Шагов: $appTotalSteps"

            val notification = NotificationCompat.Builder(this, "step_channel")
                .setContentTitle("Шагомер")
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_walk) // Убедитесь что иконка существует
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
        sendStepsToApp(appTotalSteps)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.e(TAG, "🛑 Сервис остановлен")
        try {
            sensorManager.unregisterListener(this)
            unregisterReceiver(resetReceiver)
        } catch (e: Exception) {
            // Игнорируем
        }
        super.onDestroy()
    }
}