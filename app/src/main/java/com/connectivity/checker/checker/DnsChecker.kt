package com.connectivity.checker.checker

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsChecker : NetworkChecker {

    override suspend fun check(config: CheckConfig): CheckResult {
        val host = config.host
            ?: return CheckResult(config, CheckStatus.FAILURE, message = "Host not specified")
        val timeout = config.timeout
        val start = System.currentTimeMillis()

        return try {
            if (config.dnsServer != null) {
                queryCustomDns(config, host, config.dnsServer, timeout, start)
            } else {
                querySystemDns(config, host, start)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            CheckResult(config, CheckStatus.FAILURE, latency, "Error: ${e.message}", start)
        }
    }

    private fun querySystemDns(config: CheckConfig, host: String, start: Long): CheckResult {
        val addresses = InetAddress.getAllByName(host)
        val latency = System.currentTimeMillis() - start
        val ipList = addresses.mapNotNull { it.hostAddress }.joinToString(", ")
        return CheckResult(config, CheckStatus.SUCCESS, latency, "Resolved: $ipList", start)
    }

    private fun queryCustomDns(
        config: CheckConfig,
        host: String,
        dnsServer: String,
        timeout: Int,
        start: Long
    ): CheckResult {
        val query = buildDnsQuery(host)
        val socket = DatagramSocket()
        return try {
            socket.soTimeout = timeout
            val serverAddr = InetAddress.getByName(dnsServer)
            socket.send(DatagramPacket(query, query.size, serverAddr, 53))

            val buffer = ByteArray(512)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            val latency = System.currentTimeMillis() - start
            val resolved = parseDnsResponse(buffer, response.length)
            CheckResult(config, CheckStatus.SUCCESS, latency, "via $dnsServer: $resolved", start)
        } finally {
            socket.close()
        }
    }

    private fun buildDnsQuery(hostname: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(0x1234)  // transaction ID
        dos.writeShort(0x0100)  // flags: standard query + recursion desired
        dos.writeShort(1)       // questions
        dos.writeShort(0)       // answers
        dos.writeShort(0)       // authority RRs
        dos.writeShort(0)       // additional RRs
        for (label in hostname.split(".")) {
            dos.writeByte(label.length)
            dos.write(label.toByteArray(Charsets.US_ASCII))
        }
        dos.writeByte(0)        // root label
        dos.writeShort(1)       // type A
        dos.writeShort(1)       // class IN
        return baos.toByteArray()
    }

    private fun parseDnsResponse(buf: ByteArray, len: Int): String {
        if (len < 12) return "Invalid response"
        val rcode = buf[3].toInt() and 0x0F
        if (rcode != 0) return "DNS error code $rcode"

        val answerCount = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
        if (answerCount == 0) return "No answers"

        // skip header (12 bytes) + question section
        var pos = 12
        while (pos < len && buf[pos] != 0.toByte()) {
            pos += (buf[pos].toInt() and 0xFF) + 1
        }
        pos += 5  // null label byte + qtype (2) + qclass (2)

        val ips = mutableListOf<String>()
        repeat(answerCount) {
            if (pos >= len) return@repeat
            // skip name (handle pointer compression)
            if ((buf[pos].toInt() and 0xC0) == 0xC0) {
                pos += 2
            } else {
                while (pos < len && buf[pos] != 0.toByte()) pos++
                pos++
            }
            if (pos + 10 > len) return@repeat
            val type = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            pos += 8  // type(2) + class(2) + ttl(4)
            val dataLen = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            pos += 2
            if (type == 1 && dataLen == 4 && pos + 4 <= len) {
                ips.add("${buf[pos].toInt() and 0xFF}.${buf[pos+1].toInt() and 0xFF}" +
                        ".${buf[pos+2].toInt() and 0xFF}.${buf[pos+3].toInt() and 0xFF}")
            }
            pos += dataLen
        }

        return if (ips.isEmpty()) "Resolved (no A records)" else ips.joinToString(", ")
    }
}
