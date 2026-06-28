package com.connectivity.checker.yaml

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckType

object YamlExporter {

    fun export(configs: List<CheckConfig>): String = buildString {
        appendLine("checks:")
        configs.forEach { c ->
            appendLine()
            appendLine("  - name: ${quoteYaml(c.name)}")
            appendLine("    type: ${c.type.name.lowercase()}")
            c.host?.let { appendLine("    host: $it") }
            c.port?.let { appendLine("    port: $it") }
            c.url?.let  { appendLine("    url: $it") }

            if (c.type == CheckType.HTTP) {
                appendLine("    method: ${c.method}")
                c.expectedCode?.let { appendLine("    expected_code: $it") }
                c.body?.let { appendLine("    body: ${quoteYaml(it)}") }
                if (c.headers.isNotEmpty()) {
                    appendLine("    headers:")
                    c.headers.forEach { (k, v) -> appendLine("      $k: $v") }
                }
            }

            if (c.type == CheckType.DNS) {
                c.dnsServer?.let { appendLine("    dns_server: $it") }
            }

            if (c.type == CheckType.TLS) {
                c.sni?.let { appendLine("    sni: $it") }
            }

            appendLine("    timeout: ${c.timeout}")
            if (c.interval > 0) appendLine("    interval: ${c.interval}")
        }
    }

    private fun quoteYaml(s: String): String {
        val needsQuote = s.contains('"') || s.contains(':') || s.contains('#') ||
                s.contains('\n') || s.startsWith(' ') || s.endsWith(' ')
        return if (needsQuote) "'${s.replace("'", "''")}'" else "\"$s\""
    }
}
