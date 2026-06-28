package com.connectivity.checker.checker

import com.connectivity.checker.model.CheckType

object CheckerFactory {
    private val icmp = IcmpChecker()
    private val dns  = DnsChecker()
    private val http = HttpChecker()
    private val tcp  = TcpChecker()
    private val udp  = UdpChecker()
    private val tls  = TlsChecker()

    fun get(type: CheckType): NetworkChecker = when (type) {
        CheckType.ICMP -> icmp
        CheckType.DNS  -> dns
        CheckType.HTTP -> http
        CheckType.TCP  -> tcp
        CheckType.UDP  -> udp
        CheckType.TLS  -> tls
    }
}
