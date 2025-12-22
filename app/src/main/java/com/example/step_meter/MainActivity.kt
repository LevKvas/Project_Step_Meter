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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import java.util.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.times
import androidx.compose.foundation.border
import kotlinx.coroutines.delay

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
                    viewModel?.updateSteps(steps, this@MainActivity)
                    viewModel?.setServiceRunning(true)
                }
                "STEP_COUNT_UPDATE" -> {
                    val steps = intent.getIntExtra("step_count", 0)
                    Log.e("MAIN_ACTIVITY", "📡 Получены шаги (альтернативный): $steps")
                    viewModel?.updateSteps(steps, this@MainActivity)
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
    onStartServices: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: StepViewModel = viewModel<StepViewModel>()
    val hourlySteps by viewModel.hourlySteps.collectAsState(initial = emptyList())
    val totalSteps by viewModel.totalSteps.collectAsState(initial = 0)
    val isServiceRunning by viewModel.isServiceRunning.collectAsState(initial = false)

    // Загружаем почасовые данные
    LaunchedEffect(key1 = Unit) {
        viewModel.loadHourlySteps(context)
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    // Диалог запроса разрешений (если нужно)
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Необходимы разрешения") },
            text = {
                Text("Для работы шагомера нужны разрешения на отслеживание активности")
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    onRequestPermissions()
                }) {
                    Text("Продолжить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Позже")
                }
            }
        )
    }

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
                    .height(350.dp)  // ★ Высота для графика
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

                    if (hourlySteps.isEmpty() || hourlySteps.all { it.second == 0 }) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Нет данных о шагах")
                                Text("Пройдите немного, чтобы увидеть график",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        // ★★ ВОТ ЗДЕСЬ ИСПОЛЬЗУЕМ ГРАФИК
                        SimpleScrollableChart(hourlySteps = hourlySteps)
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
                        viewModel.resetSteps(context)
                    }
                ) {
                    Text("Сбросить шаги")
                }
            }
        }
    }
}

@Composable
fun SimpleScrollableChart(hourlySteps: List<Pair<Int, Int>>) {
    val scrollState = rememberScrollState()
    var currentHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }

    // ★★ ВЫЧИСЛЯЕМ recentHours на основе hourlySteps и текущего часа
    val recentHours = remember(hourlySteps, currentHour) {
        // Получаем последние 7 часов относительно текущего часа
        val hoursToShow = (0..6).map { offset ->
            val hour = (currentHour - offset + 24) % 24 // вычитаем offset для последних часов
            hour
        }.reversed() // разворачиваем, чтобы шло от старых к новым

        hoursToShow.map { hour ->
            hour to (hourlySteps.find { it.first == hour }?.second ?: 0)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60 * 1000L) // every minute
            val newHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            if (newHour != currentHour) {
                currentHour = newHour
                Log.d("GRAPH_UPDATE", "🔄 Час сменился: $currentHour")
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Шаги",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp, bottom = 40.dp, start = 40.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                recentHours.forEach { (hour, steps) ->
                    val maxSteps = recentHours.maxOfOrNull { it.second } ?: 1
                    val heightPercentage = if (maxSteps > 0) steps.toFloat() / maxSteps else 0f
                    val barHeight = heightPercentage * 180.dp

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.width(48.dp)
                    ) {
                        // Точка
                        if (steps > 0) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .offset(y = -barHeight)
                                    .background(
                                        color = if (hour == currentHour)
                                            MaterialTheme.colorScheme.secondary
                                        else
                                            MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )

                            // Количество шагов
                            Text(
                                text = if (steps > 1000) "${steps / 1000}k" else steps.toString(),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .offset(y = -barHeight - 16.dp)
                            )
                        }

                        // Время - с ФИКСИРОВАННОЙ высотой
                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = when {
                                    hour < 10 -> "0${hour}:00"
                                    else -> "${hour}:00"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Время (последние 7 часов)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, bottom = 8.dp)
        )
    }
}