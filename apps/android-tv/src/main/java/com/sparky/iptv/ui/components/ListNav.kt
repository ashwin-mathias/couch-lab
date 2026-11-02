package com.sparky.iptv.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.sparky.iptv.data.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lazy-list keys that stay the same when the list reloads, so focus stays on the same row.
 * An id that appears more than once gets "#2", "#3"… (only the repeats get a suffix).
 */
fun <T> stableKeys(items: List<T>, id: (T) -> String): List<String> {
    val seen = HashMap<String, Int>()
    return items.map {
        val k = id(it)
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        if (n == 1) k else "$k#$n"
    }
}

/**
 * Scrolls item [index] into view before it gets focus. Moving down keeps [contextPx] of space
 * below it (about two rows), moving up the same above it. Items that aren't composed yet are
 * scrolled to first, so focus never depends on a row that isn't on screen.
 */
suspend fun LazyListState.reveal(index: Int, down: Boolean, contextPx: Float) {
    val info = layoutInfo
    val top = info.viewportStartOffset.toFloat()
    val bottom = (info.viewportEndOffset - info.afterContentPadding).toFloat()
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        scrollToItem(index)
        // Put the row in the lower half when moving down, just below the top when moving up.
        scrollBy(if (down) -((bottom - top) / 2f) else -contextPx)
        return
    }
    if (down) {
        val need = item.offset + item.size + contextPx - bottom
        if (need > 0f) scrollBy(need)
    } else {
        val need = item.offset - contextPx - top
        if (need < 0f) scrollBy(need)
    }
}

/**
 * ▲ / ▼ for TV lists whose rows are mixed with headings that can't take focus
 * (provider dividers in the channel lists, region headings in the group lists).
 *
 * Compose's default focus search looks for the nearest focusable on screen in the direction of the
 * key. Across a heading, or when the next row isn't composed yet, it finds nothing directly below
 * and jumps somewhere else. So we move ourselves: find the next focusable row, scroll it into view,
 * then request focus on it, and log if focus lands anywhere else.
 */
class ListNav internal constructor(
    val state: LazyListState,
    private val scope: CoroutineScope,
    private val contextPx: Float,
    private val where: String,
) {
    private val requesters = HashMap<String, FocusRequester>()
    private var job: Job? = null

    /** Key of the row that last took focus. Rows set it from their onFocus. */
    var focusedKey: String? = null

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * Handles ▲ / ▼ pressed on row [from]. Returns false only for ▲ on the first focusable row,
     * so focus can leave the list upwards as before. ▼ on the last row does nothing.
     *
     * @param request asks the target row (by key) to take focus; defaults to the row's requester.
     * @param describe names a row for the log ("212 CNN"), by key.
     */
    fun step(
        from: Int,
        down: Boolean,
        size: Int,
        focusable: (Int) -> Boolean,
        keyAt: (Int) -> String,
        describe: (String?) -> String,
        request: (String) -> Unit = { k -> requesters[k]?.requestFocus() },
    ): Boolean {
        // While a move is still settling (a frame or two), extra repeats from a held key are dropped,
        // so holding ▼ walks row by row and never skips.
        if (job?.isActive == true) return true
        val dir = if (down) 1 else -1
        var j = from + dir
        while (j in 0 until size && !focusable(j)) j += dir
        if (j !in 0 until size) {
            if (down) return true
            // Top row: show the heading above it, and let ▲ continue to whatever is above the list.
            if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) {
                scope.launch { state.scrollToItem(0) }
            }
            return false
        }
        val key = keyAt(j)
        job = scope.launch {
            state.reveal(j, down, contextPx)
            var landed = false
            // The row may need a frame to be composed and placed after scrolling.
            for (attempt in 0 until 12) {
                runCatching { request(key) }
                withFrameNanos { }
                if (focusedKey == key) { landed = true; break }
            }
            if (!landed) AppLog.w("$where focus: expected row ${describe(key)}, got ${describe(focusedKey)}")
        }
        return true
    }
}

@Composable
fun rememberListNav(state: LazyListState, where: String, rowHeight: Dp, contextRows: Float = 2f): ListNav {
    val scope = rememberCoroutineScope()
    val px = with(LocalDensity.current) { (rowHeight * contextRows).toPx() }
    return remember(state) { ListNav(state, scope, px, where) }
}

/** Sends ▲ / ▼ key presses to [onMove] (down = true for ▼). [onMove] returns true when it used the key. */
fun Modifier.verticalKeys(onMove: (down: Boolean) -> Boolean): Modifier = onPreviewKeyEvent { ev ->
    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (ev.nativeKeyEvent.keyCode) {
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> onMove(true)
        android.view.KeyEvent.KEYCODE_DPAD_UP -> onMove(false)
        else -> false
    }
}
