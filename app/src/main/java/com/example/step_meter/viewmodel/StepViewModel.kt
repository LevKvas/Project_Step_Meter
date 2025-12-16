package com.example.step_meter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StepViewModel : ViewModel() {
    private val _totalSteps = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps

    private val _hourlySteps = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val hourlySteps: StateFlow<List<Pair<Int, Int>>> = _hourlySteps

    // Добавьте это свойство
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    fun updateSteps(steps: Int) {
        viewModelScope.launch {
            _totalSteps.value = steps
        }
    }

    fun updateHourlySteps(steps: List<Pair<Int, Int>>) {
        viewModelScope.launch {
            _hourlySteps.value = steps
        }
    }

    // Добавьте эту функцию
    fun setServiceRunning(isRunning: Boolean) {
        viewModelScope.launch {
            _isServiceRunning.value = isRunning
        }
    }

    fun resetSteps() {
        viewModelScope.launch {
            _totalSteps.value = 0
            _hourlySteps.value = emptyList()
        }
    }
}