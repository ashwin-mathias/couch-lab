package com.sparky.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.BackHandler
import com.sparky.iptv.R
import com.sparky.iptv.data.Repository
import com.sparky.iptv.data.Names
import com.sparky.iptv.ui.GroupKind
import com.sparky.iptv.ui.GROUP_ALL
import com.sparky.iptv.ui.GROUP_FAV
import com.sparky.iptv.ui.components.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.sparky.iptv.ui.components.rememberListNav
import com.sparky.iptv.ui.components.stableKeys
import com.sparky.iptv.ui.components.verticalKeys
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sparky.iptv.data.Channel
import com.sparky.iptv.data.EpgEntry
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.ChannelLogo
import com.sparky.iptv.ui.components.Clock
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtClock
import com.sparky.iptv.ui.components.fmtDay
import com.sparky.iptv.ui.components.fmtLeft
import com.sparky.iptv.ui.components.fmtShort
import com.sparky.iptv.ui.components.rememberNow
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

private const val WINDOW_MIN = 150
private const val HALF_HOUR = 30 * 60_000L

@Composable
fun GuideScreen(vm: AppViewModel) {
    var picking by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        GuideContent(vm, onPickGroup = { picking = true })
        if (picking) {
            GroupPicker(vm, onDone = { picking = false })
        }
    }
}

/** Full list of channel groups. Choosing one also changes the group shown in Live TV. */
@Composable
private fun GroupPicker(vm: AppViewModel, onDone: () -> Unit) {
    BackHandler { onDone() }
    val groups = liveGroups(vm)
    val keys = remember(groups) { stableKeys(groups) { it.id } }
    val selIndex = groups.indexOfFirst { it.id == vm.liveGroup }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selIndex - 4).coerceAtLeast(0))
    // ▲ / ▼ move between groups (and the "Show all regions" action), skipping region headings.
    val nav = rememberListNav(state, "Group picker", rowHeight = 50.u)
    fun move(i: Int, down: Boolean) = nav.step(
        from = i, down = down, size = groups.size,
        focusable = { groups[it].kind != GroupKind.HEADER },
        keyAt = { keys[it] },
        describe = { k -> groups.getOrNull(keys.indexOf(k))?.name ?: "none" },
    )
    LaunchedEffect(Unit) { delay(120); runCatching { nav.requester(keys[selIndex]).requestFocus() } }
    Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.7f))) {
        Column(
            Modifier.fillMaxHeight().width(420.u).background(C.Panel).padding(horizontal = 20.u, vertical = 28.u),
            verticalArrangement = Arrangement.spacedBy(12.u),
        ) {
            T("Choose a group", size = 22, weight = FontWeight.Bold)
            T("${groups.count { it.kind == GroupKind.GROUP } - 3} groups · Back to close", size = 13, color = C.Dim)
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.u), contentPadding = PaddingValues(bottom = 24.u)) {
                itemsIndexed(groups, key = { idx, _ -> keys[idx] }) { i, g ->
                    if (g.kind == GroupKind.HEADER) {
                        Label(g.name, color = C.Text2, modifier = Modifier.padding(start = 14.u, top = 16.u, bottom = 4.u))
                        return@itemsIndexed
                    }
                    val key = keys[i]
                    val rowMod = Modifier.fillMaxWidth().height(46.u)
                        .focusRequester(nav.requester(key))
                        .verticalKeys { down -> move(i, down) }
                    if (g.kind == GroupKind.ACTION) {
                        FocusItem(
                            onClick = { vm.showAllRegions = !vm.showAllRegions },
                            onFocus = { nav.focusedKey = key },
                            container = C.Raised,
                            shape = RoundedCornerShape(10.u),
                            modifier = rowMod,
                        ) { f ->
                            T(g.name, size = 14, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text2,
                                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.u))
                        }
                        return@itemsIndexed
                    }
                    val isSel = g.id == vm.liveGroup
                    FocusItem(
                        onClick = { vm.liveGroup = g.id; onDone() },
                        onFocus = { nav.focusedKey = key },
                        selected = isSel,
                        shape = RoundedCornerShape(10.u),
                        modifier = rowMod,
                    ) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.u), verticalAlignment = Alignment.CenterVertically) {
                            T(
                                g.name, size = 15, weight = if (isSel || f) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (f) C.OnFocus else if (isSel) C.Text else C.Text2,
                                modifier = Modifier.weight(1f),
                            )
                            T(Repository.fmt(g.count), size = 13, color = if (f) C.OnFocusMuted else C.Dim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideContent(vm: AppViewModel, onPickGroup: () -> Unit) {
    val groups = liveGroups(vm)
    val group = vm.liveGroup
    val channels = remember(vm.library, vm.favorites, group) { vm.channelsFor(group) }
    val now = rememberNow()
    // Window position in half-hour steps back from the current half hour (0 = now; negative = the past).
    var slots by remember { mutableIntStateOf(0) }
    val winStart = now - now % HALF_HOUR + slots * HALF_HOUR
    val winEnd = winStart + WINDOW_MIN * 60_000L
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    var focusedProg by remember { mutableStateOf<EpgEntry?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Stable row keys (the channel id), so a row keeps focus when its guide data arrives.
    val keys = remember(channels) { stableKeys(channels) { it.id } }
    val listState = rememberLazyListState()
    val nav = rememberListNav(listState, "Guide", rowHeight = 56.u)
    // Each composed row registers how to focus its cell at a given time; returns that cell's start.
    val rowCells = remember { HashMap<String, (Long?) -> Long?>() }
    // The time ▲ / ▼ keep: (row key, start of the focused cell, time to keep).
    // Holding ▼ through rows with different show times stays at the same time instead of drifting.
    val anchor = remember { arrayOfNulls<Triple<String, Long, Long>>(1) }
    LaunchedEffect(group) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
    // After the window moves, put focus back on the same channel's row.
    var moved by remember { mutableIntStateOf(0) }
    LaunchedEffect(moved) {
        if (moved == 0) return@LaunchedEffect
        delay(120)
        nav.focusedKey?.let { k -> runCatching { rowCells[k]?.invoke(null) } }
    }
    /** ◀ on a row's first show moves 90 min back, on catch-up channels and within their catch-up days. */
    fun moveBack(ch: Channel): Boolean {
        if (ch.archiveDays <= 0 || vm.prefs.playlist?.type != "xtream") return false
        val next = slots - 3
        val earliest = now - ch.archiveDays * 24 * 3_600_000L - HALF_HOUR
        if (now - now % HALF_HOUR + next * HALF_HOUR < earliest) return false
        slots = next
        moved++
        return true
    }
    fun moveForward(): Boolean {
        if (slots >= 0) return false
        slots = (slots + 3).coerceAtMost(0)
        moved++
        return true
    }
    /**
     * ▲ / ▼ on a cell of row [i] that starts at [cellStart]: go to the next playable row (skipping
     * divider headings), to the cell showing the same time.
     */
    fun moveRow(i: Int, down: Boolean, cellStart: Long): Boolean {
        val a = anchor[0]
        val t = (if (a != null && a.first == keys[i] && a.second == cellStart) a.third else cellStart)
            .coerceIn(winStart, winEnd - 1)
        return nav.step(
            from = i, down = down, size = channels.size,
            focusable = { !channels[it].divider },
            keyAt = { keys[it] },
            describe = { k -> channels.getOrNull(keys.indexOf(k))?.let { "${it.num} ${it.display}" } ?: "none" },
            request = { k ->
                val start = rowCells[k]?.invoke(t)
                if (start != null) anchor[0] = Triple(k, start, t)
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.u, vertical = 28.u), verticalArrangement = Arrangement.spacedBy(18.u)) {
        // Details of whatever is highlighted
        Row(Modifier.fillMaxWidth().height(150.u)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.u)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.u)) {
                    Label("Guide")
                    Label(fmtDay(if (slots < 0) winStart else now), color = C.Text2)
                }
                val ch = focusedCh
                val p = focusedProg
                T(p?.title ?: ch?.display ?: "TV guide", size = 30, weight = FontWeight.Bold)
                if (ch != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.u)) {
                        T("${ch.num} · ${ch.display}", size = 14, color = C.Text2)
                        if (p != null) {
                            when {
                                p.end <= now -> {
                                    T("Aired ${fmtShort(p.start)} – ${fmtClock(p.end)}", size = 14, color = C.Dim)
                                    if (vm.canReplay(ch, p, now)) T("Replay available · OK to watch", size = 14, color = C.Text2)
                                    else T("Not available to replay", size = 14, color = C.Dim)
                                }
                                else -> {
                                    T("${fmtShort(p.start)} – ${fmtClock(p.end)}", size = 14, color = C.Dim)
                                    if (now in p.start until p.end) T(fmtLeft(p.end, now), size = 14, color = C.Dim)
                                }
                            }
                        }
                    }
                }
                if (!p?.description.isNullOrBlank()) {
                    T(p!!.description, size = 14, color = C.Muted, maxLines = 2, lineHeight = 1.6f, modifier = Modifier.padding(end = 40.u))
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.u)) {
                Clock()
                Row(horizontalArrangement = Arrangement.spacedBy(10.u)) {
                    if (slots < 0) {
                        FocusItem(
                            onClick = { slots = 0; moved++ },
                            container = C.Raised,
                            shape = RoundedCornerShape(999.u),
                            modifier = Modifier.height(40.u),
                        ) { f ->
                            T("Now", size = 14, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text,
                                modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.u))
                        }
                    }
                    val favOn = group == GROUP_FAV
                    FocusItem(
                        onClick = { vm.liveGroup = if (favOn) GROUP_ALL else GROUP_FAV },
                        selected = favOn,
                        container = C.Panel,
                        shape = RoundedCornerShape(999.u),
                        modifier = Modifier.height(40.u),
                    ) { f ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (favOn) R.drawable.ic_star else R.drawable.ic_star_outline, tint = if (f) C.OnFocus else C.Text, size = 16.u)
                            Spacer(Modifier.width(8.u))
                            T("Favorites", size = 14, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text)
                        }
                    }
                    FocusItem(
                        onClick = onPickGroup,
                        container = C.Panel,
                        shape = RoundedCornerShape(999.u),
                        modifier = Modifier.height(40.u).widthIn(max = 300.u),
                    ) { f ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
                            T("Group: ", size = 14, color = if (f) C.OnFocusMuted else C.Dim)
                            T(groups.firstOrNull { it.id == group }?.name ?: "All channels", size = 14, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text)
                            T("  ▾", size = 14, color = if (f) C.OnFocus else C.Dim)
                        }
                    }
                }
            }
        }

        if (channels.isEmpty()) {
            T(
                if (group == GROUP_FAV) "No favorite channels yet. Hold OK on a channel to add it, or choose another group."
                else "No channels in this group. Choose another group above.",
                size = 15, color = C.Muted, maxLines = 2,
            )
            return@Column
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val chanCol = 224.u
            val perMin: Dp = (maxWidth - chanCol) / WINDOW_MIN
            Column {
                // Time ruler
                Row(Modifier.height(32.u), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(chanCol))
                    for (k in 0 until WINDOW_MIN / 30) {
                        T(fmtClock(winStart + k * HALF_HOUR), size = 13, weight = FontWeight.SemiBold, color = C.Muted, modifier = Modifier.width(perMin * 30))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.u).background(C.Line))
                Spacer(Modifier.height(4.u))
                val firstPlayable = channels.indexOfFirst { !it.divider }
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.u), contentPadding = PaddingValues(bottom = 24.u)) {
                    itemsIndexed(channels, key = { idx, _ -> keys[idx] }) { i, ch ->
                        if (ch.divider) {
                            Row(Modifier.padding(top = 12.u, bottom = 4.u), verticalAlignment = Alignment.CenterVertically) {
                                Label(Names.dividerTitle(ch.name), color = C.Text2)
                            }
                            return@itemsIndexed
                        }
                        val rowKey = keys[i]
                        GuideRow(
                            vm, ch, rowKey, rowCells, now, winStart, winEnd, perMin, chanCol,
                            onPlay = { e ->
                                when {
                                    e == null || e.end > now -> vm.playLive(channels, i)
                                    vm.canReplay(ch, e, now) -> vm.playCatchup(ch, e)
                                    else -> vm.toast = "This show can't be replayed"
                                }
                            },
                            onFocus = { e -> focusedCh = ch; focusedProg = e; nav.focusedKey = rowKey },
                            firstFocus = if (i == firstPlayable) firstFocus else null,
                            onEdgeLeft = { moveBack(ch) },
                            onEdgeRight = { moveForward() },
                            onVertical = { down, cellStart -> moveRow(i, down, cellStart) },
                        )
                    }
                }
            }
            // "Now" line, when now is inside the window.
            if (now in winStart until winEnd) {
                val x = chanCol + perMin * ((now - winStart) / 60_000f)
                Box(Modifier.offset(x = x).padding(top = 26.u).width(2.u).fillMaxHeight().background(C.Accent))
                Box(
                    Modifier.offset(x = x - 24.u).clip(RoundedCornerShape(6.u)).background(C.Accent).padding(horizontal = 8.u, vertical = 2.u)
                ) {
                    T(fmtShort(now), size = 11, weight = FontWeight.Bold, color = C.OnFocus)
                }
            }
        }
    }
}

/** One cell of a guide row: a show, or the whole window while there's no guide data. */
private class Cell(
    val title: String,
    val sub: String,
    val start: Long,     // clamped to the window
    val end: Long,
    val width: Dp,
    val live: Boolean,
    val dim: Boolean,
    val entry: EpgEntry?,
)

@Composable
private fun GuideRow(
    vm: AppViewModel,
    ch: Channel,
    rowKey: String,
    rowCells: HashMap<String, (Long?) -> Long?>,
    now: Long,
    winStart: Long,
    winEnd: Long,
    perMin: Dp,
    chanCol: Dp,
    onPlay: (EpgEntry?) -> Unit,
    onFocus: (EpgEntry?) -> Unit,
    firstFocus: FocusRequester?,
    onEdgeLeft: () -> Boolean,
    onEdgeRight: () -> Boolean,
    onVertical: (down: Boolean, cellStart: Long) -> Boolean,
) {
    LaunchedEffect(ch.id) { vm.ensureEpg(ch) }
    // Looking back in time: load past shows for catch-up channels.
    val past = winStart < now - HALF_HOUR
    LaunchedEffect(ch.id, past) { if (past) vm.ensureFullEpg(ch) }
    val entries = vm.epg[ch.id].orEmpty().filter { it.end > winStart && it.start < winEnd }
    // Channels without guide data get a slimmer row so the guide stays compact.
    val compact = entries.isEmpty() && vm.epg.containsKey(ch.id)
    val cells = if (entries.isEmpty()) {
        listOf(
            Cell(
                title = if (compact) "${ch.display}  ·  no guide information" else "Loading…",
                sub = "", start = winStart, end = winEnd, width = perMin * WINDOW_MIN - 4.u,
                live = true, dim = false, entry = null,
            )
        )
    } else {
        entries.map { e ->
            val s = maxOf(e.start, winStart)
            val en = minOf(e.end, winEnd)
            val isPast = e.end <= now
            val replay = isPast && vm.canReplay(ch, e, now)
            Cell(
                title = (if (replay) "↺  " else "") + e.title.ifBlank { ch.display },
                sub = "${fmtShort(e.start)} – ${fmtShort(e.end)}",
                start = s, end = en,
                width = (perMin * ((en - s) / 60_000f) - 4.u).coerceAtLeast(24.dp),
                live = now in e.start until e.end,
                dim = isPast && !replay,
                entry = e,
            )
        }
    }
    // One requester per cell position, kept across guide updates.
    val reqs = remember { ArrayList<FocusRequester>() }
    while (reqs.size < cells.size) reqs.add(FocusRequester())
    // Let the guide focus this row at a time: the cell covering it, else the nearest one.
    val latest = rememberUpdatedState(cells)
    DisposableEffect(rowKey) {
        val fn: (Long?) -> Long? = { t ->
            val cs = latest.value
            val idx = if (t == null) 0 else cs.indexOfFirst { t >= it.start && t < it.end }.takeIf { it >= 0 }
                ?: cs.indices.minByOrNull { i -> if (t < cs[i].start) cs[i].start - t else t - cs[i].end }
                ?: 0
            reqs[idx].requestFocus()
            cs[idx].start
        }
        rowCells[rowKey] = fn
        onDispose { if (rowCells[rowKey] === fn) rowCells.remove(rowKey) }
    }
    Row(Modifier.height(if (compact) 38.u else 52.u), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(chanCol), verticalAlignment = Alignment.CenterVertically) {
            T(ch.num.toString(), size = 12, color = C.Dim, modifier = Modifier.width(44.u))
            ChannelLogo(ch.display, ch.logo, if (compact) 28.u else 34.u)
            Spacer(Modifier.width(12.u))
            T(ch.display, size = 14, weight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.u))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.u)) {
            // The "Loading…" cell and the first show are the same composable, so the row keeps
            // focus when its guide data arrives.
            cells.forEachIndexed { idx, c ->
                ProgramCell(
                    title = c.title,
                    sub = c.sub,
                    width = c.width,
                    live = c.live,
                    dim = c.dim,
                    onClick = { onPlay(c.entry) },
                    onFocus = { onFocus(c.entry) },
                    modifier = Modifier
                        .focusRequester(reqs[idx])
                        .let { if (idx == 0 && firstFocus != null) it.focusRequester(firstFocus) else it }
                        .cellKeys(idx == 0, idx == cells.lastIndex, onEdgeLeft, onEdgeRight) { down -> onVertical(down, c.start) },
                    height = if (compact) 34.u else 46.u,
                )
            }
        }
    }
}

/**
 * ◀ on a row's first cell and ▶ on its last cell move the guide window in time.
 * ▲ / ▼ always go to the next playable row (never left to the default focus search).
 */
private fun Modifier.cellKeys(
    first: Boolean,
    last: Boolean,
    onLeft: () -> Boolean,
    onRight: () -> Boolean,
    onVertical: (down: Boolean) -> Boolean,
): Modifier = onPreviewKeyEvent { ev ->
    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (ev.nativeKeyEvent.keyCode) {
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> first && onLeft()
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> last && onRight()
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> onVertical(true)
        android.view.KeyEvent.KEYCODE_DPAD_UP -> onVertical(false)
        else -> false
    }
}

@Composable
private fun ProgramCell(
    title: String,
    sub: String,
    width: Dp,
    live: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.u,
    dim: Boolean = false,
) {
    FocusItem(
        onClick = onClick,
        onFocus = onFocus,
        container = if (live) C.Raised else C.Panel,
        shape = RoundedCornerShape(8.u),
        modifier = modifier.width(width).height(height),
    ) { f ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.u),
            verticalArrangement = Arrangement.Center,
        ) {
            T(title, size = 14, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else if (dim) C.Dim else C.Text)
            if (sub.isNotEmpty()) T(sub, size = 11, color = if (f) C.OnFocusMuted else C.Dim)
        }
    }
}
