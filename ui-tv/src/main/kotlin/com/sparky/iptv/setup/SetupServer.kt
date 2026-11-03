package com.sparky.iptv.setup

import com.sparky.iptv.ui.platform.Platform

import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sparky.iptv.data.Playlist
import com.sparky.iptv.data.SecretBox
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The setup pages the phone talks to: the login form and "Restore from a backup file". Runs on a
 * [PhoneServer] (8080–8090). [onSubmit] returns null on success or an error message.
 */
object SetupServer {
    /** The key works for 10 minutes after the last request; the Setup screen then shows a new QR code. */
    const val IDLE_MS = 10 * 60_000L

    fun startOnFreePort(
        deviceName: String,
        /** True when the TV already has a login (Change login): the restore page offers Add or Replace. */
        existing: Boolean,
        /** Handles a backup upload from the restore page; returns the JSON answer. */
        onRestore: (body: String, token: String, lock: () -> Unit) -> String,
        /** A note for the login form after a backup without a login was uploaded. */
        restoredNote: () -> String?,
        onSubmit: (Playlist) -> String?,
    ): PhoneServer? = PhoneServer.start(PhoneServer.SETUP_PORTS, IDLE_MS, sliding = true) { session ->
        try {
            if (session.uri.startsWith("/restore")) {
                return@start if (session.method == NanoHTTPD.Method.POST) json(onRestore(body(session), token) { lock() })
                else html(PhonePages.restore(deviceName, token, existing))
            }
            if (session.method == NanoHTTPD.Method.POST && session.uri.startsWith("/save")) {
                val f = form(session)
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
}

object Net {
    /** This device's address on the home network (asks the platform first, 3.1). */
    fun localIp(): String? = runCatching { Platform.app.localIp() }.getOrNull() ?: fromInterfaces()

    /** The first IPv4 address of a network interface that is up. */
    fun fromInterfaces(): String? {
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
    /** A QR code about [size] px square, dark on light (3.1: drawn with Compose, so it works everywhere). */
    fun bitmap(text: String, size: Int = 480): ImageBitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        val n = m.width
        val cell = (size / n).coerceAtLeast(1)
        val px = n * cell
        val img = ImageBitmap(px, px)
        val canvas = androidx.compose.ui.graphics.Canvas(img)
        val light = androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color(0xFFEEEBE5) }
        val dark = androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color(0xFF0E0F11) }
        canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(), light)
        for (y in 0 until n) for (x in 0 until n) {
            if (m[x, y]) canvas.drawRect((x * cell).toFloat(), (y * cell).toFloat(), ((x + 1) * cell).toFloat(), ((y + 1) * cell).toFloat(), dark)
        }
        return img
    }
}
