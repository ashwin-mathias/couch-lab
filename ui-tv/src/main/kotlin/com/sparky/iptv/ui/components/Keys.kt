package com.sparky.iptv.ui.components

/**
 * Remote keys, with Android's key codes (3.1: shared, so the desktop maps its keyboard onto the
 * same codes: arrows = D-pad, Enter = OK, Esc/Backspace = Back, Space = play/pause).
 */
object AKey {
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val KEYCODE_BACK = 4
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_ENTER = 66
    const val KEYCODE_MENU = 82
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_REWIND = 89
    const val KEYCODE_MEDIA_FAST_FORWARD = 90
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127
    const val KEYCODE_NUMPAD_ENTER = 160
    const val KEYCODE_CHANNEL_UP = 166
    const val KEYCODE_CHANNEL_DOWN = 167
    const val KEYCODE_CAPTIONS = 175
    const val KEYCODE_LAST_CHANNEL = 229
}

/**
 * One key event in remote terms: [keyCode] from [AKey], how many times it has auto-repeated, and
 * when it went down (ms), so "hold OK" works the same everywhere.
 */
class TvKey(val keyCode: Int, val action: Int, val repeatCount: Int, val eventTime: Long, val downTime: Long)

/** Milliseconds since an arbitrary start, for measuring durations (like SystemClock.elapsedRealtime). */
fun elapsedMs(): Long = System.nanoTime() / 1_000_000
