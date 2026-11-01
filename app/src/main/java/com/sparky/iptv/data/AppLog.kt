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
    // 2.9: the file is checked every 100 lines while running (not only at start), Diagnostics
    // reads only its last 64 KB, and a message repeated more than 20 times a minute is collapsed.
    private const val CHECK_EVERY = 100
    private const val TAIL_BYTES = 64 * 1024
    private const val MAX_REPEATS = 20
    private var sinceCheck = 0
    private var lastDay = ""
    private val dayFmt = SimpleDateFormat("EEE d MMM yyyy", Locale.US)
    private val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US)
    // Repeat counting: message -> count in the current minute.
    private var repeatMinute = 0L
    private val repeats = HashMap<String, Int>()
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
        synchronized(this) { trimFile() }
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
    fun e(msg: String, t: Throwable? = null) {
        // A load that was cancelled (page closed, newer request started) is not a problem.
        if (t != null && isCancellation(t)) return
        add("ERROR", if (t != null) "$msg: ${t.javaClass.simpleName}: ${t.message}" else msg)
    }

    /** True for cancelled coroutines and cancelled network calls, anywhere in the cause chain. */
    fun isCancellation(t: Throwable): Boolean {
        var c: Throwable? = t
        var depth = 0
        while (c != null && depth < 8) {
            if (c is java.util.concurrent.CancellationException) return true
            if (c is java.io.InterruptedIOException && c !is java.net.SocketTimeoutException) return true
            if (c is java.io.IOException && c.message?.lowercase() in setOf("canceled", "cancelled", "socket closed")) return true
            c = c.cause
            depth++
        }
        return false
    }

    @Synchronized
    private fun add(level: String, msg: String) {
        val now = Date()
        // Collapse a runaway message: after 20 in a minute, one summary line when the minute ends.
        val minute = now.time / 60_000
        if (minute != repeatMinute) {
            repeats.filterValues { it > MAX_REPEATS }.forEach { (m, n) ->
                write(now, "INFO ", "(repeated ${n - MAX_REPEATS} more times in the last minute) $m")
            }
            repeats.clear()
            repeatMinute = minute
        }
        val key = "$level $msg"
        val n = (repeats[key] ?: 0) + 1
        repeats[key] = n
        if (n > MAX_REPEATS) return
        write(now, level, msg)
    }

    private fun write(now: Date, level: String, msg: String) {
        // A date line at start and whenever the day changes: "— Sun 1 Nov 2026 —".
        val day = dayKey.format(now)
        if (day != lastDay) {
            lastDay = day
            append("— ${dayFmt.format(now)} —")
        }
        append("${time.format(now)} $level ${redact(msg)}")
    }

    private fun append(line: String) {
        Log.d("SparkyIPTV", line)
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        runCatching { logFile?.appendText(line + "\n") }
        if (++sinceCheck >= CHECK_EVERY) {
            sinceCheck = 0
            trimFile()
        }
    }

    /** Over 256 KB: keep the newest 200 lines. Called at start and every 100 lines (under the lock). */
    private fun trimFile() {
        val f = logFile ?: return
        runCatching {
            if (f.exists() && f.length() > MAX_FILE_BYTES) {
                val keep = tailLines(f).takeLast(MAX_LINES / 2)
                f.writeText(keep.joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** The lines in the last 64 KB of [f], without the first (partial) one. */
    private fun tailLines(f: File): List<String> {
        java.io.RandomAccessFile(f, "r").use { raf ->
            val len = raf.length()
            val start = (len - TAIL_BYTES).coerceAtLeast(0L)
            raf.seek(start)
            val buf = ByteArray((len - start).toInt())
            raf.readFully(buf)
            val ls = String(buf, Charsets.UTF_8).split('\n').filter { it.isNotEmpty() }
            return if (start > 0) ls.drop(1) else ls
        }
    }

    /** Recent log lines, newest last (includes lines from before a crash). Reads only the file's end. */
    fun text(): String = runCatching {
        val f = logFile ?: return@runCatching null
        synchronized(this) { tailLines(f) }.takeLast(MAX_LINES).joinToString("\n")
    }.getOrNull() ?: synchronized(this) { lines.joinToString("\n") }

    /** "Log file: 84 KB · 1,120 lines", for Diagnostics. */
    fun fileSummary(): String = runCatching {
        val f = logFile ?: return@runCatching null
        if (!f.exists()) return@runCatching "Log file: empty"
        var n = 0
        synchronized(this) { f.forEachLine { n++ } }
        "Log file: ${(f.length() + 1023) / 1024} KB · ${"%,d".format(n)} lines"
    }.getOrNull() ?: "Log file: not available"

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
