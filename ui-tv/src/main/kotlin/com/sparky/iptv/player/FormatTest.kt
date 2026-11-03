package com.sparky.iptv.player

import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Diagnostics → Test live formats (2.9). Plays one channel 3 times as m3u8 and 3 times as ts,
 * one stream at a time, and times each from opening the link to the player being ready.
 */
object FormatTest {
    data class Result(val m3u8: List<Long>, val ts: List<Long>, val failures: Int) {
        fun avg(l: List<Long>) = if (l.isEmpty()) null else l.average() / 1000.0
    }

    private const val TRIES = 3
    private const val TIMEOUT_MS = 15_000L

    suspend fun run(ch: Channel, onProgress: (String) -> Unit): Result = withContext(Dispatchers.Main) {
        val base = ch.url.substringBeforeLast('.')
        val m3u8 = mutableListOf<Long>()
        val ts = mutableListOf<Long>()
        var failures = 0
        AppLog.i("Live format test on channel ${ch.num} ${ch.display}")
        // Alternate the formats so a slow moment on the provider doesn't favour one of them.
        for (i in 0 until TRIES * 2) {
            val fmt = if (i % 2 == 0) "m3u8" else "ts"
            onProgress("Testing… ${i + 1} of ${TRIES * 2}")
            var ms = once("$base.$fmt")
            if (ms == -458L) {
                // The provider still counted the previous try: wait and try once more.
                delay(PlaybackErrors.CONNECTION_RETRY_MS[0])
                ms = once("$base.$fmt")
            }
            if (ms != null && ms >= 0) {
                AppLog.i("Live format test: $fmt %.1f s".format(ms / 1000.0))
                if (fmt == "m3u8") m3u8.add(ms) else ts.add(ms)
            } else {
                failures++
                AppLog.w("Live format test: $fmt didn't play")
            }
            PlayerHolder.stop()
            PlayerHolder.closeIdleConnections()
            delay(1_500)
        }
        Result(m3u8, ts, failures)
    }

    /** Milliseconds to ready; null on error or timeout; -458 when the provider refused a second connection. */
    private suspend fun once(url: String): Long? {
        val player = PlayerHolder.get()
        val t0 = com.sparky.iptv.ui.components.elapsedMs()
        var listener: PlayerListener? = null
        val r = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Long?> { cont ->
                val l = object : PlayerListener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == PState.READY && cont.isActive) cont.resume(com.sparky.iptv.ui.components.elapsedMs() - t0)
                    }
                    override fun onPlayerError(error: PError) {
                        if (cont.isActive) cont.resume(if (PlaybackErrors.isConnectionLimit(error)) -458L else null)
                    }
                }
                listener = l
                player.addListener(l)
                if (!PlayerHolder.play(url, force = true)) cont.resume(null)
            }
        }
        listener?.let { player.removeListener(it) }
        return r
    }
}
