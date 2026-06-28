package com.connectivity.checker.checker

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.PortUnreachableException
import java.net.SocketTimeoutException

class UdpChecker : NetworkChecker {

    override suspend fun check(config: CheckConfig): CheckResult {
        val host = config.host
            ?: return CheckResult(config, CheckStatus.FAILURE, message = "Host not specified")
        val port = config.port
            ?: return CheckResult(config, CheckStatus.FAILURE, message = "Port not specified")
        val timeout = config.timeout
        val start = System.currentTimeMillis()

        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = timeout

            val addr = InetAddress.getByName(host)
            // connect() enables ICMP error propagation for this socket
            socket.connect(addr, port)

            val probe = byteArrayOf(0x00)
            socket.send(DatagramPacket(probe, probe.size))

            val buffer = ByteArray(1024)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            val latency = System.currentTimeMillis() - start
            CheckResult(config, CheckStatus.SUCCESS, latency,
                "Response received (${response.length} bytes)", start)
        } catch (e: PortUnreachableException) {
            val latency = System.currentTimeMillis() - start
            CheckResult(config, CheckStatus.FAILURE, latency,
                "Port unreachable (ICMP)", start)
        } catch (e: SocketTimeoutException) {
            val latency = System.currentTimeMillis() - start
            // Timeout on UDP is ambiguous: no response ≠ unreachable
            CheckResult(config, CheckStatus.FAILURE, latency,
                "Timeout — no UDP response (host may still be reachable)", start)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            CheckResult(config, CheckStatus.FAILURE, latency, "Error: ${e.message}", start)
        } finally {
            socket?.close()
        }
    }
}
