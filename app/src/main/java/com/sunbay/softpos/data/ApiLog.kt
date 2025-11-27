package com.sunbay.softpos.data

data class ApiLog(
    val timestamp: String,
    val type: String, // "REQUEST" or "RESPONSE"
    val method: String,
    val url: String,
    val headers: String = "",
    val body: String = "",
    val statusCode: Int? = null,
    val duration: Long? = null
) {
    fun toDisplayString(): String {
        return buildString {
            appendLine("[$timestamp] $type")
            appendLine("Method: $method")
            appendLine("URL: $url")
            if (headers.isNotEmpty()) {
                appendLine("Headers: $headers")
            }
            if (body.isNotEmpty()) {
                appendLine("Body: $body")
            }
            if (statusCode != null) {
                appendLine("Status: $statusCode")
            }
            if (duration != null) {
                appendLine("Duration: ${duration}ms")
            }
            appendLine("---")
        }
    }
}
