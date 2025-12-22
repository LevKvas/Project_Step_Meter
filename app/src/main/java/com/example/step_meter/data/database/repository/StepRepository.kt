package com.example.step_meter.data.database.repository

import android.content.Context
import android.util.Log
import com.example.step_meter.data.database.StepDatabase
import com.example.step_meter.data.database.model.StepData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import kotlinx.coroutines.flow.distinctUntilChanged

// brain of my app

class StepRepository private constructor(context: Context) {

    private val stepDao = StepDatabase.getDatabase(context).stepDao()

    // StepRepository.kt
    suspend fun saveStep(date: Date, hour: Int, steps: Int) {
        Log.d("REPO_SAVE", "💾 saveStep: дата=$date, час=$hour, шаги=$steps")

        val existingData = stepDao.getStepsByHour(date, hour)
        Log.d("REPO_SAVE", "📊 Существующая запись: $existingData")

        if (existingData != null) {
            val updatedData = existingData.copy(steps = steps)
            stepDao.insert(updatedData)
            Log.d("REPO_SAVE", "🔄 Обновлено: $updatedData")
        } else {
            val newData = StepData(date = date, hour = hour, steps = steps)
            stepDao.insert(newData)
            Log.d("REPO_SAVE", "➕ Создано: $newData")
        }

        // Проверим что сохранилось
        val check = stepDao.getStepsByHour(date, hour)
        Log.d("REPO_SAVE", "✅ Проверка: $check")
    }

    // StepRepository.kt - добавь этот метод
    suspend fun deleteStepsForDate(date: Date) {
        stepDao.deleteByDate(date)
    }

    // Если хочешь, можешь добавить и полную очистку:
    suspend fun deleteAllSteps() {
        stepDao.deleteAll()
    }

    // StepRepository.kt
    suspend fun deleteStepForHour(date: Date, hour: Int) {
        stepDao.deleteByHour(date, hour)
    }

    fun getHourlySteps(date: Date): Flow<List<Pair<Int, Int>>> {
        return stepDao.getStepsByDate(date)
            .map { stepDataList ->
                Log.d("REPO_DEBUG", "📊 Получено из БД: ${stepDataList.size} записей")

                val hourlyMap = mutableMapOf<Int, Int>()

                // Инициализируем все часы от 0 до 23
                for (hour in 0..23) {
                    hourlyMap[hour] = 0
                }

                // Заполняем данными
                stepDataList.forEach { stepData ->
                    hourlyMap[stepData.hour] = stepData.steps
                    Log.d("REPO_DEBUG", "   Час ${stepData.hour}: ${stepData.steps} шагов")
                }

                // Преобразуем в список пар
                val result = hourlyMap.toList().sortedBy { it.first }
                Log.d("REPO_DEBUG", "✅ Сформирован результат: ${result.size} часов")
                result
            }
            .distinctUntilChanged() // ⚠️ ВАЖНО: обновлять только при изменении данных
    }
    companion object {
        @Volatile
        private var INSTANCE: StepRepository? = null

        fun getInstance(context: Context): StepRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = StepRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}