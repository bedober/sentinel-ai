package ai.sentinel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SentinelScreen() }
    }
}

@Composable
fun SentinelScreen() {
    var target by remember { mutableStateOf("https://example.com") }
    var authorized by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Ready for an authorized passive scan.") }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("SENTINEL AI", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("Website security posture")
                OutlinedTextField(value = target, onValueChange = { target = it },
                    label = { Text("Website URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = authorized, onCheckedChange = { authorized = it })
                    Text("I own this site or have permission to assess it.")
                }
                Button(onClick = { message = "Connect this screen to the Sentinel API to start a scan." },
                    enabled = authorized && target.startsWith("http"),
                    modifier = Modifier.fillMaxWidth()) { Text("SCAN WEBSITE") }
                Text(message, color = MaterialTheme.colorScheme.secondary)
                Text("Passive checks only. No exploitation or destructive testing.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
