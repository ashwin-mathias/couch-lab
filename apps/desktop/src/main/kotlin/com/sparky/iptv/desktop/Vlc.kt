package com.sparky.iptv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.USER_AGENT
import com.sparky.iptv.player.PError
import com.sparky.iptv.player.PState
import com.sparky.iptv.player.PlayerEngine
import com.sparky.iptv.player.PlayerListener
import com.sparky.iptv.player.TrackHelper
import com.sparky.iptv.player.TrackOption
import com.sparky.iptv.player.TrackType
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Our own small binding to libVLC 3 (3.1). libVLC is LGPL, so the app stays MIT (no vlcj).
 * Only the calls Sparky needs are declared.
 */
@Suppress("FunctionName")
interface LibVlc : Library {
    fun libvlc_new(argc: Int, argv: Array<String>?): Pointer?
    fun libvlc_release(inst: Pointer)
    fun libvlc_get_version(): String

    fun libvlc_media_new_location(inst: Pointer, mrl: String): Pointer?
    fun libvlc_media_add_option(media: Pointer, option: String)
    fun libvlc_media_release(media: Pointer)

    fun libvlc_media_player_new(inst: Pointer): Pointer?
    fun libvlc_media_player_release(mp: Pointer)
    fun libvlc_media_player_set_media(mp: Pointer, media: Pointer?)
    fun libvlc_media_player_play(mp: Pointer): Int
    fun libvlc_media_player_set_pause(mp: Pointer, doPause: Int)
    fun libvlc_media_player_stop(mp: Pointer)
    fun libvlc_media_player_get_state(mp: Pointer): Int
    fun libvlc_media_player_get_time(mp: Pointer): Long
    fun libvlc_media_player_set_time(mp: Pointer, time: Long)
    fun libvlc_media_player_get_length(mp: Pointer): Long
    fun libvlc_media_player_is_seekable(mp: Pointer): Int

    fun libvlc_audio_get_track_description(mp: Pointer): Pointer?
    fun libvlc_audio_get_track(mp: Pointer): Int
    fun libvlc_audio_set_track(mp: Pointer, id: Int): Int
    fun libvlc_video_get_spu_description(mp: Pointer): Pointer?
    fun libvlc_video_get_spu(mp: Pointer): Int
    fun libvlc_video_set_spu(mp: Pointer, id: Int): Int
    fun libvlc_track_description_list_release(p: Pointer)

    fun libvlc_video_set_callbacks(mp: Pointer, lock: LockCb, unlock: UnlockCb?, display: DisplayCb?, opaque: Pointer?)
    fun libvlc_video_set_format_callbacks(mp: Pointer, setup: SetupCb, cleanup: CleanupCb?)

    interface LockCb : Callback { fun invoke(opaque: Pointer?, planes: Pointer): Pointer? }
    interface UnlockCb : Callback { fun invoke(opaque: Pointer?, picture: Pointer?, planes: Pointer?) }
    interface DisplayCb : Callback { fun invoke(opaque: Pointer?, picture: Pointer?) }
    interface SetupCb : Callback {
        fun invoke(opaque: Pointer?, chroma: Pointer, width: Pointer, height: Pointer, pitches: Pointer, lines: Pointer): Int
    }
    interface CleanupCb : Callback { fun invoke(opaque: Pointer?) }
}

/** Loads the libVLC bundled with the app (resources/<os>/vlc), or an installed VLC as a fallback. */
object VlcLoader {
    @Volatile var error: String? = null
        private set

    val lib: LibVlc? by lazy { load() }

    private fun candidates(): List<File> {
        val res = System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") }
        val dev = File("resources/${if (Os.isWindows) "windows" else "macos-arm64"}/vlc")
        val installed = when {
            Os.isWindows -> File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "VideoLAN\\VLC")
            Os.isMac -> File("/Applications/VLC.app/Contents/MacOS/lib")
            else -> File("/usr/lib")
        }
        return listOfNotNull(res, dev, installed).filter { it.isDirectory }
    }

    private fun load(): LibVlc? {
        for (dir in candidates()) {
            runCatching {
                if (Os.isMac) {
                    // libVLC finds its plugins through VLC_PLUGIN_PATH on the Mac.
                    val plugins = listOf(File(dir, "plugins"), File(dir.parentFile, "plugins")).firstOrNull { it.isDirectory }
                    if (plugins != null) CLib.INSTANCE.setenv("VLC_PLUGIN_PATH", plugins.absolutePath, 1)
                }
                NativeLibrary.addSearchPath("libvlccore", dir.absolutePath)
                NativeLibrary.addSearchPath("libvlc", dir.absolutePath)
                NativeLibrary.addSearchPath("vlccore", dir.absolutePath)
                NativeLibrary.addSearchPath("vlc", dir.absolutePath)
                runCatching { NativeLibrary.getInstance(if (Os.isWindows) "libvlccore" else "vlccore") }
                val l = Native.load(if (Os.isWindows) "libvlc" else "vlc", LibVlc::class.java)
                AppLog.i("libVLC ${l.libvlc_get_version()} from $dir")
                return l
            }.onFailure { AppLog.w("libVLC not loaded from $dir: ${it.message}") }
        }
        error = "The video engine (libVLC) couldn't be loaded."
        AppLog.e(error!!)
        return null
    }

    interface CLib : Library {
        fun setenv(name: String, value: String, overwrite: Int): Int
        companion object { val INSTANCE: CLib by lazy { Native.load("c", CLib::class.java) } }
    }
}

/** One decoded video frame, BGRA (VLC's "RV32"), [width] × [height]. */
class VideoFrame(val width: Int, val height: Int, val pixels: ByteArray)

/**
 * libVLC behind the shared [PlayerEngine] (3.1). Video is drawn by Compose (frames come through
 * libVLC's video callbacks), so the info bar and buttons can sit on top of it.
 * libVLC doesn't report HTTP codes, so a connection limit shows as a general playback error here.
 */
class VlcEngine : PlayerEngine {
    private val vlc = VlcLoader.lib
    private val inst: Pointer? = vlc?.libvlc_new(3, arrayOf("--no-video-title-show", "--no-osd", "--quiet"))
    private val mp: Pointer? = inst?.let { vlc?.libvlc_media_player_new(it) }
    /** libVLC's control calls can block (stop joins its threads), so they run in order off the UI thread. */
    private val control = Executors.newSingleThreadExecutor { r -> Thread(r, "vlc-control").apply { isDaemon = true } }
    private val listeners = LinkedHashSet<PlayerListener>()

    /** The newest frame; [frameTick] changes when it does, so the video surface redraws. */
    val frame = AtomicReference<VideoFrame?>(null)
    var frameTick by mutableIntStateOf(0)
        private set

    private var hasMedia = false
    private var wantPlay = true
    private var lastState = PState.IDLE
    private var lastPlaying = false
    private var lastTrackCount = -1
    @Volatile private var firstFramePending = false
    private var errorSent = false

    // ---- video callbacks (kept in fields so they aren't garbage-collected) ----
    @Volatile private var buffer: Memory? = null
    @Volatile private var w = 0
    @Volatile private var h = 0
    private val setupCb = object : LibVlc.SetupCb {
        override fun invoke(opaque: Pointer?, chroma: Pointer, width: Pointer, height: Pointer, pitches: Pointer, lines: Pointer): Int {
            chroma.write(0, "RV32".toByteArray(Charsets.US_ASCII), 0, 4)
            w = width.getInt(0); h = height.getInt(0)
            pitches.setInt(0, w * 4)
            lines.setInt(0, h)
            buffer = Memory(w.toLong() * h * 4)
            return 1
        }
    }
    private val cleanupCb = object : LibVlc.CleanupCb { override fun invoke(opaque: Pointer?) {} }
    private val lockCb = object : LibVlc.LockCb {
        override fun invoke(opaque: Pointer?, planes: Pointer): Pointer? { planes.setPointer(0, buffer); return null }
    }
    private val displayCb = object : LibVlc.DisplayCb {
        override fun invoke(opaque: Pointer?, picture: Pointer?) {
            val b = buffer ?: return
            val bytes = ByteArray(w * h * 4)
            b.read(0, bytes, 0, bytes.size)
            frame.set(VideoFrame(w, h, bytes))
            SwingUtilities.invokeLater {
                frameTick++
                if (firstFramePending) { firstFramePending = false; listeners.toList().forEach { it.onRenderedFirstFrame() } }
            }
        }
    }

    /** Reads libVLC's state 10 times a second on the UI thread and turns changes into listener events. */
    private val poll = Timer(100) { pollState() }

    init {
        if (mp != null) {
            vlc!!.libvlc_video_set_format_callbacks(mp, setupCb, cleanupCb)
            vlc.libvlc_video_set_callbacks(mp, lockCb, null, displayCb, null)
            poll.start()
        }
    }

    private fun stateNow(): Int {
        val p = mp ?: return PState.IDLE
        if (!hasMedia) return PState.IDLE
        return when (vlc!!.libvlc_media_player_get_state(p)) {
            1, 2 -> PState.BUFFERING                 // opening, buffering
            3, 4 -> if (frame.get() == null && firstFramePending) PState.BUFFERING else PState.READY
            6 -> PState.ENDED
            else -> PState.IDLE                       // nothing, stopped, error
        }
    }

    private fun pollState() {
        val p = mp ?: return
        val s = stateNow()
        if (s != lastState) { lastState = s; listeners.toList().forEach { it.onPlaybackStateChanged(s) } }
        val playing = isPlaying
        if (playing != lastPlaying) { lastPlaying = playing; listeners.toList().forEach { it.onIsPlayingChanged(playing) } }
        if (hasMedia && vlc!!.libvlc_media_player_get_state(p) == 7 && !errorSent) {
            errorSent = true
            listeners.toList().forEach { it.onPlayerError(PError(null, "VLC_ERROR", PError.Kind.OTHER)) }
        }
        val n = tracks(TrackType.AUDIO).size * 100 + tracks(TrackType.TEXT).size
        if (n != lastTrackCount && s == PState.READY) { lastTrackCount = n; listeners.toList().forEach { it.onTracksChanged() } }
    }

    override var playWhenReady: Boolean
        get() = wantPlay
        set(v) {
            wantPlay = v
            val p = mp ?: return
            if (!hasMedia) return
            control.execute { vlc!!.libvlc_media_player_set_pause(p, if (v) 0 else 1) }
        }
    override val isPlaying: Boolean get() = mp != null && hasMedia && vlc!!.libvlc_media_player_get_state(mp) == 3 && wantPlay
    override val playbackState: Int get() = stateNow()
    override val currentPosition: Long get() = mp?.let { vlc!!.libvlc_media_player_get_time(it).coerceAtLeast(0) } ?: 0L
    override val duration: Long get() = mp?.let { vlc!!.libvlc_media_player_get_length(it).coerceAtLeast(0) } ?: 0L
    override val isCurrentMediaItemSeekable: Boolean get() = mp?.let { vlc!!.libvlc_media_player_is_seekable(it) != 0 } ?: false
    override fun seekTo(ms: Long) { val p = mp ?: return; control.execute { vlc!!.libvlc_media_player_set_time(p, ms) } }

    override fun addListener(l: PlayerListener) { listeners.add(l) }
    override fun removeListener(l: PlayerListener) { listeners.remove(l) }

    override fun setMedia(url: String, startMs: Long) {
        val p = mp
        if (p == null) {
            SwingUtilities.invokeLater {
                listeners.toList().forEach { it.onPlayerError(PError(null, VlcLoader.error ?: "NO_VIDEO_ENGINE", PError.Kind.OTHER)) }
            }
            return
        }
        hasMedia = true
        errorSent = false
        firstFramePending = true
        lastTrackCount = -1
        frame.set(null)
        val v = vlc!!
        control.execute {
            val m = v.libvlc_media_new_location(inst!!, url) ?: return@execute
            v.libvlc_media_add_option(m, ":http-user-agent=$USER_AGENT")
            // Like the TV: start soon (about 1 s of buffer for network streams).
            v.libvlc_media_add_option(m, ":network-caching=1000")
            v.libvlc_media_add_option(m, ":live-caching=1000")
            if (startMs > 0) v.libvlc_media_add_option(m, ":start-time=${startMs / 1000.0}")
            v.libvlc_media_player_set_media(p, m)
            v.libvlc_media_release(m)
            v.libvlc_media_player_play(p)
            if (!wantPlay) v.libvlc_media_player_set_pause(p, 1)
        }
    }

    override fun stop() {
        hasMedia = false
        frame.set(null)
        SwingUtilities.invokeLater { frameTick++ }
        val p = mp ?: return
        control.execute { vlc!!.libvlc_media_player_stop(p) }
    }

    override fun release() {
        poll.stop()
        val p = mp ?: return
        control.execute {
            runCatching { vlc!!.libvlc_media_player_stop(p); vlc.libvlc_media_player_release(p); inst?.let { vlc.libvlc_release(it) } }
        }
        control.shutdown()
    }

    // ---------- tracks ----------
    /** libVLC's list: {int i_id; char *psz_name; next}. Names look like "Track 1 - [English]". */
    private fun readList(head: Pointer?): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var p = head
        val ptr = Native.POINTER_SIZE.toLong()
        while (p != null) {
            val id = p.getInt(0)
            val name = p.getPointer(ptr)?.getString(0, "UTF-8") ?: ""
            out.add(id to name)
            p = p.getPointer(ptr * 2)
        }
        head?.let { vlc!!.libvlc_track_description_list_release(it) }
        return out
    }

    override fun tracks(type: Int): List<TrackOption> {
        val p = mp ?: return emptyList()
        if (!hasMedia) return emptyList()
        val v = vlc!!
        val (list, current) = if (type == TrackType.AUDIO)
            readList(v.libvlc_audio_get_track_description(p)) to v.libvlc_audio_get_track(p)
        else
            readList(v.libvlc_video_get_spu_description(p)) to v.libvlc_video_get_spu(p)
        return list.filter { it.first >= 0 }.mapIndexed { n, (id, name) ->
            val lang = Regex("\\[([^\\]]+)]").find(name)?.groupValues?.get(1)
            val extra = name.substringBefore(" - [").takeIf { lang == null }
            TrackOption(TrackHelper.label(lang, extra, 0, false, type, n), lang, id == current, id)
        }
    }

    override fun select(type: Int, option: TrackOption) {
        val p = mp ?: return
        val id = option.ref as? Int ?: return
        if (type == TrackType.AUDIO) vlc!!.libvlc_audio_set_track(p, id) else vlc!!.libvlc_video_set_spu(p, id)
    }

    override fun subtitlesOff() { mp?.let { vlc!!.libvlc_video_set_spu(it, -1) } }
    override fun subtitlesShowing(): Boolean = mp?.let { hasMedia && vlc!!.libvlc_video_get_spu(it) >= 0 } ?: false
    override fun resetTracks() { /* a new media starts with VLC's defaults; subtitles are turned off once tracks appear */ }
}
