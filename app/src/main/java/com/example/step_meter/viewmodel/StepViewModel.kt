package com.example.step_meter.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.step_meter.data.database.repository.StepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import java.util.*

class StepViewModel : ViewModel() {
    private val _totalSteps = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()

    private val _hourlySteps = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val hourlySteps: StateFlow<List<Pair<Int, Int>>> = _hourlySteps.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun updateSteps(steps: Int, context: Context) {
        viewModelScope.launch {
            Log.d("StepViewModel", "📊 Обновление шагов: $steps")
            _totalSteps.value = steps
            loadHourlySteps(context)
        }
    }

    fun loadHourlySteps(context: Context) {
        viewModelScope.launch {
            try {
                val repository = StepRepository.getInstance(context)
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val today = calendar.time

                repository.getHourlySteps(today).collect { stepsList ->
                    _hourlySteps.value = stepsList
                    Log.d("StepViewModel", "📈 Загружено почасовых данных: ${stepsList.size} часов")

                    // Логируем для отладки
                    stepsList.forEach { (hour, steps) ->
                        if (steps > 0) {
                            Log.d("StepViewModel", "   $hour:00 - $steps шагов")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StepViewModel", "❌ Ошибка загрузки почасовых данных: ${e.message}")
            }
        }
    }

    // ★★ ДОБАВЬ ЭТОТ МЕТОД ТОЖЕ (для обновления из сервиса)
    fun updateHourlySteps(stepsList: List<Pair<Int, Int>>) {
        viewModelScope.launch {
            _hourlySteps.value = stepsList
            Log.d("StepViewModel", "🔄 Обновление почасовых данных: ${stepsList.size} часов")
        }
    }

    fun setServiceRunning(isRunning: Boolean) {
        viewModelScope.launch {
            _isServiceRunning.value = isRunning
        }
    }

    fun resetSteps(context: Context) {
        viewModelScope.launch {
            Log.d("StepViewModel", "🔄 Запрошен сброс шагов")

            val intent = Intent("RESET_STEPS_ACTION").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }
}