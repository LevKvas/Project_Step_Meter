package com.example.step_meter.service

import com.example.step_meter.data.database.repository.StepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
import java.text.SimpleDateFormat
import java.util.*

class StepTrackingService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "STEP_TRACKER"
        private const val PREFS_NAME = "step_prefs"
        private const val KEY_LAST_STEP_COUNT = "last_step_count"
        private const val KEY_SAVED_TOTAL = "saved_total"
        private const val KEY_CURRENT_DATE = "current_date"
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var stepDetector: StepDetector
    private var useStepDetector = false

    private var stepSensor: Sensor? = null
    private var lastStepCounterValue = 0f
    private var appTotalSteps = 0

    private var lastSavedHour = -1
    private var lastStepCountForHour = 0
    private var currentDate = ""
    private val repository by lazy { StepRepository.getInstance(this) }

    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "RESET_STEPS_ACTION") {
                Log.d(TAG, "🔄 ПОЛУЧЕНА КОМАНДА СБРОСА!")
                resetStepCounter()
            }
        }
    }

    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "REQUEST_STEPS_ACTION") {
                Log.d(TAG, "📬 Получен запрос на обновление шагов")
                sendStepsToApp(appTotalSteps)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔥 onCreate() вызван")

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Загружаем сохраненные значения
        appTotalSteps = sharedPrefs.getInt(KEY_SAVED_TOTAL, 0)
        lastStepCountForHour = appTotalSteps
        lastStepCounterValue = sharedPrefs.getFloat(KEY_LAST_STEP_COUNT, 0f)

        // Проверяем смену дня
        checkDateChange()

        Log.d(TAG, "📊 Загружено: steps=$appTotalSteps, last=$lastStepCounterValue")

        // Регистрируем receiver для сброса
        val resetFilter = IntentFilter("RESET_STEPS_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resetReceiver, resetFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(resetReceiver, resetFilter)
        }

        // Регистрируем receiver для запросов
        val requestFilter = IntentFilter("REQUEST_STEPS_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(requestReceiver, requestFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(requestReceiver, requestFilter)
        }

        sendStepsToApp(appTotalSteps)
        initSensors()
        updateNotification("Служба запущена")
    }

    private fun initSensors() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

            // ПЕРВЫЙ ВЫБОР: STEP_COUNTER (самый точный)
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            if (stepSensor != null) {
                Log.d(TAG, "✅ Найден STEP_COUNTER: ${stepSensor!!.name}")
                useStepDetector = false

                val success = sensorManager.registerListener(
                    this,
                    stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )

                if (success) {
                    Log.d(TAG, "✅ STEP_COUNTER зарегистрирован")
                    return
                }
            }

            // ВТОРОЙ ВЫБОР: STEP_DETECTOR (встроенный Android)
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

            if (stepSensor != null) {
                Log.d(TAG, "✅ Найден STEP_DETECTOR: ${stepSensor!!.name}")
                useStepDetector = false

                val success = sensorManager.registerListener(
                    this,
                    stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )

                if (success) {
                    Log.d(TAG, "✅ STEP_DETECTOR зарегистрирован")
                    return
                }
            }

            // ТРЕТИЙ ВЫБОР: StepDetector (наш, через акселерометр)
            Log.d(TAG, "⚠ Нет встроенных датчиков, использую StepDetector")
            useStepDetector = true
            initCustomStepDetector()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации: ${e.message}")
        }
    }

    private fun initCustomStepDetector() {
        stepDetector = StepDetector().apply {
            setStepListener(object : StepDetector.StepListener {
                override fun onStep(count: Int) {
                    appTotalSteps = count
                    saveTotalSteps(appTotalSteps)

                    Log.d(TAG, "📱 StepDetector: всего шагов = $appTotalSteps")

                    saveHourlyData()
                    sendStepsToApp(appTotalSteps)
                    updateNotification()
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
            Log.d(TAG, "✅ StepDetector зарегистрирован")
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
        Log.d(TAG, "📈 STEP_COUNTER: $currentSensorValue")

        if (lastStepCounterValue == 0f) {
            // Первое получение
            lastStepCounterValue = currentSensorValue
            saveLastStepValue(currentSensorValue)

            // Начинаем с 0
            appTotalSteps = 0
            lastStepCountForHour = 0

            saveTotalSteps(0)
            saveHourlyData()

            Log.d(TAG, "📌 Первое значение STEP_COUNTER: $currentSensorValue")

        } else {
            // Вычисляем разницу
            val difference = currentSensorValue - lastStepCounterValue

            if (difference > 0) {
                appTotalSteps += difference.toInt()
                lastStepCounterValue = currentSensorValue

                Log.d(TAG, "🆕 STEP_COUNTER: +${difference.toInt()} шагов, всего: $appTotalSteps")

                saveLastStepValue(currentSensorValue)
                saveTotalSteps(appTotalSteps)
                saveHourlyData()

                sendStepsToApp(appTotalSteps)
                updateNotification()
            }
        }
    }

    private fun checkDateChange() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val savedDate = sharedPrefs.getString(KEY_CURRENT_DATE, "")

        if (savedDate != today) {
            Log.d(TAG, "📅 Обнаружена смена дня: $savedDate -> $today")

            // Сбрасываем счетчики для нового дня
            lastSavedHour = -1
            lastStepCountForHour = 0
            lastStepCounterValue = 0f

            // Сохраняем новую дату
            sharedPrefs.edit().putString(KEY_CURRENT_DATE, today).apply()

            // Сбрасываем шаги
            appTotalSteps = 0
            saveTotalSteps(0)
            saveLastStepValue(0f)

            Log.d(TAG, "🔄 Счетчики сброшены для нового дня")
        }

        currentDate = today
    }

    private fun saveHourlyData() {
        try {
            checkDateChange()

            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            Log.d(TAG, "🕐 Текущий час: $currentHour, сохраненный: $lastSavedHour")

            if (currentHour != lastSavedHour) {
                if (lastSavedHour != -1) {
                    val stepsForLastHour = appTotalSteps - lastStepCountForHour
                    Log.d(TAG, "📊 Шагов за час $lastSavedHour: $stepsForLastHour")

                    if (stepsForLastHour > 0) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val saveCalendar = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, lastSavedHour)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }

                                repository.saveStep(saveCalendar.time, lastSavedHour, stepsForLastHour)
                                Log.d(TAG, "💾 Сохранено: $lastSavedHour:00 - $stepsForLastHour шагов")

                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Ошибка сохранения в БД: ${e.message}")
                            }
                        }
                    }
                }

                lastSavedHour = currentHour
                lastStepCountForHour = appTotalSteps

                Log.d(TAG, "🔄 Начинаем новый час $currentHour, базовое значение: $lastStepCountForHour")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сохранения почасовых данных: ${e.message}")
        }
    }

    private fun handleStepDetector() {
        appTotalSteps++
        saveTotalSteps(appTotalSteps)

        Log.d(TAG, "👣 STEP_DETECTOR: Шаг! Всего: $appTotalSteps")

        saveHourlyData()
        sendStepsToApp(appTotalSteps)
        updateNotification()
    }

    private fun resetStepCounter() {
        Log.d(TAG, "🔄 ВЫПОЛНЯЕТСЯ СБРОС ШАГОВ!")

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

        Log.d(TAG, "✅ Счетчик сброшен до 0")
    }

    private fun saveLastStepValue(value: Float) {
        if (!useStepDetector) {
            sharedPrefs.edit().putFloat(KEY_LAST_STEP_COUNT, value).apply()
        }
    }

    private fun saveTotalSteps(steps: Int) {
        sharedPrefs.edit().putInt(KEY_SAVED_TOTAL, steps).apply()
    }

    private fun sendStepsToApp(steps: Int) {
        try {
            Log.d(TAG, "📡 Отправка в приложение: $steps шагов")

            val broadcastIntent = Intent("STEP_UPDATE_ACTION").apply {
                putExtra("steps", steps)
                setPackage(applicationContext.packageName)
            }
            sendBroadcast(broadcastIntent)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка отправки: ${e.message}")
        }
    }

    private fun updateNotification(customText: String? = null) {
        try {
            val currentSteps = sharedPrefs.getInt(KEY_SAVED_TOTAL, 0)
            appTotalSteps = currentSteps

            // Создаем канал только для Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "step_channel",
                    "Шагомер",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Отслеживание шагов"
                    setShowBadge(false)
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel("step_channel") == null) {
                    manager.createNotificationChannel(channel)
                }
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, pendingIntentFlags
            )

            val source = when {
                useStepDetector -> "(алгоритм)"
                stepSensor?.type == Sensor.TYPE_STEP_COUNTER -> "(счетчик)"
                else -> "(детектор)"
            }

            val notificationText = if (currentSteps > 0) {
                customText ?: "Шагов: $currentSteps $source"
            } else {
                customText ?: "Начните ходить!"
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationCompat.Builder(this, "step_channel")
            } else {
                NotificationCompat.Builder(this)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
            }

            val notification = builder
                .setContentTitle("Шагомер")
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_walk)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setSilent(true)
                .build()

            startForeground(1, notification)

            Log.d(TAG, "📢 Уведомление: $notificationText")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка уведомления: ${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶ onStartCommand()")
        sendStepsToApp(appTotalSteps)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "🛑 Сервис остановлен")
        try {
            sensorManager.unregisterListener(this)
            if (useStepDetector) {
                sensorManager.unregisterListener(stepDetector)
            }
            unregisterReceiver(resetReceiver)
            unregisterReceiver(requestReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при остановке: ${e.message}")
        }
        super.onDestroy()
    }
}