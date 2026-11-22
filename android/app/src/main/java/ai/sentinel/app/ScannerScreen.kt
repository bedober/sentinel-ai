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
import kotlinx.coroutines.launch

data class Finding(val title: String, val severity: String, val evidence: String, val fix: String)

@Composable
fun SentinelScreen() {
    var target by remember { mutableStateOf("https://example.com") }
    var authorized by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Only assess systems you own or have permission to test. Checks run on this phone.") }
    var score by remember { mutableStateOf<Int?>(null) }
    var findings by remember { mutableStateOf(emptyList<Finding>()) }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp)) {
            Text("WEBSITE SECURITY", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("On-device passive HTTPS assessment")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(target, { target = it }, label = { Text("HTTPS website URL") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(authorized, { authorized = it })
                Text("I own this site or have permission to assess it.")
            }
            Button(enabled = authorized && !busy && target.isNotBlank(),
                onClick = {
                    busy = true
                    score = null
                    findings = emptyList()
                    scope.launch {
                        try {
                            val result = LocalScanner.scan(target.trim())
                            score = result.first
                            findings = result.second
                            message = "Assessment completed on this device. No exploit or brute-force tests were run."
                        } catch (e: Exception) {
                            message = e.message ?: "Unable to complete the assessment."
                        } finally {
                            busy = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "ASSESSING…" else "ASSESS WEBSITE")
            }
            Spacer(Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall)
            score?.let {
                Text("Configuration score: $it / 100",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(findings) { finding ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(finding.severity.uppercase(),
                                color = when (finding.severity.lowercase()) {
                                    "high" -> MaterialTheme.colorScheme.error
                                    "medium" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                })
                            Text(finding.title, style = MaterialTheme.typography.titleMedium)
                            Text(finding.evidence, style = MaterialTheme.typography.bodySmall)
                            Text("Recommendation: ${finding.fix}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
