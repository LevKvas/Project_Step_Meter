package com.example.step_meter.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.step_meter.data.database.repository.StepRepository
import com.example.step_meter.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.*

class StepNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repository = StepRepository.getInstance(applicationContext)
            val currentDate = Calendar.getInstance().time

            // Получаем общее количество шагов за сегодня
            val totalSteps = repository.getTotalSteps(currentDate).first()

            // Создаем мотивационное сообщение
            val motivationMessage = when {
                totalSteps < 1000 -> "Хорошее начало! Продолжайте в том же духе!"
                totalSteps < 5000 -> "Отлично! Вы на правильном пути!"
                totalSteps < 10000 -> "Потрясающе! Вы почти достигли цели!"
                else -> "Фантастически! Вы превзошли ожидания!"
            }

            // Отправляем уведомление
            NotificationHelper.sendStepNotification(
                applicationContext,
                totalSteps,
                motivationMessage
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}