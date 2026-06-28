package com.connectivity.checker.metrics

import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import com.connectivity.checker.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MetricsRepository(
    private val dao: MetricsDao,
    private val settings: SettingsRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Called after every completed check
    suspend fun record(result: CheckResult) {
        if (!settings.isVmConfigured) return
        val payload = buildPayload(result)
        val sent = trySend(payload)
        if (!sent) {
            dao.insert(PendingMetric(payload = payload))
        }
    }

    // Returns number of flushed entries; called by MetricsSyncWorker
    suspend fun flushBuffer(): Int {
        if (!settings.isVmConfigured) return 0
        val batch = dao.getOldest(500)
        if (batch.isEmpty()) return 0
        val combined = batch.joinToString("\n") { it.payload }
        return if (trySend(combined)) {
            dao.delete(batch)
            // Evict entries older than 7 days to prevent unbounded growth
            dao.deleteOlderThan(System.currentTimeMillis() - 7 * 86_400_000L)
            batch.size
        } else 0
    }

    suspend fun pendingCount(): Int = dao.count()

    private suspend fun trySend(payload: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${settings.vmUrl.trimEnd('/')}/api/v1/import/prometheus"
            val body = payload.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val reqBuilder = Request.Builder().url(url).post(body)
            if (settings.vmUsername.isNotBlank()) {
                val cred = "${settings.vmUsername}:${settings.vmPassword}".encodeToByteArray().let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
                reqBuilder.header("Authorization", "Basic $cred")
            }
            client.newCall(reqBuilder.build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildPayload(result: CheckResult): String {
        val cfg = result.config
        val ts = result.lastChecked.takeIf { it > 0 } ?: System.currentTimeMillis()
        val labels = buildString {
            append("name=\"${cfg.name.sanitize()}\"")
            append(",type=\"${cfg.type.name.lowercase()}\"")
            cfg.host?.let  { append(",host=\"$it\"") }
            cfg.url?.let   { append(",url=\"${it.sanitize()}\"") }
            cfg.port?.let  { append(",port=\"$it\"") }
        }
        val up = if (result.status == CheckStatus.SUCCESS) "1" else "0"
        return buildString {
            appendLine("connectivity_check_up{$labels} $up $ts")
            if (result.latencyMs >= 0)
                appendLine("connectivity_check_latency_ms{$labels} ${result.latencyMs} $ts")
        }.trimEnd()
    }

    private fun String.sanitize() = replace("\\", "\\\\").replace("\"", "\\\"")
}
