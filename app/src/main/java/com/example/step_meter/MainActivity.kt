package com.example.step_meter

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.step_meter.service.StepTrackingService
import com.example.step_meter.ui.theme.Step_meterTheme
import com.example.step_meter.utils.StepScheduler
import com.example.step_meter.viewmodel.StepViewModel

import androidx.activity.viewModels

import android.hardware.Sensor
import android.hardware.SensorManager


class MainActivity : ComponentActivity() {

    // Разрешения для Android 14+
    private val permissions = mutableListOf<String>().apply {
        add(Manifest.permission.ACTIVITY_RECOGNITION) // for count steps

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.FOREGROUND_SERVICE) // work in the background
        }

        // Для health foreground service (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }

        // for notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val allGranted = permissionsResult.values.all { it }
        if (allGranted) {
            startServices()
        } else {
            Log.e("MAIN_ACTIVITY", "⚠ Не все разрешения получены")
        }
    }

    private val stepUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "STEP_UPDATE_ACTION" -> {
                    val steps = intent.getIntExtra("steps", 0)
                    Log.e("MAIN_ACTIVITY", "📡 Получены шаги: $steps")
                    viewModel?.updateSteps(steps)
                    viewModel?.setServiceRunning(true)
                }
                "STEP_COUNT_UPDATE" -> {
                    val steps = intent.getIntExtra("step_count", 0)
                    Log.e("MAIN_ACTIVITY", "📡 Получены шаги (альтернативный): $steps")
                    viewModel?.updateSteps(steps)
                }
            }
        }
    }
    private val viewModel: StepViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // create all interface
        setContent {
            Step_meterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        onRequestPermissions = { checkAndRequestPermissions() },
                        onStartServices = { startServices() }
                    )
                }
            }
        }
    }

    // user start use my app
    override fun onResume() {
        super.onResume()

        // ★ ПРОВЕРЯЕМ разрешения перед регистрацией
        checkAndRequestPermissions()

        val filter = IntentFilter().apply {
            addAction("STEP_UPDATE_ACTION")
            addAction("STEP_COUNT_UPDATE")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stepUpdateReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(stepUpdateReceiver, filter) // start to get msg from service
            }
            Log.e("MAIN_ACTIVITY", "✅ Receiver зарегистрирован")
        } catch (e: Exception) {
            Log.e("MAIN_ACTIVITY", "❌ Ошибка регистрации receiver: ${e.message}")
        }

        // ★ ПРИ ВОЗОБНОВЛЕНИИ ЗАПУСКАЕМ СЕРВИС
        startServices()
    }

    override fun onPause() { // when something starts to block
        super.onPause()

        // Отписываемся от BroadcastReceiver
        try {
            unregisterReceiver(stepUpdateReceiver)
            Log.e("MAIN_ACTIVITY", "✅ BroadcastReceiver отписан")
        } catch (e: Exception) {
            // Игнорируем если не зарегистрирован
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions)
        } else {
            startServices()
        }
    }

    private fun startServices() { // to run background service
        Log.e("MAIN_ACTIVITY", "🚀 Запуск сервиса...")

        try {
            val serviceIntent = Intent(this, StepTrackingService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Log.e("MAIN_ACTIVITY", "✅ Сервис запущен")

            // Запускаем планировщик уведомлений
            StepScheduler.scheduleHourlyNotifications(this)

        } catch (e: Exception) {
            Log.e("MAIN_ACTIVITY", "❌ Ошибка запуска сервиса: ${e.message}")
        }
    }

    override fun onDestroy() { // when the system completely destroys MainActivity
        super.onDestroy()
        StepScheduler.cancelNotifications(this)
    }
}

// to show results
@Composable
fun DashboardScreen(
    onRequestPermissions: () -> Unit = {},
    onStartServices: () -> Unit = {} // function to start service
) {
    // get data
    val context = LocalContext.current
    val viewModel: StepViewModel = viewModel<StepViewModel>()
    val hourlySteps by viewModel.hourlySteps.collectAsState(
        initial = emptyList<Pair<Int, Int>>()
    )
    val totalSteps by viewModel.totalSteps.collectAsState(initial = 0)
    val isServiceRunning by viewModel.isServiceRunning.collectAsState(initial = false)

    // Локальное состояние для статуса датчиков
    var sensorStatus by remember { mutableStateOf("") }

    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasActivityRecognition = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasActivityRecognition) {
            showPermissionDialog = true
        } else if (!isServiceRunning) {
            onStartServices()
        }
    }

    // Диалог запроса разрешений
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Необходимы разрешения") },
            text = {
                Text(
                    "Для работы шагомера необходимы следующие разрешения:\n\n" +
                            "• Отслеживание физической активности\n" +
                            "• Работа в фоновом режиме\n" +
                            "• Показ уведомлений\n\n" +
                            "Приложение будет запрашивать эти разрешения."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        onRequestPermissions()
                    }
                ) {
                    Text("Продолжить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text("Позже")
                }
            }
        )
    }

    // Функция для проверки датчиков
    fun checkSensors() {
        try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

            val sensorInfo = StringBuilder()
            sensorInfo.append("ПРОВЕРКА ДАТЧИКОВ:\n")
            sensorInfo.append("• STEP_COUNTER: ${if (stepCounter != null) "✅ Есть (${stepCounter.name})" else "❌ Нет"}\n")
            sensorInfo.append("• STEP_DETECTOR: ${if (stepDetector != null) "✅ Есть (${stepDetector.name})" else "❌ Нет"}\n")
            sensorInfo.append("\nКак работают датчики:\n")
            sensorInfo.append("- STEP_COUNTER: Считает ВСЕ шаги с последней перезагрузки\n")
            sensorInfo.append("- STEP_DETECTOR: Детектирует КАЖДЫЙ шаг отдельно\n")

            if (stepCounter == null && stepDetector == null) {
                sensorInfo.append("\n⚠ ВНИМАНИЕ: На устройстве НЕТ датчиков шагов!\n")
                sensorInfo.append("Приложение не сможет считать шаги.")
            }

            sensorStatus = sensorInfo.toString()
        } catch (e: Exception) {
            sensorStatus = "❌ Ошибка проверки: ${e.message}"
        }
    }
    // create interface
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Шагомер",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Всего шагов сегодня",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$totalSteps",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isServiceRunning) {
                        Text(
                            text = "✓ Служба активна",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "⚠ Служба неактивна",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = onStartServices,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Запустить отслеживание")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    Text(
                        text = "Активность по часам",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (hourlySteps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Данные о шагах отсутствуют")
                        }
                    } else {
                        // Простой список вместо графика для начала
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            hourlySteps.forEach { (hour, steps) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$hour:00")
                                    Text("$steps шагов")
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        // Сброс шагов
                        viewModel.resetSteps(context)
                    }
                ) {
                    Text("Сбросить шаги")
                }
            }
        }
    }
}