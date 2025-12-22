package com.example.step_meter.data.database.dao

import androidx.room.*
import com.example.step_meter.data.database.model.StepData
import java.util.Date
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stepData: StepData)

    @Query("SELECT * FROM step_data WHERE date = :date ORDER BY hour ASC")
    fun getStepsByDate(date: Date): Flow<List<StepData>>

    @Query("SELECT SUM(steps) FROM step_data WHERE date = :date")
    fun getTotalStepsByDate(date: Date): Flow<Int>

    @Query("SELECT * FROM step_data WHERE hour = :hour AND date = :date")
    suspend fun getStepsByHour(date: Date, hour: Int): StepData?

    @Query("DELETE FROM step_data WHERE date < :date")
    suspend fun deleteOldData(date: Date)

    @Query("DELETE FROM step_data WHERE date = :date")
    suspend fun deleteByDate(date: Date)

    // StepDao.kt
    @Query("DELETE FROM step_data WHERE date = :date AND hour = :hour")
    suspend fun deleteByHour(date: Date, hour: Int)

    @Query("DELETE FROM step_data")
    suspend fun deleteAll()
}