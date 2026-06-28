package com.connectivity.checker.model

enum class CheckType { ICMP, DNS, HTTP, TCP, UDP, TLS }

data class CheckConfig(
    val name: String,
    val type: CheckType,
    val host: String? = null,
    val port: Int? = null,
    val url: String? = null,
    val method: String = "GET",
    val timeout: Int = 5000,
    val interval: Int = 0,           // seconds; 0 = manual only
    val expectedCode: Int? = null,
    val dnsServer: String? = null,
    val sni: String? = null,         // TLS: custom SNI (defaults to host)
    val body: String? = null,
    val headers: Map<String, String> = emptyMap()
)
