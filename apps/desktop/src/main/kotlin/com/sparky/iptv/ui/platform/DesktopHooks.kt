package com.sparky.iptv.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import com.sparky.iptv.DesktopRes
import com.sparky.iptv.ui.components.AKey
import com.sparky.iptv.ui.components.TvKey
import com.sparky.iptv.ui.components.elapsedMs

/*
 * The desktop side of the shared screens' hooks (3.1). Android has the same functions.
 */

/** Keyboard keys as remote keys. */
fun Key.remoteCode(): Int? = when (this) {
    Key.DirectionUp -> AKey.KEYCODE_DPAD_UP
    Key.DirectionDown -> AKey.KEYCODE_DPAD_DOWN
    Key.DirectionLeft -> AKey.KEYCODE_DPAD_LEFT
    Key.DirectionRight -> AKey.KEYCODE_DPAD_RIGHT
    Key.Enter -> AKey.KEYCODE_ENTER
    Key.NumPadEnter -> AKey.KEYCODE_NUMPAD_ENTER
    Key.Spacebar -> AKey.KEYCODE_MEDIA_PLAY_PAUSE
    Key.Escape -> AKey.KEYCODE_BACK
    Key.PageUp -> AKey.KEYCODE_CHANNEL_UP
    Key.PageDown -> AKey.KEYCODE_CHANNEL_DOWN
    Key.M -> AKey.KEYCODE_MENU
    Key.L -> AKey.KEYCODE_LAST_CHANNEL
    Key.MediaPlayPause -> AKey.KEYCODE_MEDIA_PLAY_PAUSE
    Key.MediaRewind -> AKey.KEYCODE_MEDIA_REWIND
    Key.MediaFastForward -> AKey.KEYCODE_MEDIA_FAST_FORWARD
    Key.MediaNext -> AKey.KEYCODE_MEDIA_NEXT
    else -> null
}

/** Repeat counts and hold times, which the keyboard doesn't report: counted per key from its first press. */
private object KeyTracker {
    private val down = HashMap<Long, Pair<Long, Int>>()   // key → (down time, repeats)
    private var lastNative: Any? = null
    private var lastKey: TvKey? = null

    fun of(ev: KeyEvent): TvKey {
        // The same event reaches several handlers; count it once.
        val native = ev.nativeKeyEvent
        if (native != null && native === lastNative) lastKey?.let { return it }
        val code = ev.key.remoteCode() ?: (10_000 + (ev.key.keyCode and 0xFFFF).toInt())
        val now = elapsedMs()
        val k = ev.key.keyCode
        val tv = if (ev.type == KeyEventType.KeyDown) {
            val prev = down[k]
            val entry = if (prev == null) now to 0 else prev.first to prev.second + 1
            down[k] = entry
            TvKey(code, AKey.ACTION_DOWN, entry.second, now, entry.first)
        } else {
            val prev = down.remove(k)
            TvKey(code, AKey.ACTION_UP, 0, now, prev?.first ?: now)
        }
        lastNative = native
        lastKey = tv
        return tv
    }
}

fun KeyEvent.tv(): TvKey = KeyTracker.of(this)

/**
 * Back on the desktop (Esc or Backspace): the most recently added enabled handler runs, as on
 * Android. Main.kt calls [BackDispatcher.back].
 */
object BackDispatcher {
    class Entry(var enabled: Boolean, var onBack: () -> Unit)
    private val entries = ArrayList<Entry>()

    fun add(e: Entry) { entries.add(e) }
    fun remove(e: Entry) { entries.remove(e) }

    /** Returns true if a handler took it. */
    fun back(): Boolean {
        val e = entries.lastOrNull { it.enabled } ?: return false
        e.onBack()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry(enabled) { current.value() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(entry) {
        BackDispatcher.add(entry)
        onDispose { BackDispatcher.remove(entry) }
    }
}

/** An image from the app's resources (R.drawable.*), from the files copied out of the Android app. */
@Composable
fun painterRes(id: Int): Painter = painterResource(DesktopRes.paths[id] ?: error("No resource $id"))
