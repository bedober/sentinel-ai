package ai.sentinel.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Finding(val title: String, val severity: String, val evidence: String, val fix: String)

suspend fun runScan(target: String): Pair<Int, List<Finding>> = withContext(Dispatchers.IO) {
    val endpoint = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/scan"
    require(!endpoint.contains("YOUR-SENTINEL-API-HOST")) {
        "Set API_BASE_URL to your deployed HTTPS Sentinel API and rebuild the APK."
    }
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10000
        readTimeout = 25000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
    }
    try {
        val body = JSONObject().put("target", target).put("authorized", true).toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val json = JSONObject(stream.bufferedReader().use { it.readText() })
        if (code !in 200..299) throw IllegalStateException(json.optString("detail", "Scan failed ($code)"))
        val array = json.optJSONArray("findings")
        val findings = buildList {
            if (array != null) for (i in 0 until array.length()) {
                val f = array.getJSONObject(i)
                add(Finding(f.optString("title"), f.optString("severity"),
                    f.optString("evidence"), f.optString("recommendation")))
            }
        }
        json.optInt("score", 0) to findings
    } finally { connection.disconnect() }
}

@Composable
fun SentinelScreen() {
    var target by remember { mutableStateOf("https://example.com") }
    var authorized by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Only scan systems you own or are authorized to assess.") }
    var score by remember { mutableStateOf<Int?>(null) }
    var findings by remember { mutableStateOf(emptyList<Finding>()) }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp)) {
            Text("WEBSITE SECURITY", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("Passive checks only")
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(target, { target = it }, label = { Text("Website URL") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(authorized, { authorized = it })
                Text("I own this site or have permission to assess it.")
            }
            Button(enabled = authorized && !busy && target.startsWith("http"),
                onClick = {
                    busy = true; score = null; findings = emptyList()
                    scope.launch {
                        try {
                            val result = runScan(target.trim())
                            score = result.first; findings = result.second
                            message = "Scan completed."
                        } catch (e: Exception) {
                            message = e.message ?: "Unable to complete scan."
                        } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "SCANNING…" else "SCAN WEBSITE")
            }
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.secondary)
            score?.let { Text("Security configuration score: $it / 100",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(findings) { f ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(f.severity.uppercase(), color = MaterialTheme.colorScheme.primary)
                            Text(f.title, style = MaterialTheme.typography.titleMedium)
                            Text(f.evidence, style = MaterialTheme.typography.bodySmall)
                            Text("Fix: ${f.fix}")
                        }
                    }
                }
            }
        }
    }
}
