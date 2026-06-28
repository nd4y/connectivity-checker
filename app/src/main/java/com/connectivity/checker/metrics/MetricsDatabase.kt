package com.connectivity.checker.metrics

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PendingMetric::class], version = 1, exportSchema = false)
abstract class MetricsDatabase : RoomDatabase() {

    abstract fun dao(): MetricsDao

    companion object {
        @Volatile private var instance: MetricsDatabase? = null

        fun getInstance(context: Context): MetricsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MetricsDatabase::class.java,
                    "metrics.db"
                ).build().also { instance = it }
            }
    }
}
