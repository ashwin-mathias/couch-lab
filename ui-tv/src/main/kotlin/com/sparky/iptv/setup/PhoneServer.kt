package com.sparky.iptv.setup

import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.SecretBox
import fi.iki.elonen.NanoHTTPD

/**
 * The one small web server behind every phone page: setup, diagnostics, backup, restore and
 * favorites folders (shared since 2.8). Every request needs the one-time key from the TV's QR code
 * (?k=…); without it the answer is 401 before anything else is read. Request bodies over 2 MB are
 * refused.
 *
 * The key stops working after [ttlMs]: counted from the start, or with [sliding] from the last
 * request (setup and diagnostics, whose TV screens show a fresh QR when the old one runs out).
 * [lock] stops it at once (for example after three wrong passphrases).
 */
class PhoneServer(
    port: Int,
    val token: String,
    private val ttlMs: Long,
    private val sliding: Boolean,
    private val handler: PhoneServer.(NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response,
) : NanoHTTPD(port) {
    private val started = System.currentTimeMillis()
    @Volatile private var lastUse = started
    @Volatile private var locked = false

    fun lock() { locked = true }

    /** True once the key no longer works; the TV screen should show a new QR code. */
    val expired: Boolean
        get() = locked || System.currentTimeMillis() - (if (sliding) lastUse else started) > ttlMs

    override fun serve(session: IHTTPSession): Response {
        if (!SecretBox.same(session.parameters["k"]?.firstOrNull(), token)) {
            return text(Response.Status.UNAUTHORIZED, "Scan the QR code on your TV to open this page.")
        }
        if (expired) {
            return text(Response.Status.FORBIDDEN, "This page has expired. Open the screen on your TV again and scan the new QR code.")
        }
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (length > MAX_BODY) {
            return text(Response.Status.PAYLOAD_TOO_LARGE, "That's too large. A Sparky backup is well under 1 MB.")
        }
        lastUse = System.currentTimeMillis()
        return try {
            handler(session)
        } catch (e: Exception) {
            AppLog.e("Phone page failed", e)
            json("""{"ok":false,"error":"Something went wrong on the TV: ${jsonEsc(e.message ?: "unknown")}"}""")
        }
    }

    private fun text(status: Response.Status, msg: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)

    /** The request body of a JSON POST. */
    fun body(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }

    @Suppress("DEPRECATION")
    fun form(session: IHTTPSession): Map<String, String> {
        session.parseBody(HashMap())
        return session.parms
    }

    fun html(body: String): Response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)
    fun json(body: String): Response = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    /** A file the phone saves (Save to Files on iPhone, Downloads on Android). */
    fun download(name: String, text: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", text).apply {
            addHeader("Content-Disposition", "attachment; filename=\"$name\"")
            addHeader("Cache-Control", "no-store")
        }

    companion object {
        /** Largest request body accepted (backups are a few KB to about 100 KB). */
        const val MAX_BODY = 2L * 1024 * 1024

        /** Setup 8080–8090, diagnostics 8091–8099, backup / restore / folders 8100–8110. */
        val SETUP_PORTS = 8080..8090
        val LOG_PORTS = 8091..8099
        val PAGE_PORTS = 8100..8110

        /** Starts on the first free port in [ports] with a new key. Returns null if none could be opened. */
        fun start(
            ports: IntRange,
            ttlMs: Long,
            sliding: Boolean = false,
            handler: PhoneServer.(NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response,
        ): PhoneServer? {
            for (port in ports) {
                val s = PhoneServer(port, SecretBox.newToken(), ttlMs, sliding, handler)
                try {
                    s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return s
                } catch (_: Exception) {
                    // port busy, try the next one
                }
            }
            return null
        }

        fun jsonEsc(s: String): String = org.json.JSONObject.quote(s).removeSurrounding("\"")
    }
}
