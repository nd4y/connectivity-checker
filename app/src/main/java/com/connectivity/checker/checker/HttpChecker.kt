package com.connectivity.checker.checker

import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HttpChecker : NetworkChecker {

    private val baseClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun check(config: CheckConfig): CheckResult {
        val url = config.url
            ?: return CheckResult(config, CheckStatus.FAILURE, message = "URL not specified")
        val method = config.method.uppercase()
        val timeout = config.timeout.toLong()
        val start = System.currentTimeMillis()

        val client = baseClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .build()

        val requestBuilder = Request.Builder().url(url)
        config.headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        when (method) {
            "GET"  -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            "POST" -> {
                val contentType = config.headers["Content-Type"] ?: "text/plain"
                val body = (config.body ?: "").toRequestBody(contentType.toMediaTypeOrNull())
                requestBuilder.post(body)
            }
            else   -> requestBuilder.get()
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val code = response.code
                val expected = config.expectedCode
                if (expected != null && code != expected) {
                    CheckResult(config, CheckStatus.FAILURE, latency,
                        "HTTP $code (expected $expected)", start)
                } else {
                    CheckResult(config, CheckStatus.SUCCESS, latency,
                        "HTTP $code ${response.message}", start)
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            CheckResult(config, CheckStatus.FAILURE, latency, "Error: ${e.message}", start)
        }
    }
}
