package com.connectivity.checker.checker

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class IcmpChecker : NetworkChecker {

    override suspend fun check(config: CheckConfig): CheckResult {
        val host = config.host
            ?: return CheckResult(config, CheckStatus.FAILURE, message = "Host not specified")
        val timeout = config.timeout
        val start = System.currentTimeMillis()

        return try {
            if (tryPingCommand(host, timeout)) {
                val latency = System.currentTimeMillis() - start
                CheckResult(config, CheckStatus.SUCCESS, latency, "Reachable (ICMP ping)", start)
            } else {
                // Fallback: InetAddress.isReachable (may use TCP echo on port 7)
                val addr = InetAddress.getByName(host)
                val reachable = addr.isReachable(timeout)
                val latency = System.currentTimeMillis() - start
                if (reachable) {
                    CheckResult(config, CheckStatus.SUCCESS, latency, "Reachable (TCP echo fallback)", start)
                } else {
                    CheckResult(config, CheckStatus.FAILURE, latency, "Host unreachable", start)
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            CheckResult(config, CheckStatus.FAILURE, latency, "Error: ${e.message}", start)
        }
    }

    private fun tryPingCommand(host: String, timeoutMs: Int): Boolean {
        return try {
            val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1)
            val process = Runtime.getRuntime().exec(
                arrayOf("/system/bin/ping", "-c", "1", "-W", timeoutSec.toString(), host)
            )
            val finished = process.waitFor(
                (timeoutMs + 2000).toLong(), TimeUnit.MILLISECONDS
            )
            if (!finished) {
                process.destroy()
                return false
            }
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
}
