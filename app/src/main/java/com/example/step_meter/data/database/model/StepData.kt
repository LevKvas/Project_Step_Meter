package com.example.step_meter.data.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "step_data")
data class StepData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Date,
    val hour: Int,
    val steps: Int,
    val timestamp: Long = System.currentTimeMillis()
)