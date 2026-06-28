package com.connectivity.checker.checker

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import java.net.InetSocketAddress
import java.net.Socket

class TcpChecker : NetworkChecker {

    override suspend fun check(config: CheckConfig): CheckResult {
        val host = config.host
            ?: return CheckResult(config, CheckStatus.FAILURE, message = "Host not specified")
        val port = config.port
            ?: return CheckResult(config, CheckStatus.FAILURE, message = "Port not specified")
        val timeout = config.timeout
        val start = System.currentTimeMillis()

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                val latency = System.currentTimeMillis() - start
                CheckResult(config, CheckStatus.SUCCESS, latency,
                    "Connected to $host:$port", start)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            CheckResult(config, CheckStatus.FAILURE, latency, "Failed: ${e.message}", start)
        }
    }
}
