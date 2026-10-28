package com.sparky.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sparky.iptv.data.USER_AGENT

/** One shared player, used by both the live preview and the full-screen player. */
object PlayerHolder {
    private var player: ExoPlayer? = null
    private var currentUrl: String? = null

    @OptIn(UnstableApi::class)
    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val p = ExoPlayer.Builder(ctx.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
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
