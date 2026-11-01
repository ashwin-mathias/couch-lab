package com.sparky.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sparky.iptv.data.USER_AGENT

/** One shared player, used by both the live preview and the full-screen player. */
object PlayerHolder {
    /** Video buffered before playback starts (2.9: 0.5 s, was 1 s). Rebuffering still waits for 2 s. */
    const val START_BUFFER_MS = 500
    private var player: ExoPlayer? = null
    private var currentUrl: String? = null
    private var client: OkHttpClient? = null

    /** Changes when the player is released, so video surfaces attach to the new one. */
    var generation by mutableIntStateOf(0)
        private set

    /**
     * Set when the sleep timer stops playback: nothing starts playing again (for example the
     * Live TV preview) until a key is pressed.
     */
    @Volatile var held = false

    @OptIn(UnstableApi::class)
    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        // One OkHttp client keeps the connection to the provider open between channel switches.
        // It shares the app's connection pool (Http.base).
        val client = com.sparky.iptv.data.Http.base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        this.client = client
        val http = OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)
        // Start playing after about 0.5 s of video (2.9; was 1 s, default 2.5 s); 2 s after a stall.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                START_BUFFER_MS,
                2_000,
            )
            .build()
        val p = ExoPlayer.Builder(ctx.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(load)
            .build()
        p.playWhenReady = true
        player = p
        return p
    }

    /**
     * Plays [url]. Returns true if a new stream was opened, false if [url] was already playing
     * (so a channel switch back to the same channel isn't timed, 2.9).
     */
    fun play(ctx: Context, url: String, startMs: Long = 0L, force: Boolean = false): Boolean {
        if (held) return false
        val p = get(ctx)
        if (!force && url == currentUrl && p.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            p.playWhenReady = true
            return false
        }
        // 2.9: close the previous stream fully before opening the next one. The plan allows one
        // connection, and an idle kept-alive socket can still count as the old stream (HTTP 458).
        if (currentUrl != null) {
            p.stop()
            closeIdleConnections()
        }
        currentUrl = url
        TrackHelper.reset(p)
        val builder = MediaItem.Builder().setUri(url)
        val lower = url.substringBefore('?').lowercase()
        if (lower.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        val item = builder.build()
        if (startMs > 0) p.setMediaItem(item, startMs) else p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        return true
    }

    /** Closes kept-alive provider connections that no stream is using. */
    fun closeIdleConnections() {
        runCatching { com.sparky.iptv.data.Http.base.connectionPool.evictAll() }
    }

    fun stop() {
        currentUrl = null
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    /**
     * Stops playback, releases the player and closes the provider connection. The next [get]
     * builds a fresh player. Used by the sleep timer.
     */
    fun release() {
        currentUrl = null
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
        client?.let { c -> runCatching { c.connectionPool.evictAll() } }
        client = null
        generation++
    }

    /** "none", or what the player is doing with the open stream, for Diagnostics. */
    fun streamState(): String {
        val p = player ?: return "none"
        if (currentUrl == null) return "none"
        return when (p.playbackState) {
            androidx.media3.common.Player.STATE_IDLE -> "none"
            androidx.media3.common.Player.STATE_BUFFERING -> "open (buffering)"
            androidx.media3.common.Player.STATE_READY -> if (p.playWhenReady) "open (playing)" else "open (paused)"
            else -> "open (ended)"
        }
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun resume() {
        player?.let { if (currentUrl != null) it.playWhenReady = true }
    }

    fun currentPosition(): Long = player?.currentPosition ?: 0L
    fun duration(): Long = player?.duration?.takeIf { it > 0 } ?: 0L
}
