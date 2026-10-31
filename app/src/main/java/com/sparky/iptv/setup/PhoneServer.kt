package com.sparky.iptv.setup

import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.SecretBox
import fi.iki.elonen.NanoHTTPD

/**
 * A small web server for one phone page (backup, restore, favorites folders). Every request needs the
 * one-time key from the TV's QR code (?k=…); without it the answer is 401. After [ttlMs], or once
 * [lock] is called, the key stops working.
 */
class PhoneServer(
    port: Int,
    val token: String,
    private val ttlMs: Long,
    private val handler: PhoneServer.(NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response,
) : NanoHTTPD(port) {
    private val started = System.currentTimeMillis()
    @Volatile private var locked = false

    /** Makes the key stop working (for example after three wrong passphrases). */
    fun lock() { locked = true }

    override fun serve(session: IHTTPSession): Response {
        if (!SecretBox.same(session.parameters["k"]?.firstOrNull(), token)) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain; charset=utf-8",
                "Scan the QR code on your TV to open this page.")
        }
        if (locked || System.currentTimeMillis() - started > ttlMs) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8",
                "This page has expired. Open the screen on your TV again and scan the new QR code.")
        }
        return try {
            handler(session)
        } catch (e: Exception) {
            AppLog.e("Phone page failed", e)
            json("""{"ok":false,"error":"Something went wrong on the TV: ${jsonEsc(e.message ?: "unknown")}"}""")
        }
    }

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
        /** Starts on the first free port in 8100–8110. Returns null if none could be opened. */
        fun start(ttlMs: Long, handler: PhoneServer.(NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response): PhoneServer? {
            for (port in 8100..8110) {
                val s = PhoneServer(port, SecretBox.newToken(), ttlMs, handler)
                try {
                    s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return s
                } catch (_: Exception) {
                }
            }
            return null
        }

        fun jsonEsc(s: String): String = org.json.JSONObject.quote(s).removeSurrounding("\"")
    }
}
