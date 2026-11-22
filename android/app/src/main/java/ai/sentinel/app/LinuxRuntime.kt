package ai.sentinel.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

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
    val status: String = "Planned — package availability and Android compatibility not yet verified"
)

/**
 * Owns the private Alpine rootfs. This downloads only the official Alpine minirootfs,
 * verifies the published SHA-256 file, rejects archive path traversal, and installs
 * atomically into app-private storage. A separate Android-compatible PRoot executable
 * is required before guest commands can run.
 */
class LinuxRuntime(private val context: Context) {
    private val root = File(context.filesDir, "linux")
    private val rootfs = File(root, "rootfs")
    private val proot = File(root, "bin/proot")
    private val version = "3.24.4"

    fun status(): RuntimeStatus = RuntimeStatus(
        rootfsInstalled = File(rootfs, ".sentinel-rootfs-version").takeIf { it.isFile }
            ?.readText()?.trim() == version,
        prootInstalled = proot.isFile && proot.canExecute(),
        architecture = alpineArch(),
        rootfsPath = rootfs.absolutePath
    )

    private fun alpineArch(): String? = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "aarch64"
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "x86_64"
        else -> null
    }

    suspend fun installRootfs(progress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        val arch = alpineArch() ?: error("This device ABI is not supported yet. Supported: arm64-v8a and x86_64.")
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
                        val target = if (link.startsWith("/")) {
                            File(destination, link.removePrefix("/"))
                        } else {
                            File(output.parentFile, link)
                        }.canonicalFile
                        require(target.path == rootCanonical.path || target.path.startsWith(rootCanonical.path + File.separator)) {
                            "Symlink escapes rootfs: ${entry.name}"
                        }
                        output.parentFile?.mkdirs()
                        if (!output.exists()) {
                            val safeRelativeTarget = output.parentFile!!.toPath()
                                .relativize(target.toPath()).toString()
                            android.system.Os.symlink(safeRelativeTarget, output.path)
                        }
                    }
                    entry.isFile -> {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { out ->
                            tar.copyTo(out, 64 * 1024)
                        }
                        if ((entry.mode and 0b001001001) != 0) output.setExecutable(true, false)
                    }
                    else -> {
                        // Device nodes, sockets, FIFOs and hard links are deliberately not restored.
                    }
                }
            }
        }
    }

    companion object {
        val packages = listOf(
            ToolPackage("bash", "Bash", "bash", "Shell", "Bash command interpreter"),
            ToolPackage("core", "Core utilities", "coreutils", "Shell", "File and text utilities"),
            ToolPackage("git", "Git", "git", "Development", "Version control"),
            ToolPackage("python", "Python", "python3 py3-pip", "Development", "Python runtime and pip"),
            ToolPackage("node", "Node.js", "nodejs npm", "Development", "JavaScript runtime and npm"),
            ToolPackage("openssh", "OpenSSH", "openssh-client", "Network", "SSH client tools"),
            ToolPackage("curl", "curl and wget", "curl wget", "Network", "HTTP and file transfer"),
            ToolPackage("dns", "DNS utilities", "bind-tools", "Network", "dig, nslookup and host"),
            ToolPackage("nmap", "Nmap", "nmap", "Security", "Network discovery and service auditing"),
            ToolPackage("nikto", "Nikto", "nikto", "Security", "Web server security checks"),
            ToolPackage("jq", "jq", "jq", "Development", "JSON processor"),
            ToolPackage("nano", "Nano", "nano", "Editors", "Terminal text editor"),
            ToolPackage("vim", "Vim", "vim", "Editors", "Terminal text editor"),
            ToolPackage("clang", "Clang toolchain", "clang make cmake", "Development", "C/C++ compiler and build tools")
        )
    }
}
