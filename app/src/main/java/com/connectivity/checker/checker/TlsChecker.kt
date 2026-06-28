package com.connectivity.checker.checker

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class TlsChecker : NetworkChecker {

    override suspend fun check(config: CheckConfig): CheckResult {
        val host = config.host
            ?: return CheckResult(config, CheckStatus.FAILURE, message = "Host not specified")
        val port = config.port ?: 443
        val sni = config.sni ?: host
        val timeout = config.timeout
        val start = System.currentTimeMillis()

        var tcpSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        return try {
            // 1. TCP connect
            tcpSocket = Socket()
            tcpSocket.connect(InetSocketAddress(host, port), timeout)
            tcpSocket.soTimeout = timeout

            // 2. Wrap with TLS, presenting custom SNI
            // getDefault() returns SocketFactory; cast to SSLSocketFactory first
            // so the createSocket(Socket, host, port, autoClose) overload is available
            sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(tcpSocket, sni, port, true) as SSLSocket

            val params = SSLParameters()
            params.serverNames = listOf(SNIHostName(sni))
            sslSocket.sslParameters = params
            sslSocket.useClientMode = true

            // 3. Handshake
            sslSocket.startHandshake()
            val session = sslSocket.session

            val latency = System.currentTimeMillis() - start

            // 4. Parse certificate
            val cert = session.peerCertificates.firstOrNull() as? X509Certificate
            val msg = if (cert != null) {
                val daysLeft = (cert.notAfter.time - System.currentTimeMillis()) / 86_400_000L
                val cn = extractCN(cert.subjectX500Principal.name)
                val hostVerified = HttpsURLConnection.getDefaultHostnameVerifier().verify(sni, session)
                buildString {
                    if (hostVerified) append("✓ valid") else append("✗ CN mismatch")
                    append(" CN=$cn")
                    append(" expires in ${daysLeft}d")
                    if (sni != host) append(" (SNI=$sni → $host:$port)")
                    if (daysLeft < 30) append(" ⚠ expiring soon")
                }
            } else {
                "Handshake OK (no cert info)"
            }

            val expired = cert != null && cert.notAfter.time < System.currentTimeMillis()
            val status = if (expired) CheckStatus.FAILURE else CheckStatus.SUCCESS
            CheckResult(config, status, latency, msg, start)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            CheckResult(config, CheckStatus.FAILURE, latency, "TLS error: ${e.message}", start)
        } finally {
            runCatching { sslSocket?.close() }
            runCatching { tcpSocket?.close() }
        }
    }

    private fun extractCN(dn: String): String =
        dn.split(",").firstOrNull { it.trimStart().startsWith("CN=") }
            ?.substringAfter("CN=")?.trim() ?: dn
}
