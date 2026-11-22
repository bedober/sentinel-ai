package ai.sentinel.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
                    NavigationBarItem(selected = page == 4, onClick = { page = 4 },
                        icon = { Text("▶") }, label = { Text("Run tools") })
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
                    3 -> SentinelScreen()
                    else -> GraphicalToolRunnerScreen()
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
        Card(onClick = onOpenTools, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Security tool centre", style = MaterialTheme.typography.titleMedium)
                    Text("${LinuxRuntime.packages.size} starter packages • install and manage tools", style = MaterialTheme.typography.bodySmall)
                }
                Text("OPEN →", color = MaterialTheme.colorScheme.primary)
            }
        }
        Card(onClick = onOpenScanner, modifier = Modifier.fillMaxWidth()) {
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
    var output by remember { mutableStateOf("Sentinel shell console\n") }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<LinuxShellSession?>(null) }

    LaunchedEffect(session) {
        val current = session ?: return@LaunchedEffect
        while (current.isAlive()) {
            val line = current.readLine() ?: break
            output = (output + line + "\n").takeLast(30000)
        }
        if (session === current) {
            output += "\n[Shell session ended]\n"
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
                    output += "\n[Interactive shell started]\n"
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
                        output += "$ $value\n"
                    } catch (e: Exception) {
                        output += "[Input error: ${e.message}]\n"
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



private data class GuiToolSpec(
 val id:String,val name:String,val category:String,val summary:String,
 val targetLabel:String,val targetHint:String,val optionLabel:String,val optionHint:String,
 val availability:String,val mode:String
)
private val sentinelGuiTools = listOf(
 GuiToolSpec("nmap","Nmap","Network scanning","Discover open TCP ports and identify services on an authorized host.","Target IP or hostname","192.168.1.10","Ports","1-1000 or 22,80,443","Local Android/PRoot adapter","local"),
 GuiToolSpec("masscan","Masscan","Network scanning","High-speed port discovery with strict rate and target limits.","Target IP or CIDR","192.168.1.0/24","Ports and rate","80,443 • rate capped by policy","Requires compatible raw-socket environment or remote Linux worker","worker"),
 GuiToolSpec("amass","Amass","Reconnaissance","Map domains and DNS assets using passive collection sources.","Root domain","example.com","Enumeration profile","Passive only","Local package/adapter integration pending","pending"),
 GuiToolSpec("gobuster","Gobuster","Web testing","Discover approved web paths and virtual hosts with bounded request rates.","Base URL","https://example.com","Wordlist / mode","Choose a bundled wordlist","Local adapter integration pending; scoped requests only","pending"),
 GuiToolSpec("ffuf","ffuf","Web testing","Run controlled web content discovery against an approved endpoint.","Target URL","https://example.com/FUZZ","Wordlist","Choose a bundled wordlist","Local adapter integration pending; request limits required","pending"),
 GuiToolSpec("nessus","Nessus","Vulnerability assessment","Connect to a separately licensed Nessus scanner and view assessment results.","Scanner URL","https://scanner.example.com:8834","Target / policy","Select configured scan policy","External licensed scanner required","licensed"),
 GuiToolSpec("openvas","OpenVAS / Greenbone","Vulnerability assessment","Manage authenticated vulnerability scans through a Greenbone server.","Greenbone server URL","https://greenbone.example.com","Target / scan config","Choose configured scan profile","Remote Greenbone server required","worker"),
 GuiToolSpec("nikto","Nikto","Web testing","Check web-server configuration and known exposure indicators.","Website URL","https://example.com","Tuning profile","Safe checks only","Local adapter integration pending","pending"),
 GuiToolSpec("burp","Burp Suite","Web testing","Launch or connect to Burp Suite for interactive proxy testing.","Burp host / URL","http://127.0.0.1:8080","Project","Choose configured project","Separate Burp installation and licence may be required","licensed"),
 GuiToolSpec("zap","OWASP ZAP","Web testing","Review passive alerts and baseline web security findings.","Target URL","https://example.com","Scan profile","Baseline / passive analysis","Desktop or remote ZAP daemon integration pending","worker"),
 GuiToolSpec("sqlmap","sqlmap","Web testing","Prepare a scoped SQL injection verification assessment for an approved test URL.","Authorized test URL","https://test.example/item?id=1","Parameter","id","Execution adapter pending; manual approval and safe test scope required","pending"),
 GuiToolSpec("metasploit","Metasploit Framework","Validation","Manage approved exploit-validation modules and evidence in a controlled lab.","Lab target","10.10.10.10","Module / session","Choose approved validation module","Remote Linux worker recommended; exploit execution gated","worker"),
 GuiToolSpec("bloodhound","BloodHound","Identity assessment","Analyze Active Directory relationship data and privilege paths.","BloodHound server","https://bloodhound.example.com","Dataset","Select imported collection","Separate BloodHound server and collector workflow required","worker"),
 GuiToolSpec("impacket","Impacket","Identity assessment","Choose supported Windows protocol inventory and analysis workflows.","Authorized host","192.168.1.20","Protocol / workflow","SMB inventory","Local package integration pending; authentication workflows restricted","pending"),
 GuiToolSpec("netexec","NetExec","Identity assessment","Review authorized network service inventory and configuration.","Authorized host / range","192.168.1.0/24","Service","SMB / SSH / LDAP","Remote Linux worker recommended; credential and lateral movement actions disabled","worker"),
 GuiToolSpec("peas","LinPEAS / WinPEAS","Host auditing","Audit local or imported host configuration for privilege-risk indicators.","Host / evidence source","Select local audit or uploaded output","Operating system","Linux / Windows","Run on a controlled host or analyze collected evidence","pending"),
 GuiToolSpec("hashcat","Hashcat","Password auditing","Audit password hashes from files you are authorized to assess.","Hash file","Select an authorized hash file","Audit profile","Choose hash format and bounded profile","Compatible compute hardware and runtime required","hardware"),
 GuiToolSpec("john","John the Ripper","Password auditing","Run an offline password-strength audit against an authorized hash file.","Hash file","Select an authorized hash file","Audit mode","Select format and bounded mode","Local package/runtime integration pending","pending"),
 GuiToolSpec("hydra","Hydra","Authentication testing","Prepare an approved authentication resilience test with strict attempt limits.","Authorized service","ssh://192.168.1.20","Test policy","Low-rate, account-safe policy","Execution disabled pending safe adapter and explicit engagement scope","restricted"),
 GuiToolSpec("aircrack","Aircrack-ng","Wireless auditing","Review authorized wireless capture files and supported adapter status.","Capture file / interface","Select capture file or compatible adapter","Audit workflow","Offline capture analysis","Compatible Wi-Fi adapter and driver support required","hardware"),
 GuiToolSpec("beef","BeEF","Browser security","Manage a controlled browser-security lab and review browser exposure.","BeEF server URL","https://lab.example.com","Lab profile","Isolated test browser only","Separate server and isolated lab required; hooking features disabled","worker")
)

@Composable
fun GraphicalToolRunnerScreen() {
 val context=LocalContext.current
 val runtime=remember { LinuxRuntime(context) }
 val scope=rememberCoroutineScope()
 var selectedId by remember { mutableStateOf("nmap") }
 var target by remember { mutableStateOf("") }
 var options by remember { mutableStateOf("1-1000") }
 var consent by remember { mutableStateOf(false) }
 var busy by remember { mutableStateOf(false) }
 var output by remember { mutableStateOf("Select a tool, complete its form, and run an authorized assessment.") }
 var status by remember { mutableStateOf("Ready") }
 var nmapInstalled by remember { mutableStateOf(false) }
 var serverUrl by remember { mutableStateOf("") }
 var serverToken by remember { mutableStateOf("") }
 val selected=sentinelGuiTools.first { it.id==selectedId }
 LaunchedEffect(Unit) {
  nmapInstalled=runCatching { runtime.status().ready && runtime.isPackageInstalled("nmap") }.getOrDefault(false)
 }
 LaunchedEffect(selectedId) {
  target=""
  options=if(selectedId=="nmap") "1-1000" else ""
  consent=false
  status="Ready"
  output="Complete this tool's form. Execution availability is shown below."
 }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
  verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("GRAPHICAL SECURITY TOOLS",style=MaterialTheme.typography.titleLarge)
  Text("All 21 requested tools have dedicated GUI entries. Select a tool, fill in its form and review its availability.",
   style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
  sentinelGuiTools.forEach { tool ->
   Card(onClick={selectedId=tool.id},modifier=Modifier.fillMaxWidth(),
    colors=CardDefaults.cardColors(containerColor=if(selectedId==tool.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) {
      Text(tool.name,style=MaterialTheme.typography.titleMedium)
      Text(tool.category+" • "+tool.availability,style=MaterialTheme.typography.labelSmall)
     }
     if(selectedId==tool.id) Text("SELECTED",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)
    }
   }
  }
  Card(Modifier.fillMaxWidth()) {
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Text(selected.name.uppercase(),style=MaterialTheme.typography.titleLarge)
    Text(selected.summary,style=MaterialTheme.typography.bodySmall)
    OutlinedTextField(value=target,onValueChange={target=it},label={Text(selected.targetLabel)},
     placeholder={Text(selected.targetHint)},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(value=options,onValueChange={options=it},label={Text(selected.optionLabel)},
     placeholder={Text(selected.optionHint)},modifier=Modifier.fillMaxWidth())
    if(selected.id=="nmap") {
     Text("REMOTE LINUX SERVER",style=MaterialTheme.typography.titleSmall)
     OutlinedTextField(value=serverUrl,onValueChange={serverUrl=it},
      label={Text("HTTPS server URL")},placeholder={Text("https://security.example.com")},
      modifier=Modifier.fillMaxWidth(),singleLine=true)
     OutlinedTextField(value=serverToken,onValueChange={serverToken=it},
      label={Text("Server access token")},visualTransformation=PasswordVisualTransformation(),
      modifier=Modifier.fillMaxWidth(),singleLine=true)
    }
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     Checkbox(checked=consent,onCheckedChange={consent=it})
     Text("I own this target or have explicit permission to assess it.",style=MaterialTheme.typography.bodySmall)
    }
    Text("Availability: "+selected.availability,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
    val runnable=selected.id=="nmap"
    OutlinedButton(enabled=runnable && !busy && consent && target.isNotBlank() &&
     options.isNotBlank() && serverUrl.startsWith("https://") && serverToken.isNotBlank(),
     onClick={
      val host=target.trim()
      val ports=options.trim()
      busy=true; status="Connecting to Linux server…"; output=""
      scope.launch {
       try {
        output=LinuxWorkerApi.runNmap(serverUrl,serverToken,host,ports)
        status="Linux server scan completed"
       } catch(e:Exception) {
        status="Linux server scan failed"
        output=e.message ?: "Could not connect to the Linux server."
       } finally { busy=false }
      }
     },modifier=Modifier.fillMaxWidth()) {
      Text(if(busy) "WAITING FOR LINUX SERVER…" else "RUN ON LINUX SERVER")
    }
    Button(enabled=runnable && !busy && consent && target.isNotBlank() && options.isNotBlank(),
     onClick={
      val host=target.trim()
      val ports=options.trim()
      val validHost=host.length<=253 && host.matches(Regex("(?i)[a-z0-9][a-z0-9.-]*[a-z0-9]|[a-z0-9]")) && !host.contains("..")
      val validPorts=ports.matches(Regex("[0-9]{1,5}(-[0-9]{1,5})?(,[0-9]{1,5}(-[0-9]{1,5})?)*")) &&
       ports.split(",").all { part ->
        val bounds=part.split("-").mapNotNull { it.toIntOrNull() }
        bounds.size in 1..2 && bounds.all { it in 1..65535 } && (bounds.size==1 || bounds[0]<=bounds[1])
       }
      if(!validHost || !validPorts) { status="Check the target and port range."; return@Button }
      busy=true; status="Preparing Nmap…"; output=""
      scope.launch {
       try {
        require(runtime.status().ready) { "Install the Linux environment first from the Terminal tab." }
        if(!runtime.isPackageInstalled("nmap")) {
         status="Installing Nmap from the Alpine repository…"
         val install=runtime.installPackage("nmap")
         require(install.success) { "Nmap installation failed: "+install.output.takeLast(500) }
        }
        nmapInstalled=true; status="Running authorized TCP connect scan…"
        val result=runtime.runCommand(listOf("nmap","-sT","-sV","--reason","-p",ports,host),300)
        output=result.output.ifBlank { "Nmap returned no output." }
        status=if(result.success) "Scan completed" else "Scan exited with code "+result.exitCode
       } catch(e:Exception) { status="Could not run scan"; output=e.message ?: "Unknown error" }
       finally { busy=false }
      }
     },modifier=Modifier.fillMaxWidth()) {
      Text(if(busy) "RUNNING…" else if(runnable) "RUN NMAP" else "RUNNER NOT CONNECTED YET")
     }
    if(!runnable) Text("This tool is included in the 21-tool GUI catalogue, but its execution adapter is not yet connected. It will not be marked installed or functional until verified.",
     style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    else Text("Nmap status: "+if(nmapInstalled) "installed" else "will install on first run",style=MaterialTheme.typography.labelSmall)
   }
  }
  Card(Modifier.fillMaxWidth()) {
   Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     Text("RESULTS",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
     Text(status,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
    }
    Surface(Modifier.fillMaxWidth().heightIn(min=160.dp,max=440.dp),color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium) {
     Text(output,Modifier.padding(12.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
    }
   }
  }
  Text("Local adapters, remote workers, vendor licences and compatible hardware are separate requirements. Only assess systems within written authorization and scope.",
   style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
 }
}
