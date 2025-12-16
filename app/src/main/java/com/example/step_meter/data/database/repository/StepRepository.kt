package com.example.step_meter.data.database.repository

import android.content.Context
import com.example.step_meter.data.database.StepDatabase
import com.example.step_meter.data.database.model.StepData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

// brain of my app

class StepRepository private constructor(context: Context) {

    private val stepDao = StepDatabase.getDatabase(context).stepDao()

    suspend fun saveStep(date: Date, hour: Int, steps: Int) {
        val existingData = stepDao.getStepsByHour(date, hour)

        if (existingData != null) {
            // Обновляем существующую запись
            val updatedData = existingData.copy(steps = steps)
            stepDao.insert(updatedData)
        } else {
            // Создаем новую запись
            val newData = StepData(date = date, hour = hour, steps = steps)
            stepDao.insert(newData)
        }
    }

    fun getHourlySteps(date: Date): Flow<List<Pair<Int, Int>>> {
        return stepDao.getStepsByDate(date).map { stepDataList ->
            val hourlyMap = mutableMapOf<Int, Int>()

            // Инициализируем все часы от 0 до 23
            for (hour in 0..23) {
                hourlyMap[hour] = 0
            }

            // Заполняем данными
            stepDataList.forEach { stepData ->
                hourlyMap[stepData.hour] = stepData.steps
            }

            // Преобразуем в список пар
            hourlyMap.toList().sortedBy { it.first }
        }
    }

    fun getTotalSteps(date: Date): Flow<Int> {
        return stepDao.getStepsByDate(date).map { stepDataList ->
            stepDataList.sumOf { it.steps.toLong() }.toInt()
        }
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