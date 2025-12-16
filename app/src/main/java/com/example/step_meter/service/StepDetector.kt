package com.example.step_meter.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import kotlin.math.sqrt

class StepDetector : SensorEventListener {

    private var totalSteps = 0
    private var lastStepTime = 0L
    private val stepDelay = 300000000L // 300ms между шагами (нормальная ходьба)
    private var lastAcceleration = 9.8f // Начинаем с гравитации (9.8 м/с²)

    // ★ ОПТИМИЗИРОВАННЫЕ ПОРОГИ (более чувствительные)
    private val stepThreshold = 1.5f // Уменьшили порог
    private val minAcceleration = 8.0f  // Минимальное допустимое ускорение
    private val maxAcceleration = 20.0f // Максимальное допустимое ускорение

    // ★ ПРОСТОЙ ФИЛЬТР (без сложной логики)
    private val accelerationBuffer = FloatArray(3)
    private var bufferIndex = 0

    private var stepListener: StepListener? = null

    fun setStepListener(listener: StepListener) {
        stepListener = listener
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Вычисляем общее ускорение
            val acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            // ★ ПРОСТОЕ СГЛАЖИВАНИЕ (скользящее среднее)
            accelerationBuffer[bufferIndex] = acceleration
            bufferIndex = (bufferIndex + 1) % accelerationBuffer.size

            val smoothedAcceleration = accelerationBuffer.average().toFloat()

            val currentTime = System.nanoTime()
            val delta = smoothedAcceleration - lastAcceleration

            // ★ ПРОСТЫЕ УСЛОВИЯ ДЛЯ ОБНАРУЖЕНИЯ ШАГА:
            // 1. Ускорение в разумных пределах (не слишком мало/много)
            // 2. Резкое увеличение ускорения (положительный пик)
            // 3. Прошел минимальный интервал между шагами

            val timeSinceLastStep = currentTime - lastStepTime

            if (smoothedAcceleration in minAcceleration..maxAcceleration &&
                delta > stepThreshold && // Положительный пик
                timeSinceLastStep > stepDelay) {

                // ★ ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: пик должен смениться спадом
                // Это помогает отличить шаг от постоянной тряски
                if (isRealStepPattern(smoothedAcceleration, lastAcceleration)) {

                    lastStepTime = currentTime
                    totalSteps++

                    Log.d("StepDetector",
                        "✅ Шаг #$totalSteps | " +
                                "Ускорение: ${"%.2f".format(smoothedAcceleration)} | " +
                                "Дельта: ${"%.2f".format(delta)} | " +
                                "Время: ${timeSinceLastStep / 1000000}ms")

                    stepListener?.onStep(totalSteps)
                }
            }

            lastAcceleration = smoothedAcceleration
        }
    }

    private fun isRealStepPattern(current: Float, previous: Float): Boolean {
        // Простая проверка: после пика должно быть уменьшение
        // (шаг = удар ногой → отскок)
        return true // Пока упростим, можно доработать
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не используется
    }

    fun resetSteps() {
        totalSteps = 0
        lastStepTime = 0L
        lastAcceleration = 9.8f
        accelerationBuffer.fill(0f)
        bufferIndex = 0
        Log.d("StepDetector", "🔄 StepDetector сброшен")
    }

    fun getStepCount(): Int = totalSteps

    interface StepListener {
        fun onStep(count: Int)
    }
}