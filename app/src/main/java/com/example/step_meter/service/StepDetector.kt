package com.example.step_meter.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

// physics of my application

class StepDetector : SensorEventListener {

    private var stepCount = 0
    private var lastStepTime = 0L
    private val stepDelay = 250000000L // Наносекунды между шагами
    private var lastAcceleration = 0f

    // Порог для обнаружения шага
    private val stepThreshold = 2.0f
    private var acceleration = 0f

    private var stepListener: StepListener? = null

    fun setStepListener(listener: StepListener) {
        stepListener = listener
    }

    override fun onSensorChanged(event: SensorEvent) { // when data on sensor changed
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Вычисляем ускорение
            acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            val currentTime = System.nanoTime()

            // Проверяем, превышает ли ускорение порог и прошел ли достаточный интервал времени
            if ((acceleration - lastAcceleration) > stepThreshold &&
                (currentTime - lastStepTime) > stepDelay) {

                lastStepTime = currentTime
                stepCount++

                // Уведомляем слушателя о новом шаге
                stepListener?.onStep(stepCount)
            }

            lastAcceleration = acceleration
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не используется
    }

    fun resetSteps() {
        stepCount = 0
    }

    fun getStepCount(): Int = stepCount

    interface StepListener {
        fun onStep(count: Int)
    }
}