package ai.sentinel.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RuntimeStatus(
    val rootfsInstalled: Boolean,
    val prootInstalled: Boolean,
    val architecture: String?,
    val rootfsPath: String
) {
    val ready: Boolean get() = rootfsInstalled && prootInstalled
}

data class ToolPackage(
    val id: String,
    val title: String,
    val apkPackage: String,
    val category: String,
    val description: String,
    val status: String = "Available from Alpine repositories; compatibility depends on device/kernel"
)

data class CommandResult(val exitCode: Int, val output: String) {
    val success: Boolean get() = exitCode == 0
}

/**
 * App-private Alpine userland and bundled Android PRoot runtime.
 * The PRoot binary and its companion loader files are produced by the pinned
 * CI build from the separately licensed upstream PRoot project.
 */
class LinuxRuntime(private val context: Context) {
    private val root = File(context.filesDir, "linux")
    private val rootfs = File(root, "rootfs")
    private val prootRoot = File(root, "proot/root")
    private val proot = File(prootRoot, "bin/proot")
    private val version = "3.24.4"

    fun status(): RuntimeStatus = RuntimeStatus(
        rootfsInstalled = File(rootfs, ".sentinel-rootfs-version").takeIf { it.isFile }
            ?.readText()?.trim() == version,
        prootInstalled = proot.isFile && proot.canExecute() &&
            File(prootRoot, "libexec/proot").listFiles()?.isNotEmpty() == true,
        architecture = alpineArch(),
        rootfsPath = rootfs.absolutePath
    )

    private fun alpineArch(): String? =
        if (Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) "aarch64" else null

    suspend fun installEnvironment(progress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        installProot(progress)
        installRootfs(progress)
        val check = status()
        require(check.ready) { "Linux environment self-check failed. PRoot or Alpine is missing." }
        progress("Linux userland installed. Starting the shell self-test…")
        val test = runCommand(listOf("/bin/sh", "-lc", "echo SENTINEL_LINUX_OK && /sbin/apk --version"), 20)
        require(test.success && test.output.contains("SENTINEL_LINUX_OK")) {
            "Linux self-test failed: ${test.output.take(500)}"
        }
        progress("Linux environment passed its startup self-test.")
    }

    private fun installProot(progress: (String) -> Unit) {
        if (status().prootInstalled) return
        progress("Preparing the bundled Android PRoot runtime…")
        val assetRoot = "proot/root"
        val entries = context.assets.list(assetRoot) ?: emptyArray()
        require(entries.isNotEmpty()) {
            "This APK does not contain the Android PRoot runtime. Build the app with the PRoot CI step enabled."
        }
        val staging = File(root, "proot-staging/root")
        staging.parentFile?.deleteRecursively()
        staging.mkdirs()
        copyAssetsRecursively(assetRoot, staging)
        val stagedProot = File(staging, "bin/proot")
        require(stagedProot.isFile && stagedProot.setExecutable(true, false)) {
            "Bundled PRoot executable is missing or could not be marked executable."
        }
        val loaderDir = File(staging, "libexec/proot")
        require(loaderDir.isDirectory && (loaderDir.listFiles()?.isNotEmpty() == true)) {
            "Bundled PRoot loader files are missing."
        }
        val finalRoot = File(root, "proot/root")
        val backup = File(root, "proot/previous")
        finalRoot.parentFile?.mkdirs()
        backup.deleteRecursively()
        if (finalRoot.exists() && !finalRoot.renameTo(backup)) {
            error("Could not stage the previous PRoot runtime.")
        }
        if (!staging.renameTo(finalRoot)) {
            if (backup.exists()) backup.renameTo(finalRoot)
            error("Could not activate the PRoot runtime.")
        }
        backup.deleteRecursively()
        progress("Android PRoot runtime installed.")
    }

    private fun copyAssetsRecursively(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetsRecursively("$assetPath/$child", File(destination, child))
        }
    }

    private fun download(url: URL, destination: File) {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SentinelAI-Android")
        }
        try {
            require(connection.responseCode in 200..299) { "Download failed: HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 512L * 1024 * 1024) { "Rootfs download exceeded the 512 MiB safety limit." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun installRootfs(progress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        if (status().rootfsInstalled) {
            progress("Verified Alpine Linux root filesystem is already installed.")
            return@withContext
        }
        val arch = alpineArch() ?: error("This device ABI is not supported yet. Supported in this APK: arm64-v8a.")
        val name = "alpine-minirootfs-$version-$arch.tar.gz"
        val base = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/$arch/"
        val archive = File(root, "$name.part")
        val checksum = File(root, "$name.sha256")
        root.mkdirs()
        try {
            progress("Downloading Alpine Linux $version ($arch)…")
            download(URL(base + name), archive)
            progress("Downloading and checking SHA-256…")
            download(URL(base + "$name.sha256"), checksum)
            val expected = checksum.readText().trim().split(Regex("\\s+")).firstOrNull()?.lowercase(Locale.ROOT)
                ?: error("Alpine checksum file is invalid.")
            val actual = sha256(archive)
            require(expected.matches(Regex("[a-f0-9]{64}")) && actual == expected) {
                "Alpine rootfs checksum verification failed. The archive was not installed."
            }
            progress("Extracting verified Linux filesystem…")
            val staging = File(root, "rootfs-staging")
            staging.deleteRecursively()
            staging.mkdirs()
            extractSafely(archive, staging)
            File(staging, ".sentinel-rootfs-version").writeText(version)
            val old = File(root, "rootfs-old")
            old.deleteRecursively()
            if (rootfs.exists() && !rootfs.renameTo(old)) error("Could not stage the previous Linux environment.")
            if (!staging.renameTo(rootfs)) {
                if (old.exists()) old.renameTo(rootfs)
                error("Could not activate the new Linux environment.")
            }
            old.deleteRecursively()
            progress("Alpine Linux root filesystem installed.")
        } finally {
            archive.delete()
            checksum.delete()
        }
    }

    suspend fun runCommand(arguments: List<String>, timeoutSeconds: Long = 120): CommandResult =
        withContext(Dispatchers.IO) {
            val s = status()
            require(s.ready) { "Install the Linux environment first." }
            require(arguments.isNotEmpty() && arguments.all { !it.contains("\u0000") }) { "Invalid command." }
            val process = createProotProcess(arguments).start()
            val output = StringBuilder()
            val reader = Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if (output.length < 128 * 1024) output.append(line).append('\n')
                        }
                    }
                }
            }
            reader.start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
                reader.join(1000)
                error("Command timed out after $timeoutSeconds seconds.")
            }
            reader.join(1500)
            CommandResult(process.exitValue(), synchronized(output) { output.toString() })
        }

    fun startShell(): LinuxShellSession {
        require(status().ready) { "Install the Linux environment first." }
        val process = createProotProcess(listOf("/bin/sh", "-i")).start()
        return LinuxShellSession(process)
    }

    suspend fun isPackageInstalled(packageNames: String): Boolean {
        val names = packageNames.split(Regex("\\s+")).filter { it.matches(Regex("[A-Za-z0-9+_.-]+")) }
        if (names.isEmpty()) return false
        return names.all { runCommand(listOf("/sbin/apk", "info", "-e", it), 15).success }
    }

    suspend fun installPackage(packageNames: String): CommandResult {
        val names = packageNames.split(Regex("\\s+")).filter { it.matches(Regex("[A-Za-z0-9+_.-]+")) }
        require(names.isNotEmpty() && names.size == packageNames.split(Regex("\\s+")).count { it.isNotBlank() }) {
            "Invalid package selection."
        }
        val refresh = runCommand(listOf("/sbin/apk", "update"), 120)
        if (!refresh.success) return refresh
        return runCommand(listOf("/sbin/apk", "add", "--no-progress") + names, 240)
    }

    private fun createProotProcess(command: List<String>): ProcessBuilder {
        val args = mutableListOf(
            proot.absolutePath, "-0", "-r", rootfs.absolutePath, "-w", "/root",
            "-b", "${context.filesDir.absolutePath}:/sentinel-host",
            "-b", "/dev", "-b", "/proc"
        )
        args.addAll(command)
        return ProcessBuilder(args).redirectErrorStream(true).apply {
            directory(rootfs)
            environment()["HOME"] = "/root"
            environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            environment()["LANG"] = "C.UTF-8"
            environment()["TERM"] = "xterm-256color"
            environment()["PS1"] = "sentinel:\\w# "
            environment()["PROOT_TMP_DIR"] = File(root, "tmp").apply { mkdirs() }.absolutePath
            environment()["TMPDIR"] = File(rootfs, "tmp").absolutePath
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractSafely(archive: File, destination: File) {
        val rootCanonical = destination.canonicalFile
        TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val output = File(destination, entry.name).canonicalFile
                require(output.path == rootCanonical.path || output.path.startsWith(rootCanonical.path + File.separator)) {
                    "Unsafe path in rootfs archive: ${entry.name}"
                }
                when {
                    entry.isDirectory -> output.mkdirs()
                    entry.isSymbolicLink -> {
                        val link = entry.linkName
                        val target = if (link.startsWith("/")) File(destination, link.removePrefix("/"))
                            else File(output.parentFile, link)
                        val canonicalTarget = target.canonicalFile
                        require(canonicalTarget.path == rootCanonical.path ||
                            canonicalTarget.path.startsWith(rootCanonical.path + File.separator)) {
                            "Symlink escapes rootfs: ${entry.name}"
                        }
                        output.parentFile?.mkdirs()
                        if (!output.exists()) {
                            val relative = output.parentFile!!.toPath().relativize(target.toPath()).toString()
                            android.system.Os.symlink(relative, output.path)
                        }
                    }
                    entry.isFile -> {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { out -> tar.copyTo(out, 64 * 1024) }
                        if ((entry.mode and 0b001001001) != 0) output.setExecutable(true, false)
                    }
                    else -> Unit // Do not restore device nodes, sockets, FIFOs or hard links.
                }
            }
        }
    }

    companion object {
        val packages = listOf(
            ToolPackage("bash", "Bash", "bash", "Shell", "Bash command interpreter"),
            ToolPackage("core", "Core utilities", "coreutils findutils grep sed gawk less", "Shell", "Common file and text utilities"),
            ToolPackage("git", "Git", "git", "Development", "Version control"),
            ToolPackage("python", "Python", "python3 py3-pip", "Development", "Python runtime and pip"),
            ToolPackage("node", "Node.js", "nodejs npm", "Development", "JavaScript runtime and npm"),
            ToolPackage("openssh", "OpenSSH client", "openssh-client", "Network", "SSH client tools"),
            ToolPackage("curl", "curl and wget", "curl wget", "Network", "HTTP and file transfer"),
            ToolPackage("dns", "DNS utilities", "bind-tools", "Network", "dig, nslookup and host"),
            ToolPackage("nmap", "Nmap", "nmap", "Security", "Network discovery and authorized service auditing"),
            ToolPackage("jq", "jq", "jq", "Development", "JSON processor"),
            ToolPackage("nano", "Nano", "nano", "Editors", "Terminal text editor"),
            ToolPackage("vim", "Vim", "vim", "Editors", "Terminal text editor"),
            ToolPackage("clang", "Clang toolchain", "clang make cmake", "Development", "C/C++ compiler and build tools"),
            ToolPackage("whois", "WHOIS", "whois", "Network", "Domain registration lookup")
        )
    }
}

class LinuxShellSession internal constructor(private val process: Process) {
    private val writer = OutputStreamWriter(process.outputStream)
    private val reader = BufferedReader(InputStreamReader(process.inputStream))

    suspend fun readLine(): String? = withContext(Dispatchers.IO) { reader.readLine() }

    suspend fun send(command: String) = withContext(Dispatchers.IO) {
        writer.write(command)
        writer.write("\\n")
        writer.flush()
    }

    fun isAlive(): Boolean = process.isAlive

    fun stop() {
        runCatching { writer.write("exit\\n"); writer.flush() }
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
    }
}
