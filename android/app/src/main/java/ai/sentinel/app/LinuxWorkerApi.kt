package ai.sentinel.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LinuxWorkerApi {
    suspend fun runNmap(
        serverUrl: String,
        accessToken: String,
        target: String,
        ports: String
    ): String = withContext(Dispatchers.IO) {
        require(serverUrl.startsWith("https://")) {
            "Use an HTTPS Linux server URL. Plain HTTP is disabled for security."
        }
        require(accessToken.isNotBlank()) { "Enter the Linux server access token." }
        val base = serverUrl.trim().trimEnd('/')
        val connection = (URL(base + "/api/worker/nmap").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 195000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer " + accessToken)
        }
        try {
            val body = JSONObject()
                .put("target", target)
                .put("ports", ports)
                .put("authorized", true)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("detail") }.getOrNull()
                error(detail?.takeIf { it.isNotBlank() } ?: "Linux server returned HTTP " + code + ".")
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}
