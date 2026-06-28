package com.connectivity.checker.metrics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_metrics")
data class PendingMetric(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val payload: String,                        // Prometheus text-format lines
    val createdAt: Long = System.currentTimeMillis()
)
