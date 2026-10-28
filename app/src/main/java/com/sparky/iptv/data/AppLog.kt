package com.sparky.iptv.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small activity log for troubleshooting. Kept in memory and mirrored to a file,
 * so it survives a crash. Never log passwords: use [redact] on anything containing URLs.
 */
object AppLog {
    private const val MAX_LINES = 400
    private const val MAX_FILE_BYTES = 256 * 1024
    private val lines = ArrayDeque<String>()
    private var logFile: File? = null
    private var crashFile: File? = null
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val secrets = mutableSetOf<String>()

    fun init(ctx: Context) {
        if (logFile != null) return
        val dir = ctx.filesDir
        logFile = File(dir, "app_log.txt")
        crashFile = File(dir, "last_crash.txt")
        logFile?.let { f ->
            if (f.exists() && f.length() > MAX_FILE_BYTES) {
                val tail = f.readLines().takeLast(MAX_LINES / 2)
                f.writeText(tail.joinToString("\n", postfix = "\n"))
            }
        }
        installCrashHandler(ctx)
        i("App started · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
    }

    /** Values that must never appear in the log (the password, and the username in URLs). */
    fun setSecrets(vararg values: String) {
        synchronized(secrets) {
            secrets.clear()
            values.filter { it.length >= 3 }.forEach { secrets.add(it) }
        }
    }

    fun redact(s: String): String {
        var out = s
        synchronized(secrets) { secrets.forEach { out = out.replace(it, "•••") } }
        return out
    }

    fun i(msg: String) = add("INFO ", msg)
    fun w(msg: String) = add("WARN ", msg)
    fun e(msg: String, t: Throwable? = null) =
        add("ERROR", if (t != null) "$msg: ${t.javaClass.simpleName}: ${t.message}" else msg)

    @Synchronized
    private fun add(level: String, msg: String) {
        val line = "${time.format(Date())} $level ${redact(msg)}"
        Log.d("SparkyIPTV", line)
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        runCatching { logFile?.appendText(line + "\n") }
    }

    /** Recent log lines, newest last (includes lines from before a crash). */
    fun text(): String = runCatching { logFile?.readLines()?.takeLast(MAX_LINES)?.joinToString("\n") }
        .getOrNull() ?: synchronized(this) { lines.joinToString("\n") }

    private fun seenFile() = crashFile?.let { java.io.File(it.parentFile, "seen_crash.txt") }

    /** The most recent crash, whether or not it was already shown on the loading screen. */
    fun lastCrash(): String? = runCatching {
        crashFile?.takeIf { it.exists() }?.readText() ?: seenFile()?.takeIf { it.exists() }?.readText()
    }.getOrNull()

    /** Summary of a crash not yet shown to the user; marks it as shown. */
    fun takeUnseenCrashSummary(): String? {
        val f = crashFile ?: return null
        if (!f.exists()) return null
        val c = runCatching { f.readText() }.getOrNull() ?: return null
        runCatching { seenFile()?.let { s -> s.delete(); f.renameTo(s) } }
        return summarize(c)
    }

    private fun summarize(c: String): String? {
        val ls = c.lines().filter { it.isNotBlank() }
        val err = ls.getOrNull(1) ?: return null
        val at = ls.drop(2).firstOrNull { it.contains("com.sparky") }?.trim()
        return listOfNotNull(err.take(160), at?.take(160)).joinToString("\n")
    }

    fun clearCrash() { runCatching { crashFile?.delete(); seenFile()?.delete() } }

    private fun installCrashHandler(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
                File(ctx.filesDir, "last_crash.txt").writeText(
                    "$stamp · version $version · thread ${thread.name}\n" + redact(Log.getStackTraceString(e))
                )
                add("CRASH", "${e.javaClass.simpleName}: ${e.message}")
            }
            previous?.uncaughtException(thread, e)
        }
    }
}
