package com.connectivity.checker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.connectivity.checker.checker.CheckerFactory
import com.connectivity.checker.metrics.MetricsDatabase
import com.connectivity.checker.metrics.MetricsRepository
import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import com.connectivity.checker.settings.SettingsRepository
import com.connectivity.checker.yaml.YamlExporter
import com.connectivity.checker.yaml.YamlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val metricsRepo = MetricsRepository(
        MetricsDatabase.getInstance(app).dao(), settings
    )

    // ── Checks state ─────────────────────────────────────────────────────────
    private val _checks = MutableStateFlow<List<CheckResult>>(emptyList())
    val checks: StateFlow<List<CheckResult>> = _checks.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val summary: StateFlow<String> = _checks.map { list ->
        when {
            list.isEmpty() -> "No config loaded"
            list.all { it.status == CheckStatus.PENDING } -> "${list.size} checks ready"
            else -> {
                val ok   = list.count { it.status == CheckStatus.SUCCESS }
                val fail = list.count { it.status == CheckStatus.FAILURE }
                val run  = list.count { it.status == CheckStatus.RUNNING }
                buildString {
                    append("$ok/${list.size} passing")
                    if (fail > 0) append(" • $fail failed")
                    if (run  > 0) append(" • $run running")
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "No config loaded")

    // ── VM settings (exposed so VmSettingsDialog can read them) ─────────────
    val vmUrl:      String get() = settings.vmUrl
    val vmUsername: String get() = settings.vmUsername
    val vmPassword: String get() = settings.vmPassword

    // ── Periodic jobs (index → coroutine) ────────────────────────────────────
    private val periodicJobs = mutableMapOf<Int, Job>()

    init {
        restoreSavedConfig()
        autoSaveChecks()
    }

    sealed class UiEvent {
        data class ShowError(val message: String)   : UiEvent()
        data class ShowSuccess(val message: String) : UiEvent()
    }

    // ── Persistence ───────────────────────────────────────────────────────────
    private fun restoreSavedConfig() {
        val yaml = settings.savedChecksYaml
        if (yaml.isBlank()) return
        viewModelScope.launch {
            try {
                val configs = withContext(Dispatchers.Default) { YamlParser.parse(yaml) }
                if (configs.isNotEmpty()) {
                    _checks.value = configs.map { CheckResult(it) }
                    scheduleAllPeriodic()
                }
            } catch (_: Exception) {}
        }
    }

    private fun autoSaveChecks() {
        viewModelScope.launch {
            _checks
                .drop(1)          // skip the initial empty emission
                .debounce(500)
                .collect { checks ->
                    val yaml = if (checks.isEmpty()) ""
                    else withContext(Dispatchers.Default) {
                        YamlExporter.export(checks.map { it.config })
                    }
                    settings.savedChecksYaml = yaml
                }
        }
    }

    // ── Config loading ────────────────────────────────────────────────────────
    fun loadConfig(content: String) {
        viewModelScope.launch {
            try {
                val configs = withContext(Dispatchers.Default) { YamlParser.parse(content) }
                if (configs.isEmpty()) {
                    _events.emit(UiEvent.ShowError("No checks found in config"))
                } else {
                    cancelAllPeriodicJobs()
                    _checks.value = configs.map { CheckResult(it) }
                    scheduleAllPeriodic()
                    _events.emit(UiEvent.ShowSuccess("Loaded ${configs.size} check(s)"))
                }
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowError("Parse error: ${e.message}"))
            }
        }
    }

    fun exportYaml(): String = YamlExporter.export(_checks.value.map { it.config })

    // ── CRUD ─────────────────────────────────────────────────────────────────
    fun addCheck(config: CheckConfig) {
        _checks.update { it + CheckResult(config) }
        val idx = _checks.value.lastIndex
        schedulePeriodicIfNeeded(idx, config.interval)
    }

    fun updateCheck(index: Int, config: CheckConfig) {
        periodicJobs.remove(index)?.cancel()
        _checks.update { list ->
            list.toMutableList().also { it[index] = CheckResult(config) }
        }
        schedulePeriodicIfNeeded(index, config.interval)
    }

    fun deleteCheck(index: Int) {
        periodicJobs.remove(index)?.cancel()
        _checks.update { list -> list.toMutableList().also { it.removeAt(index) } }
        // Re-map jobs whose index shifted down
        val shifted = periodicJobs.entries.filter { it.key > index }
            .associate { (k, v) -> (k - 1) to v }
        periodicJobs.keys.removeAll { it >= index }
        periodicJobs.putAll(shifted)
    }

    // ── Run ───────────────────────────────────────────────────────────────────
    fun runAll() {
        if (_isRunning.value) return
        viewModelScope.launch {
            _isRunning.value = true
            _checks.update { list -> list.map { it.copy(status = CheckStatus.RUNNING) } }

            val snapshot = _checks.value
            val jobs = snapshot.mapIndexed { i, result ->
                async(Dispatchers.IO) {
                    val updated = CheckerFactory.get(result.config.type).check(result.config)
                    _checks.update { list -> list.toMutableList().also { it[i] = updated } }
                    launch { metricsRepo.record(updated) }
                }
            }
            jobs.awaitAll()
            _isRunning.value = false
        }
    }

    fun runSingle(index: Int) {
        viewModelScope.launch {
            val list = _checks.value
            if (index >= list.size) return@launch
            _checks.update { l ->
                l.toMutableList().also { it[index] = l[index].copy(status = CheckStatus.RUNNING) }
            }
            val updated = withContext(Dispatchers.IO) {
                CheckerFactory.get(list[index].config.type).check(list[index].config)
            }
            _checks.update { l -> l.toMutableList().also { it[index] = updated } }
            launch { metricsRepo.record(updated) }
        }
    }

    // ── Periodic scheduling ───────────────────────────────────────────────────
    private fun scheduleAllPeriodic() {
        _checks.value.forEachIndexed { i, r ->
            schedulePeriodicIfNeeded(i, r.config.interval)
        }
    }

    private fun schedulePeriodicIfNeeded(index: Int, intervalSec: Int) {
        if (intervalSec <= 0) return
        periodicJobs[index] = viewModelScope.launch {
            while (isActive) {
                delay(intervalSec * 1_000L)
                if (isActive) runSingle(index)
            }
        }
    }

    private fun cancelAllPeriodicJobs() {
        periodicJobs.values.forEach { it.cancel() }
        periodicJobs.clear()
    }

    // ── VM helpers (called from VmSettingsDialog) ─────────────────────────────
    fun saveVmSettings(url: String, username: String, password: String) {
        settings.vmUrl      = url
        settings.vmUsername = username
        settings.vmPassword = password
    }

    suspend fun testVmConnection(): Boolean = withContext(Dispatchers.IO) {
        if (!settings.isVmConfigured) return@withContext false
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val url = "${settings.vmUrl.trimEnd('/')}/api/v1/query?query=up"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    suspend fun flushMetrics(): Int = metricsRepo.flushBuffer()

    suspend fun pendingMetricCount(): Int = metricsRepo.pendingCount()

    suspend fun clearMetricBuffer() {
        val db = MetricsDatabase.getInstance(getApplication())
        db.dao().deleteOlderThan(Long.MAX_VALUE)   // deletes everything
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllPeriodicJobs()
    }
}
