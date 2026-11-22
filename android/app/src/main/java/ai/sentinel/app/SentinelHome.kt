package ai.sentinel.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun SentinelHome() {
    var page by remember { mutableStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("SENTINEL AI", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Security workspace", style = MaterialTheme.typography.bodySmall)
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = page == 0, onClick = { page = 0 },
                        icon = { Text("⌕") }, label = { Text("Scanner") })
                    NavigationBarItem(selected = page == 1, onClick = { page = 1 },
                        icon = { Text(">_") }, label = { Text("Terminal") })
                    NavigationBarItem(selected = page == 2, onClick = { page = 2 },
                        icon = { Text("▦") }, label = { Text("Tools") })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    0 -> SentinelScreen()
                    1 -> LinuxTerminalScreen()
                    else -> ToolCatalogueScreen()
                }
            }
        }
    }
}

@Composable
fun LinuxTerminalScreen() {
    val context = LocalContext.current
    val runtime = remember { LinuxRuntime(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(runtime.status()) }
    var message by remember { mutableStateOf("Install the private Linux environment to begin.") }
    var command by remember { mutableStateOf("uname -a") }
    var output by remember { mutableStateOf("Sentinel terminal\n") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("LINUX ENVIRONMENT", style = MaterialTheme.typography.titleLarge)
        Text("Alpine Linux ${if (status.rootfsInstalled) "installed" else "not installed"} • PRoot ${if (status.prootInstalled) "installed" else "not installed"}")
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Button(enabled = !busy && !status.rootfsInstalled, onClick = {
            busy = true
            scope.launch {
                try {
                    runtime.installRootfs { message = it }
                    status = runtime.status()
                    message = if (status.prootInstalled) "Linux environment ready." else
                        "Root filesystem installed and verified. Android-compatible PRoot runtime is still required before commands can run."
                } catch (e: Exception) {
                    message = e.message ?: "Linux installation failed."
                } finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "INSTALLING…" else if (status.rootfsInstalled) "LINUX ROOTFS INSTALLED" else "INSTALL LINUX ENVIRONMENT")
        }
        OutlinedTextField(command, { command = it }, label = { Text("Command") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && status.ready && command.isNotBlank(), onClick = {
            busy = true
            val cmd = command
            scope.launch {
                try {
                    val result = runLinuxCommand(runtime, cmd)
                    output += "\n$ $cmd\n$result\n"
                } catch (e: Exception) {
                    output += "\n$ $cmd\nERROR: ${e.message}\n"
                } finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "RUNNING…" else "RUN COMMAND") }
        Surface(Modifier.weight(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium) {
            Text(output, Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        Text("Commands are disabled until the matching Android PRoot binary is installed. No root access is requested.",
            style = MaterialTheme.typography.labelSmall)
    }
}

private suspend fun runLinuxCommand(runtime: LinuxRuntime, command: String): String =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val status = runtime.status()
        require(status.ready) { "Linux runtime is not ready." }
        val proot = File(runtime.status().rootfsPath).parentFile?.resolve("bin/proot")
            ?: error("PRoot binary path is missing.")
        val process = ProcessBuilder(
            proot.absolutePath, "-0", "-r", status.rootfsPath, "-w", "/root",
            "/bin/sh", "-lc", command
        ).redirectErrorStream(true).apply {
            environment()["HOME"] = "/root"
            environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            environment()["LANG"] = "C.UTF-8"
        }.start()
        val text = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (text.length < 64 * 1024) synchronized(text) { text.append(line).append('\n') }
                }
            }
        }
        reader.start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            reader.join(1000)
            error("Command timed out after 30 seconds.")
        }
        reader.join(1000)
        synchronized(text) { text.toString() }
    }

@Composable
fun ToolCatalogueScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("TOOL CATALOGUE", style = MaterialTheme.typography.titleLarge)
        Text("Packages available from Alpine repositories. Installation is not enabled until the PRoot runtime is provisioned and verified.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinuxRuntime.packages.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.category + " • " + item.apkPackage, style = MaterialTheme.typography.labelSmall)
                        Text(item.description, style = MaterialTheme.typography.bodySmall)
                        Text(item.status, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
