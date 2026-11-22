package ai.sentinel.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.IDN
import java.net.URI
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.cert.X509Certificate

/**
 * Conservative on-device HTTPS posture checks.
 * Makes a single GET request to the supplied origin; it does not crawl, exploit,
 * brute-force, enumerate paths, or send test payloads.
 */
object LocalScanner {
    suspend fun scan(input: String): Pair<Int, List<Finding>> = withContext(Dispatchers.IO) {
        val raw = if (input.contains("://")) input else "https://$input"
        val uri = URI(raw.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "For safety, enter an HTTPS URL. HTTP-only sites are not assessed by this version."
        }
        require(uri.userInfo == null) { "URLs containing usernames or passwords are not accepted." }
        val host = uri.host?.let { IDN.toASCII(it) } ?: error("Enter a valid website hostname.")
        require(host.length <= 253 && !host.startsWith(".") && !host.endsWith(".")) {
            "The hostname is invalid."
        }
        val port = if (uri.port == -1) 443 else uri.port
        require(port in 1..65535) { "The website port is invalid." }
        val findings = mutableListOf<Finding>()
        val addresses = InetAddress.getAllByName(host).map { it.hostAddress }.distinct()
        require(addresses.isNotEmpty()) { "The hostname did not resolve to an IP address." }
        findings += Finding("DNS resolution", "info",
            "$host resolved to ${addresses.joinToString()}.", "Review DNS records and remove unexpected addresses.")

        val url = URL("https", host, port, if (uri.rawPath.isNullOrBlank()) "/" else uri.rawPath +
            (if (uri.rawQuery != null) "?${uri.rawQuery}" else ""))
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 9000
            readTimeout = 12000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SentinelAI-Android/1.0 (passive-security-check)")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.5")
            setRequestProperty("Connection", "close")
        }
        try {
            val code = connection.responseCode
            val headers = connection.headerFields.entries
                .filter { it.key != null }
                .associate { it.key!!.lowercase() to (it.value?.firstOrNull() ?: "") }
            val bodyStream = if (code in 200..399) connection.inputStream else connection.errorStream
            bodyStream?.use { stream ->
                val buffer = ByteArray(8192)
                var total = 0
                while (total < 128 * 1024) {
                    val n = stream.read(buffer, 0, minOf(buffer.size, 128 * 1024 - total))
                    if (n <= 0) break
                    total += n
                }
            }
            findings += Finding("HTTPS response", "info",
                "Server returned HTTP $code. TLS negotiation succeeded.", "Investigate unexpected status codes and redirects.")
            val cert = connection.serverCertificates.firstOrNull() as? X509Certificate
            if (cert != null) {
                try {
                    cert.checkValidity()
                    val days = ((cert.notAfter.time - System.currentTimeMillis()) / 86400000L)
                    findings += if (days <= 30) Finding("TLS certificate expires soon", "medium",
                        "The presented certificate expires in $days day(s).", "Renew the certificate before it expires.")
                    else Finding("TLS certificate validity", "low",
                        "The presented certificate is currently valid and expires in $days day(s).",
                        "Keep certificate renewal monitoring enabled.")
                } catch (_: Exception) {
                    findings += Finding("TLS certificate validity", "high",
                        "The presented certificate is expired or not yet valid.", "Install a valid certificate and verify the device clock.")
                }
            }

            val checks = listOf(
                Triple("strict-transport-security", "HSTS", "Enable Strict-Transport-Security with an appropriate max-age after confirming HTTPS works across the site."),
                Triple("content-security-policy", "Content Security Policy", "Define a restrictive Content-Security-Policy and avoid unsafe-inline/unsafe-eval where possible."),
                Triple("x-content-type-options", "MIME sniffing protection", "Set X-Content-Type-Options: nosniff."),
                Triple("referrer-policy", "Referrer policy", "Set a privacy-conscious Referrer-Policy such as strict-origin-when-cross-origin."),
                Triple("permissions-policy", "Permissions Policy", "Disable browser features the site does not need.")
            )
            var passed = 0
            checks.forEach { (header, title, fix) ->
                val value = headers[header]
                if (!value.isNullOrBlank()) {
                    passed++
                    findings += Finding(title, "low", "Present: $value", "Review the value periodically.")
                } else {
                    val severity = if (header == "content-security-policy" || header == "strict-transport-security") "medium" else "low"
                    findings += Finding("Missing $title", severity,
                        "The response did not include the $header header.", fix)
                }
            }
            val frameOptions = headers["x-frame-options"]
            val csp = headers["content-security-policy"] ?: ""
            if (!frameOptions.isNullOrBlank() || csp.contains("frame-ancestors", ignoreCase = true)) {
                passed++
                findings += Finding("Clickjacking protection", "low",
                    "X-Frame-Options or CSP frame-ancestors is present.", "Keep framing rules limited to trusted origins.")
            } else {
                findings += Finding("Missing clickjacking protection", "medium",
                    "Neither X-Frame-Options nor CSP frame-ancestors was detected.",
                    "Set X-Frame-Options or a CSP frame-ancestors directive.")
            }
            val cookies = connection.headerFields.entries
                .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                .flatMap { it.value ?: emptyList() }
            if (cookies.isNotEmpty()) {
                val weak = cookies.filter { cookie ->
                    val c = cookie.lowercase()
                    !c.contains("secure") || !c.contains("httponly") || !c.contains("samesite")
                }
                if (weak.isEmpty()) findings += Finding("Cookie security attributes", "low",
                    "Observed Set-Cookie responses include Secure, HttpOnly and SameSite attributes.",
                    "Retain secure cookie attributes.")
                else findings += Finding("Review cookie security attributes", "medium",
                    "${weak.size} observed cookie(s) are missing one or more of Secure, HttpOnly or SameSite.",
                    "Set Secure, HttpOnly and an appropriate SameSite value on session cookies.")
            }
            val server = headers["server"]
            if (!server.isNullOrBlank()) findings += Finding("Server banner disclosed", "low",
                "The response exposes the Server header: $server.", "Consider minimizing unnecessary version disclosure.")
            val score = ((passed.toDouble() / 6.0) * 100).toInt().coerceIn(0, 100)
            score to findings
        } finally {
            connection.disconnect()
        }
    }
}
