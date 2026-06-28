package com.connectivity.checker.checker

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckResult

interface NetworkChecker {
    suspend fun check(config: CheckConfig): CheckResult
}
