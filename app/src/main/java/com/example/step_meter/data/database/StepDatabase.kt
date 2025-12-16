package com.example.step_meter.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.step_meter.data.database.dao.StepDao
import androidx.room.TypeConverters
import com.example.step_meter.data.database.converters.DateConverter
import com.example.step_meter.data.database.model.StepData

@Database(
    entities = [StepData::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class StepDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao

    companion object {
        @Volatile
        private var INSTANCE: StepDatabase? = null

        fun getDatabase(context: Context): StepDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StepDatabase::class.java,
                    "step_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}