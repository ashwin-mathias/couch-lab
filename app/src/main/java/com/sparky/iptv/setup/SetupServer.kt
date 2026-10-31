package com.sparky.iptv.setup

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sparky.iptv.data.Playlist
import com.sparky.iptv.data.SecretBox
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Tiny web server the phone talks to during setup. It serves the login form and
 * hands the submitted login to [onSubmit], which returns null on success or an error message.
 */
class SetupServer(
    port: Int,
    private val deviceName: String,
    /** One-time key that must be in the address (?k=…). Only the QR code on the TV carries it. */
    val token: String,
    private val onSubmit: (Playlist) -> String?,
    /** Handles a backup upload from the restore page; returns the JSON answer. */
    private val onRestore: (body: String, token: String, lock: () -> Unit) -> String,
    /** A note for the login form after a backup without a login was uploaded. */
    private val restoredNote: () -> String?,
    /** True when the TV already has a login (Change login): the restore page offers Add or Replace. */
    private val existing: Boolean,
) : NanoHTTPD(port) {
    @Volatile private var locked = false

    override fun serve(session: IHTTPSession): Response {
        val key = session.parameters["k"]?.firstOrNull()
        if (!SecretBox.same(key, token)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN, "text/plain; charset=utf-8",
                "Scan the QR code on your TV to open this page.",
            )
        }
        if (locked) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN, "text/plain; charset=utf-8",
                "Open the setup screen on your TV again and scan the new QR code.",
            )
        }
        return try {
            if (session.uri.startsWith("/restore")) {
                if (session.method == Method.POST) {
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8",
                        onRestore(files["postData"] ?: "", token) { locked = true })
                }
                return html(PhonePages.restore(deviceName, token, existing))
            }
            if (session.method == Method.POST && session.uri.startsWith("/save")) {
                session.parseBody(HashMap())
                @Suppress("DEPRECATION")
                val f = session.parms
                val type = if (f["type"] == "m3u") "m3u" else "xtream"
                val p = Playlist(
                    type = type,
                    name = f["name"]?.trim()?.ifBlank { null } ?: "Home",
                    url = (if (type == "m3u") f["m3u"] else f["url"])?.trim() ?: "",
                    user = f["user"]?.trim() ?: "",
                    pass = f["pass"]?.trim() ?: "",
                )
                val missing = when {
                    p.url.isBlank() -> if (type == "m3u") "Enter the playlist link." else "Enter the server URL."
                    type == "xtream" && (p.user.isBlank() || p.pass.isBlank()) -> "Enter your username and password."
                    else -> null
                }
                val err = missing ?: onSubmit(p)
                if (err == null) html(SetupPage.done(deviceName)) else html(SetupPage.form(deviceName, token, p, err))
            } else {
                val note = if (session.parameters["restored"] != null) restoredNote() else null
                html(SetupPage.form(deviceName, token, null, null, note))
            }
        } catch (e: Exception) {
            html(SetupPage.form(deviceName, token, null, "Something went wrong: ${e.message}"))
        }
    }

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    companion object {
        /** Starts on the first free port from 8080. Returns null if none could be opened. */
        fun startOnFreePort(
            deviceName: String,
            existing: Boolean,
            onRestore: (String, String, () -> Unit) -> String,
            restoredNote: () -> String?,
            onSubmit: (Playlist) -> String?,
        ): SetupServer? {
            for (port in 8080..8090) {
                val s = SetupServer(port, deviceName, SecretBox.newToken(), onSubmit, onRestore, restoredNote, existing)
                try {
                    s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return s
                } catch (_: Exception) {
                    // port busy, try the next one
                }
            }
            return null
        }
    }
}

object Net {
    fun localIp(ctx: Context): String? {
        runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val lp = cm.getLinkProperties(cm.activeNetwork)
            lp?.linkAddresses?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress?.let { return it }
        }
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces().toList()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses.toList()) {
                    if (a is Inet4Address && !a.isLoopbackAddress) return a.hostAddress
                }
            }
        }
        return null
    }
}

object Qr {
    fun bitmap(text: String, size: Int = 480): ImageBitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            px[y * size + x] = if (matrix[x, y]) 0xFF0E0F11.toInt() else 0xFFEEEBE5.toInt()
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp.asImageBitmap()
    }
}
