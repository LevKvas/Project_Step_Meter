package com.example.step_meter.viewmodel

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

class StepViewModel : ViewModel() {
    private val _totalSteps = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()

    private val _hourlySteps = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val hourlySteps: StateFlow<List<Pair<Int, Int>>> = _hourlySteps.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun updateSteps(steps: Int) {
        viewModelScope.launch {
            Log.d("StepViewModel", "📊 Обновление шагов: $steps")
            _totalSteps.value = steps
        }
    }

    fun setServiceRunning(isRunning: Boolean) {
        viewModelScope.launch {
            _isServiceRunning.value = isRunning
        }
    }

    // ★★ ИЗМЕНЕННАЯ ФУНКЦИЯ: теперь она только уведомляет о желании сбросить ★★
    fun resetSteps(context: Context) {
        viewModelScope.launch {
            Log.d("StepViewModel", "🔄 Запрошен сброс шагов")

            // Отправляем команду сервису для сброса
            val intent = Intent("RESET_STEPS_ACTION").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }
}