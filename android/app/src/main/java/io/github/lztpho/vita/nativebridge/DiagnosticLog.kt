// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.content.Context
import android.os.Build
import android.webkit.WebView
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException

class DiagnosticLog(
    private val directory: File,
    private val metadata: () -> Map<String, String>,
) {
    companion object {
        private const val ACTIVE_FILE = "vita-diagnostics.jsonl"
        private const val MAX_FILE_BYTES = 128 * 1024L
        private const val BACKUP_COUNT = 2
        private const val MAX_MESSAGE_LENGTH = 240
        private val lock = Any()
        private val exportNameFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

        fun from(context: Context): DiagnosticLog {
            val appContext = context.applicationContext
            return DiagnosticLog(File(appContext.noBackupFilesDir, "diagnostics")) {
                val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                linkedMapOf(
                    "app" to "Vita",
                    "version" to (packageInfo.versionName ?: "unknown"),
                    "versionCode" to PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
                    "android" to Build.VERSION.RELEASE,
                    "sdk" to Build.VERSION.SDK_INT.toString(),
                    "device" to listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim(),
                    "webView" to (runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "unknown"),
                )
            }
        }

        internal fun redact(value: String): String = value
            .replace(Regex("(?i)\\b(?:https?|content|file)://[^\\s\\\"']+"), "<redacted-uri>")
            .replace(Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer <redacted>")
            .replace(Regex("(?i)(api[-_ ]?key|token|secret|authorization)\\s*[:=]\\s*[^,;\\s]+"), "$1=<redacted>")
            .replace(Regex("(?i)\\b(?:sk|key)-[A-Za-z0-9_-]{8,}"), "<redacted-key>")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .take(MAX_MESSAGE_LENGTH)
    }

    fun record(event: String, outcome: String = "ok", durationMs: Long? = null, error: Throwable? = null, phase: String? = null) {
        val entry = JSONObject()
            .put("time", Instant.now().toString())
            .put("event", event.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(64))
            .put("outcome", outcome.take(16))
        durationMs?.let { entry.put("durationMs", it.coerceAtLeast(0)) }
        phase?.let { entry.put("phase", it.filter { character -> character.isLetterOrDigit() || character == '_' || character == '-' }.take(32)) }
        error?.let {
            entry.put("errorType", it.javaClass.simpleName.take(80))
            entry.put("errorCategory", errorCategory(it))
        }
        append(entry.toString() + "\n")
    }

    fun exportFileName(now: Instant = Instant.now()): String = "vita-diagnostics-${exportNameFormat.format(now)}-UTC.txt"

    fun exportText(): String = synchronized(lock) {
        val header = buildString {
            appendLine("Vita diagnostic log")
            appendLine("Generated: ${Instant.now()}")
            metadata().forEach { (key, value) -> appendLine("$key: ${redact(value)}") }
            appendLine("Privacy: no API keys, user content, image locations, or full provider endpoints are intentionally recorded.")
            appendLine()
        }
        val body = (BACKUP_COUNT downTo 1).map(::backupFile).plus(activeFile())
            .filter(File::isFile)
            .joinToString(separator = "") { file ->
                runCatching { file.readLines(StandardCharsets.UTF_8).joinToString("\n") { redact(it) } }
                    .getOrDefault("") + "\n"
            }
        header + body.trimEnd() + "\n"
    }

    fun clear() = synchronized(lock) {
        directory.listFiles()?.filter { it.name == ACTIVE_FILE || it.name.startsWith("$ACTIVE_FILE.") }?.forEach { file ->
            check(file.delete()) { "诊断日志清理失败" }
        }
        if (directory.isDirectory && directory.list().isNullOrEmpty()) directory.delete()
    }

    private fun append(line: String) = synchronized(lock) {
        directory.mkdirs()
        val bytes = line.toByteArray(StandardCharsets.UTF_8)
        if (activeFile().length() + bytes.size > MAX_FILE_BYTES) rotate()
        activeFile().appendBytes(bytes)
    }

    private fun rotate() {
        backupFile(BACKUP_COUNT).delete()
        for (index in BACKUP_COUNT - 1 downTo 1) {
            val source = backupFile(index)
            if (source.exists()) source.renameTo(backupFile(index + 1))
        }
        val active = activeFile()
        if (active.exists()) active.renameTo(backupFile(1))
    }

    private fun activeFile() = File(directory, ACTIVE_FILE)
    private fun backupFile(index: Int) = File(directory, "$ACTIVE_FILE.$index")

    private fun errorCategory(error: Throwable): String = when (error) {
        is UnknownHostException -> "dns"
        is SocketTimeoutException -> "timeout"
        is SSLException -> "tls"
        is SecurityException -> "security"
        is org.json.JSONException -> "response_format"
        is IllegalArgumentException -> "input"
        is IOException -> "network"
        else -> "operation"
    }
}
