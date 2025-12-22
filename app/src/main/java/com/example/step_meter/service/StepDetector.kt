package com.example.step_meter.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import kotlin.math.sqrt

class StepDetector : SensorEventListener {

    private var totalSteps = 0
    private var lastStepTime = 0L
    private val stepDelay = 300000000L
    private var lastAcceleration = 9.8f

    private val stepThreshold = 1.5f
    private val minAcceleration = 8.0f  // Minimum permissible acceleration
    private val maxAcceleration = 20.0f // Maximum permissible acceleration

    // simple filter
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

            // common accelerate
            val acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            // moving average method
            accelerationBuffer[bufferIndex] = acceleration
            bufferIndex = (bufferIndex + 1) % accelerationBuffer.size

            val smoothedAcceleration = accelerationBuffer.average().toFloat()

            val currentTime = System.nanoTime()
            val delta = smoothedAcceleration - lastAcceleration

            val timeSinceLastStep = currentTime - lastStepTime

            if (smoothedAcceleration in minAcceleration..maxAcceleration &&
                delta > stepThreshold &&
                timeSinceLastStep > stepDelay) {

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
        // after the peak there should be a decrease
        return true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // do not use
    }

    fun resetSteps() {
        totalSteps = 0
        lastStepTime = 0L
        lastAcceleration = 9.8f
        accelerationBuffer.fill(0f)
        bufferIndex = 0
        Log.d("StepDetector", "🔄 StepDetector сброшен")
    }


    interface StepListener {
        fun onStep(count: Int)
    }
}