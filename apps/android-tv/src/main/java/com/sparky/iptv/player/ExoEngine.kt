package com.sparky.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sparky.iptv.data.USER_AGENT
import java.util.concurrent.TimeUnit

/**
 * ExoPlayer behind the shared [PlayerEngine] (3.1; the settings are those of 2.9's PlayerHolder):
 * OkHttp over the app's shared connection pool, playback after 0.5 s of video, 2 s after a stall.
 */
@OptIn(UnstableApi::class)
class ExoEngine(ctx: Context) : PlayerEngine {
    val exo: ExoPlayer
    private val adapters = HashMap<PlayerListener, Player.Listener>()

    init {
        val client = com.sparky.iptv.data.Http.base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val http = OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                PlayerHolder.START_BUFFER_MS,
                2_000,
            )
            .build()
        exo = ExoPlayer.Builder(ctx.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(load)
            .build()
    }

    override var playWhenReady: Boolean
        get() = exo.playWhenReady
        set(v) { exo.playWhenReady = v }
    override val isPlaying get() = exo.isPlaying
    override val playbackState get() = exo.playbackState
    override val currentPosition get() = exo.currentPosition
    override val duration get() = exo.duration.takeIf { it > 0 } ?: 0L
    override val isCurrentMediaItemSeekable get() = exo.isCurrentMediaItemSeekable
    override fun seekTo(ms: Long) = exo.seekTo(ms)

    override fun addListener(l: PlayerListener) {
        val a = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = l.onPlayerError(toError(error))
            override fun onPlaybackStateChanged(playbackState: Int) = l.onPlaybackStateChanged(playbackState)
            override fun onRenderedFirstFrame() = l.onRenderedFirstFrame()
            override fun onTracksChanged(tracks: Tracks) = l.onTracksChanged()
            override fun onIsPlayingChanged(isPlaying: Boolean) = l.onIsPlayingChanged(isPlaying)
        }
        adapters[l] = a
        exo.addListener(a)
    }

    override fun removeListener(l: PlayerListener) {
        adapters.remove(l)?.let { exo.removeListener(it) }
    }

    override fun setMedia(url: String, startMs: Long) {
        val builder = MediaItem.Builder().setUri(url)
        if (url.substringBefore('?').lowercase().endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        val item = builder.build()
        if (startMs > 0) exo.setMediaItem(item, startMs) else exo.setMediaItem(item)
        exo.prepare()
    }

    override fun stop() {
        exo.stop()
        exo.clearMediaItems()
    }

    override fun release() = exo.release()

    // ---------- tracks ----------
    private class Ref(val group: Tracks.Group, val index: Int)

    override fun tracks(type: Int): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        for (g in exo.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                out.add(TrackOption(label(f, type, out.size), f.language, g.isTrackSelected(i), Ref(g, i)))
            }
        }
        return out
    }

    override fun select(type: Int, option: TrackOption) {
        val r = option.ref as? Ref ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(r.group.mediaTrackGroup, r.index))
            .build()
    }

    override fun subtitlesOff() {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    override fun subtitlesShowing(): Boolean =
        !exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) &&
            tracks(C.TRACK_TYPE_TEXT).any { it.selected }

    override fun resetTracks() {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun label(f: Format, type: Int, n: Int) = TrackHelper.label(
        f.language, f.label, f.channelCount, (f.roleFlags and C.ROLE_FLAG_CAPTION) != 0, type, n,
    )

    companion object {
        /** The HTTP status the provider returned, if the error came from one. */
        fun httpCode(e: Throwable?): Int? {
            var t: Throwable? = e
            var depth = 0
            while (t != null && depth < 8) {
                if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode
                t = t.cause
                depth++
            }
            return null
        }

        fun toError(e: PlaybackException): PError {
            val code = httpCode(e)
            val kind = when {
                code != null -> PError.Kind.HTTP
                e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PError.Kind.BEHIND_LIVE
                e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PError.Kind.TIMEOUT
                e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PError.Kind.CONNECTION
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> PError.Kind.DAMAGED
                else -> PError.Kind.OTHER
            }
            return PError(code, e.errorCodeName, kind)
        }
    }
}
