package com.connectivity.checker.model

enum class CheckStatus { PENDING, RUNNING, SUCCESS, FAILURE }

data class CheckResult(
    val config: CheckConfig,
    val status: CheckStatus = CheckStatus.PENDING,
    val latencyMs: Long = -1L,
    val message: String = "",
    val lastChecked: Long = 0L
)
