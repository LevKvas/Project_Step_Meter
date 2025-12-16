package com.example.step_meter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.example.step_meter.utils.StepScheduler
import com.example.step_meter.viewmodel.StepViewModel
import com.example.step_meter.ui.theme.Step_meterTheme

class MainActivity : ComponentActivity() {

    // Разрешения для Android 14+
    private val permissions = mutableListOf<String>().apply {
        // Основные разрешения для шагомера
        add(Manifest.permission.ACTIVITY_RECOGNITION)

        // Для foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.FOREGROUND_SERVICE)
        }

        // Для health foreground service (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }

        // Для уведомлений (Android 13+)
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
            // Показать сообщение о необходимости разрешений
            // В реальном приложении нужно обработать этот случай
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    private fun startServices() {
        // Запускаем сервис отслеживания шагов
        val serviceIntent = Intent(this, StepTrackingService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Запускаем планировщик уведомлений
        StepScheduler.scheduleHourlyNotifications(this)
    }

    override fun onResume() {
        super.onResume()
        // При возвращении в приложение проверяем разрешения
        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        StepScheduler.cancelNotifications(this)
    }
}

@Composable
fun DashboardScreen(
    viewModel: StepViewModel = viewModel<StepViewModel>(),
    onRequestPermissions: () -> Unit = {},
    onStartServices: () -> Unit = {}
) {
    val context = LocalContext.current
    val hourlySteps by viewModel.hourlySteps.collectAsState(
        initial = emptyList<Pair<Int, Int>>()
    )
    val totalSteps by viewModel.totalSteps.collectAsState(initial = 0)
    val isServiceRunning by viewModel.isServiceRunning.collectAsState(initial = false)

    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Проверяем разрешения при первом запуске
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
                Text("Для работы шагомера необходимы следующие разрешения:\n\n" +
                        "• Отслеживание физической активности\n" +
                        "• Работа в фоновом режиме\n" +
                        "• Показ уведомлений\n\n" +
                        "Приложение будет запрашивать эти разрешения.")
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
            Button(
                onClick = {
                    // Сброс шагов
                    viewModel.resetSteps()
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Сбросить шаги")
            }
        }
    }
}