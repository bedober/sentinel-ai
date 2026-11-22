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
                        icon = { Text("⌂") }, label = { Text("Home") })
                    NavigationBarItem(selected = page == 1, onClick = { page = 1 },
                        icon = { Text(">_") }, label = { Text("Terminal") })
                    NavigationBarItem(selected = page == 2, onClick = { page = 2 },
                        icon = { Text("▦") }, label = { Text("Tools") })
                    NavigationBarItem(selected = page == 3, onClick = { page = 3 },
                        icon = { Text("⌕") }, label = { Text("Scanner") })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    0 -> WorkstationHome(
                        onOpenTerminal = { page = 1 },
                        onOpenTools = { page = 2 },
                        onOpenScanner = { page = 3 }
                    )
                    1 -> LinuxTerminalScreen()
                    2 -> ToolCatalogueScreen()
                    else -> SentinelScreen()
                }
            }
        }
    }
}

@Composable
fun WorkstationHome(onOpenTerminal: () -> Unit, onOpenTools: () -> Unit, onOpenScanner: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { LinuxRuntime(context) }
    val status = remember { runCatching { runtime.status() }.getOrNull() }
    val freeGb = String.format(java.util.Locale.US, "%.1f", context.filesDir.usableSpace / (1024.0 * 1024.0 * 1024.0))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("LINUX WORKSTATION", style = MaterialTheme.typography.titleLarge)
        Text("Your private Android security workspace", color = MaterialTheme.colorScheme.secondary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LINUX ENVIRONMENT", style = MaterialTheme.typography.titleMedium)
                Text(if (status?.ready == true) "Alpine Linux • PRoot detected" else "Setup required or runtime unavailable")
                Text("Private app storage • $freeGb GB available", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenTerminal, modifier = Modifier.fillMaxWidth()) {
                    Text(if (status?.ready == true) "OPEN LINUX TERMINAL" else "SET UP LINUX")
                }
            }
        }
        Text("WORKSPACES", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth(), onClick = onOpenTools) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Security tool centre", style = MaterialTheme.typography.titleMedium)
                    Text("${LinuxRuntime.packages.size} starter packages • install and manage tools", style = MaterialTheme.typography.bodySmall)
                }
                Text("OPEN →", color = MaterialTheme.colorScheme.primary)
            }
        }
        Card(Modifier.fillMaxWidth(), onClick = onOpenScanner) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Security scanner", style = MaterialTheme.typography.titleMedium)
                    Text("Run currently supported website checks", style = MaterialTheme.typography.bodySmall)
                }
                Text("OPEN →", color = MaterialTheme.colorScheme.primary)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PLATFORM STATUS", style = MaterialTheme.typography.titleMedium)
                Text("Device ABI: ${status?.architecture ?: "Not detected"}")
                Text("Alpine root filesystem: ${if (status?.rootfsInstalled == true) "Installed" else "Not installed"}")
                Text("PRoot runtime: ${if (status?.prootInstalled == true) "Detected" else "Not detected"}")
                Text("Terminal is currently pipe-backed; PTY, resize, reliable Ctrl-C and full-screen terminal apps remain unfinished.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Text("Sentinel provides a Linux userland, not a separate Linux kernel. Some tools need a remote Linux worker, a commercial licence or hardware Android phones do not expose.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun LinuxTerminalScreen() {
    val context = LocalContext.current
    val runtime = remember { LinuxRuntime(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(runtime.status()) }
    var message by remember { mutableStateOf("Install Sentinel's private Alpine Linux environment to begin.") }
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("Sentinel shell console\\n") }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<LinuxShellSession?>(null) }

    LaunchedEffect(session) {
        val current = session ?: return@LaunchedEffect
        while (current.isAlive()) {
            val line = current.readLine() ?: break
            output = (output + line + "\\n").takeLast(30000)
        }
        if (session === current) {
            output += "\\n[Shell session ended]\\n"
            session = null
        }
    }
    DisposableEffect(session) {
        onDispose { session?.stop() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("LINUX ENVIRONMENT", style = MaterialTheme.typography.titleLarge)
        Text("Alpine: ${if (status.rootfsInstalled) "installed" else "not installed"} • PRoot: ${if (status.prootInstalled) "installed" else "not installed"}")
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        if (!status.ready) {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        runtime.installEnvironment { message = it }
                        status = runtime.status()
                        message = "Linux startup self-test passed. Your environment is ready."
                    } catch (e: Exception) {
                        status = runtime.status()
                        message = e.message ?: "Linux environment installation failed."
                    } finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "SETTING UP LINUX…" else "INSTALL / REPAIR LINUX ENVIRONMENT")
            }
        } else if (session == null) {
            Button(onClick = {
                try {
                    session = runtime.startShell()
                    output += "\\n[Interactive shell started]\\n"
                } catch (e: Exception) {
                    message = e.message ?: "Could not start the shell."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("START SHELL SESSION") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Shell session running", modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { session?.stop(); session = null }) { Text("STOP") }
            }
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium) {
            Text(output, Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(command, { command = it }, label = { Text("Enter shell command") },
                singleLine = true, enabled = session != null, modifier = Modifier.weight(1f))
            Button(enabled = session != null && command.isNotBlank(), onClick = {
                val current = session ?: return@Button
                val value = command
                command = ""
                scope.launch {
                    try {
                        current.send(value)
                        output += "$ $value\\n"
                    } catch (e: Exception) {
                        output += "[Input error: ${e.message}]\\n"
                    }
                }
            }) { Text("SEND") }
        }
        Text("This build provides a persistent pipe-backed shell. Full PTY terminal emulation, resize and Ctrl-C support still require the native terminal bridge.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun ToolCatalogueScreen() {
    val context = LocalContext.current
    val runtime = remember { LinuxRuntime(context) }
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(runCatching { runtime.status().ready }.getOrDefault(false)) }
    var checking by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(setOf<String>()) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Packages install from signed Alpine repositories inside Sentinel's private Linux userland.") }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All") + LinuxRuntime.packages.map { it.category }.distinct().sorted()
    val visiblePackages = LinuxRuntime.packages.filter { item ->
        (selectedCategory == "All" || item.category == selectedCategory) &&
            (query.isBlank() || item.title.contains(query, true) ||
                item.description.contains(query, true) || item.apkPackage.contains(query, true))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("TOOL CENTRE", style = MaterialTheme.typography.titleLarge)
        Text("Search, filter, install and manage the Linux packages available in this Android build.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        OutlinedTextField(query, { query = it }, label = { Text("Search tools and packages") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                FilterChip(selected = selectedCategory == category,
                    onClick = { selectedCategory = category }, label = { Text(category) })
            }
        }
        Text(message, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary)
        if (ready) {
            OutlinedButton(enabled = !checking && busyId == null, onClick = {
                checking = true
                scope.launch {
                    try {
                        val found = mutableSetOf<String>()
                        visiblePackages.forEach { item ->
                            if (runtime.isPackageInstalled(item.apkPackage)) found += item.id
                        }
                        installed = found
                        message = "Package check completed."
                    } catch (e: Exception) {
                        message = "Package check failed: ${e.message ?: "Linux runtime unavailable"}"
                    } finally {
                        checking = false
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (checking) "CHECKING PACKAGES…" else "CHECK INSTALLED PACKAGES")
            }
        }
        if (!ready) {
            Spacer(Modifier.height(12.dp))
            Text("Install the Linux environment from the Terminal tab before installing packages.",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            Spacer(Modifier.height(10.dp))
            Button(enabled = busyId == null, onClick = {
                busyId = "all"
                scope.launch {
                    try {
                        val result = runtime.installAllPackages()
                        if (result.success) {
                            installed = LinuxRuntime.packages.map { it.id }.toSet()
                            message = "The complete supported starter toolkit is installed."
                        } else {
                            message = "Toolkit install failed (exit ${result.exitCode}): ${result.output.takeLast(700)}"
                        }
                    } catch (e: Exception) {
                        message = e.message ?: "Toolkit installation failed."
                    } finally { busyId = null }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (busyId == "all") "INSTALLING TOOLKIT…" else "INSTALL COMPLETE STARTER TOOLKIT")
            }
            Text("This downloads all packages in the current catalogue. Large development packages can use significant storage and mobile data.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinuxRuntime.packages.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.category + " • " + item.apkPackage,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            if (installed.contains(item.id)) {
                                Text("INSTALLED", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall)
                            } else {
                                Button(enabled = ready && busyId == null, onClick = {
                                    busyId = item.id
                                    scope.launch {
                                        try {
                                            val result = runtime.installPackage(item.apkPackage)
                                            if (result.success) {
                                                installed = installed + item.id
                                                message = "${item.title} installed successfully."
                                            } else {
                                                message = "Package install failed (exit ${result.exitCode}): ${result.output.takeLast(500)}"
                                            }
                                        } catch (e: Exception) {
                                            message = e.message ?: "Package installation failed."
                                        } finally { busyId = null }
                                    }
                                }) {
                                    Text(if (busyId == item.id) "INSTALLING…" else "INSTALL")
                                }
                            }
                        }
                        Text(item.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (visiblePackages.isEmpty()) Text("No matching packages. Try another search or category.", style = MaterialTheme.typography.bodySmall)
        Text("This is the starter package catalogue, not the full security suite. Some requested tools require a Linux worker, commercial licence or hardware not available to ordinary Android apps. Only assess systems you are authorized to test.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

