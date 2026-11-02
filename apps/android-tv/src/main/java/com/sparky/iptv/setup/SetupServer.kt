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
