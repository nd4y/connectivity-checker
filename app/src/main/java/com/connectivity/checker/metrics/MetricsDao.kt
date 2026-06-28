package com.connectivity.checker.metrics

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MetricsDao {
    @Insert
    suspend fun insert(metric: PendingMetric): Long

    @Query("SELECT * FROM pending_metrics ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getOldest(limit: Int = 500): List<PendingMetric>

    @Delete
    suspend fun delete(metrics: List<PendingMetric>)

    @Query("SELECT COUNT(*) FROM pending_metrics")
    suspend fun count(): Int

    @Query("DELETE FROM pending_metrics WHERE createdAt < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long)
}
