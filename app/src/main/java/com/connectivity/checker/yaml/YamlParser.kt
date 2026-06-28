package com.connectivity.checker.yaml

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckType
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object YamlParser {

    fun parse(content: String): List<CheckConfig> {
        val yaml = Yaml(SafeConstructor(LoaderOptions()))
        val data = yaml.load<Map<String, Any>>(content) ?: return emptyList()
        val list  = data["checks"] as? List<*> ?: return emptyList()
        return list.filterIsInstance<Map<String, Any>>().map { parseCheck(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCheck(m: Map<String, Any>): CheckConfig {
        val typeStr = m["type"]?.toString()?.uppercase()
            ?: throw IllegalArgumentException("Missing required field: 'type'")
        val type = try {
            CheckType.valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown type '$typeStr'. Supported: icmp, dns, http, tcp, udp, tls")
        }

        val headers: Map<String, String> = (m["headers"] as? Map<*, *>)
            ?.entries
            ?.filter { it.key != null && it.value != null }
            ?.associate { it.key.toString() to it.value.toString() }
            ?: emptyMap()

        return CheckConfig(
            name         = m["name"]?.toString() ?: "Unnamed",
            type         = type,
            host         = m["host"]?.toString(),
            port         = m["port"]?.toIntSafe(),
            url          = m["url"]?.toString(),
            method       = m["method"]?.toString()?.uppercase() ?: "GET",
            timeout      = m["timeout"]?.toIntSafe() ?: 5000,
            interval     = m["interval"]?.toIntSafe() ?: 0,
            expectedCode = m["expected_code"]?.toIntSafe(),
            dnsServer    = m["dns_server"]?.toString(),
            sni          = m["sni"]?.toString(),
            body         = m["body"]?.toString(),
            headers      = headers
        )
    }

    private fun Any.toIntSafe(): Int? = when (this) {
        is Int    -> this
        is Long   -> this.toInt()
        is String -> this.toIntOrNull()
        else      -> null
    }
}
