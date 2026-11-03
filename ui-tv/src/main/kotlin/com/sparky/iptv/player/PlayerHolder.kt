package com.sparky.iptv.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Player states (the same numbers as ExoPlayer's, 3.1). */
object PState {
    const val IDLE = 1
    const val BUFFERING = 2
    const val READY = 3
    const val ENDED = 4
}

/** Track types (the same numbers as ExoPlayer's C.TRACK_TYPE_*). */
object TrackType {
    const val AUDIO = 1
    const val TEXT = 3
}

/** A playback error without the player library's types (3.1). */
class PError(val httpCode: Int?, val codeName: String, val kind: Kind) : Exception(codeName) {
    enum class Kind { HTTP, TIMEOUT, CONNECTION, DAMAGED, BEHIND_LIVE, OTHER }
    val behindLiveWindow get() = kind == Kind.BEHIND_LIVE
}

/** Player events the screens listen to. */
interface PlayerListener {
    fun onPlayerError(e: PError) {}
    fun onPlaybackStateChanged(state: Int) {}
    fun onRenderedFirstFrame() {}
    fun onTracksChanged() {}
    fun onIsPlayingChanged(isPlaying: Boolean) {}
}

/** One choosable audio or subtitle track; [ref] is the engine's own handle. */
data class TrackOption(val label: String, val language: String?, val selected: Boolean, val ref: Any)

/**
 * What the screens need from a video player (3.1): ExoPlayer on Android TV, libVLC on Windows and Mac.
 * Every call is made on the main thread; listeners are called on the main thread.
 */
interface PlayerEngine {
    var playWhenReady: Boolean
    val isPlaying: Boolean
    val playbackState: Int
    val currentPosition: Long
    /** Length in ms, or 0 if unknown (live). */
    val duration: Long
    val isCurrentMediaItemSeekable: Boolean
    fun seekTo(ms: Long)
    fun addListener(l: PlayerListener)
    fun removeListener(l: PlayerListener)
    /** Opens [url] (from [startMs]) and plays it. The previous stream is already stopped. */
    fun setMedia(url: String, startMs: Long)
    /** Stops playback and forgets the stream (the connection closes). */
    fun stop()
    /** Frees the player; it isn't used again. */
    fun release()
    fun tracks(type: Int): List<TrackOption>
    fun select(type: Int, option: TrackOption)
    fun subtitlesOff()
    /** True when subtitles are switched on and showing one of the tracks. */
    fun subtitlesShowing(): Boolean
    /** Clears choices from the previous stream; subtitles start off. */
    fun resetTracks()
}

/**
 * One shared player, used by both the live preview and the full-screen player (2.5). Since 3.1 the
 * engine comes from the app ([factory]); the rules here are the same on every platform.
 */
object PlayerHolder {
    /** Video buffered before playback starts (2.9: 0.5 s, was 1 s). Rebuffering still waits for 2 s. */
    const val START_BUFFER_MS = 500

    /** Set by the app at start: makes the platform's player. */
    lateinit var factory: () -> PlayerEngine

    private var player: PlayerEngine? = null
    private var currentUrl: String? = null

    /** Changes when the player is released, so video surfaces attach to the new one. */
    var generation by mutableIntStateOf(0)
        private set

    /**
     * Set when the sleep timer stops playback: nothing starts playing again (for example the
     * Live TV preview) until a key is pressed.
     */
    @Volatile var held = false

    fun get(): PlayerEngine = player ?: factory().also { it.playWhenReady = true; player = it }

    /** The player if one exists, without creating it. */
    fun current(): PlayerEngine? = player

    /**
     * Plays [url]. Returns true if a new stream was opened, false if [url] was already playing
     * (so a channel switch back to the same channel isn't timed, 2.9).
     */
    fun play(url: String, startMs: Long = 0L, force: Boolean = false): Boolean {
        if (held) return false
        val p = get()
        if (!force && url == currentUrl && p.playbackState != PState.IDLE) {
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
        p.resetTracks()
        p.setMedia(url, startMs)
        p.playWhenReady = true
        return true
    }

    /** Closes kept-alive provider connections that no stream is using. */
    fun closeIdleConnections() {
        runCatching { com.sparky.iptv.data.Http.base.connectionPool.evictAll() }
    }

    fun stop() {
        currentUrl = null
        player?.stop()
    }

    /**
     * Stops playback, releases the player and closes the provider connection. The next [get]
     * builds a fresh player. Used by the sleep timer.
     */
    fun release() {
        currentUrl = null
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
        closeIdleConnections()
        generation++
    }

    /** "none", or what the player is doing with the open stream, for Diagnostics. */
    fun streamState(): String {
        val p = player ?: return "none"
        if (currentUrl == null) return "none"
        return when (p.playbackState) {
            PState.IDLE -> "none"
            PState.BUFFERING -> "open (buffering)"
            PState.READY -> if (p.playWhenReady) "open (playing)" else "open (paused)"
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
