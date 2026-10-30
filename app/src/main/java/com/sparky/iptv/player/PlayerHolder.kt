package com.sparky.iptv.player

import android.content.Context
import androidx.annotation.OptIn
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
    private var player: ExoPlayer? = null
    private var currentUrl: String? = null

    @OptIn(UnstableApi::class)
    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        // One OkHttp client keeps the connection to the provider open between channel switches.
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
        val http = OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)
        // Start playing after about 1 s of video (default 2.5 s); 2 s after a stall.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                1_000,
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

    fun play(ctx: Context, url: String, startMs: Long = 0L, force: Boolean = false) {
        val p = get(ctx)
        if (!force && url == currentUrl && p.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            p.playWhenReady = true
            return
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
    }

    fun stop() {
        currentUrl = null
        player?.let {
            it.stop()
            it.clearMediaItems()
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
