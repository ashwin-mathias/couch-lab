package com.sparky.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.R
import com.sparky.iptv.data.Channel
import com.sparky.iptv.data.Names
import com.sparky.iptv.data.Repository
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.GROUP_ACTION_ALL_REGIONS
import com.sparky.iptv.ui.GROUP_FAV
import com.sparky.iptv.ui.GROUP_RECENT
import com.sparky.iptv.ui.GROUP_FOLDER
import com.sparky.iptv.data.AppLog
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.sparky.iptv.ui.components.reveal
import kotlinx.coroutines.launch
import com.sparky.iptv.ui.GroupItem
import com.sparky.iptv.ui.GroupKind
import com.sparky.iptv.ui.components.Badge
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.ChannelLogo
import com.sparky.iptv.ui.components.ChannelTile
import com.sparky.iptv.ui.components.Clock
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Icon
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.PlayerSurface
import com.sparky.iptv.ui.components.ProgressBar
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtClock
import com.sparky.iptv.ui.components.fmtLeft
import com.sparky.iptv.ui.components.fmtShort
import com.sparky.iptv.ui.components.rememberNow
import com.sparky.iptv.ui.components.u
import com.sparky.iptv.ui.components.rememberListNav
import com.sparky.iptv.ui.components.stableKeys
import com.sparky.iptv.ui.components.verticalKeys
import kotlinx.coroutines.delay

/** Group list shared by Live TV and the guide, recomputed only when something it depends on changes. */
@Composable
fun liveGroups(vm: AppViewModel): List<GroupItem> =
    remember(vm.library, vm.favorites, vm.folders, vm.myRegions, vm.showAllRegions, vm.recents, vm.regionsClosed) { vm.groupItems() }

/** Lists where the tile/list toggle is offered. */
private fun toggleable(group: String) =
    group == GROUP_FAV || group == GROUP_RECENT || group.startsWith(GROUP_FOLDER)

/** ◀ handler for rows that hand focus back to the group list. */
fun Modifier.leftKey(onLeft: () -> Boolean): Modifier = onPreviewKeyEvent { ev ->
    ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && onLeft()
}

/**
 * Focus between the Groups pane and the channel pane. The channel list never takes focus on its
 * own: only ▶ / OK on a group, coming back from full screen, or opening Live TV move focus into it.
 */
class LiveFocus {
    /** On the channel the list starts on (last watched in this group, else the first). */
    val entry = FocusRequester()
    /** Set by the group list: focuses the selected group (or its region heading). */
    var focusSelectedGroup: () -> Unit = {}
}

@Composable
fun LiveScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val groups = liveGroups(vm)
    val group = vm.liveGroup
    val channels = remember(vm.library, vm.favorites, vm.favOrder, vm.folders, vm.moving, group, vm.myRegions, vm.showAllRegions, vm.recents) {
        vm.channelsFor(group)
    }
    var focused by remember { mutableStateOf<Channel?>(null) }
    val previewCh = focused ?: channels.firstOrNull { !it.divider }
    val tiles = toggleable(group) && vm.isTiles(group)
    val lf = remember { LiveFocus() }
    val scope = rememberCoroutineScope()

    PreviewDriver(vm, focused)

    /** Moves focus into the channel list, once it's composed. Returns false if there's nothing to focus. */
    suspend fun focusChannels(): Boolean {
        repeat(15) {
            withFrameNanos { }
            if (runCatching { lf.entry.requestFocus() }.isSuccess) return true
        }
        return false
    }
    fun enterChannels(id: String) {
        vm.liveGroup = id
        scope.launch { if (!focusChannels()) AppLog.w("Live TV: no channel to focus in ${groups.firstOrNull { it.id == id }?.name}") }
    }
    // Opening Live TV (or Back from full screen): start on the channel watched last.
    LaunchedEffect(Unit) {
        delay(150)
        if (!focusChannels()) lf.focusSelectedGroup()
    }

    Row(Modifier.fillMaxSize()) {
        GroupList(vm, groups, group, lf, onEnter = { enterChannels(it) }, modifier = Modifier.width(270.u))
        Box(Modifier.width(1.u).fillMaxHeight().background(C.Line))
        Column(Modifier.width(470.u).fillMaxHeight().padding(top = 28.u, start = 12.u, end = 12.u)) {
            Row(Modifier.padding(start = 10.u, end = 4.u, top = 2.u, bottom = 10.u).height(36.u), verticalAlignment = Alignment.CenterVertically) {
                Label(groups.firstOrNull { it.id == group }?.name ?: "Channels", modifier = Modifier.weight(1f))
                if (toggleable(group) && channels.isNotEmpty()) {
                    FocusItem(
                        onClick = { vm.setTiles(group, !tiles) },
                        container = C.Panel,
                        shape = RoundedCornerShape(999.u),
                        modifier = Modifier.height(34.u).leftKey { lf.focusSelectedGroup(); true },
                    ) { f ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 12.u), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (tiles) R.drawable.ic_list else R.drawable.ic_tiles, tint = if (f) C.OnFocus else C.Text2, size = 15.u)
                            Spacer(Modifier.width(6.u))
                            T(if (tiles) "List" else "Tiles", size = 12, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text2)
                        }
                    }
                } else {
                    T("Hold OK to ♥", size = 12, color = C.Dim)
                }
            }
            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.u)) {
                    T(
                        when {
                            group == GROUP_FAV -> "No favorites yet. Hold OK on any channel to add it here."
                            group.startsWith(GROUP_FOLDER) -> "No channels in this folder yet. Hold OK on a favorite channel and choose Folders…"
                            group == GROUP_RECENT -> "Channels you watch will appear here."
                            else -> "No channels in this group."
                        },
                        size = 15, color = C.Muted, maxLines = 3,
                    )
                }
            } else {
                // A new list (and scroll position) per group, so it opens at its own start channel.
                key(group, tiles) {
                    val onLeft = { lf.focusSelectedGroup(); true }
                    if (tiles) TileGrid(vm, channels, vm.moveListFor(group), lf.entry, onLeft, onFocus = { focused = it })
                    else ChannelList(vm, channels, vm.moveListFor(group), lf.entry, onLeft, onFocus = { focused = it })
                }
            }
        }
        Box(Modifier.width(1.u).fillMaxHeight().background(C.Line))
        ChannelPreviewPane(vm, previewCh, hasFocus = focused != null, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GroupList(
    vm: AppViewModel, groups: List<GroupItem>, selected: String, lf: LiveFocus,
    onEnter: (String) -> Unit, modifier: Modifier,
) {
    val selIndex = remember(groups, selected) { groups.indexOfFirst { it.id == selected }.coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selIndex - 3).coerceAtLeast(0))
    val keys = remember(groups) { stableKeys(groups) { it.id } }
    val scope = rememberCoroutineScope()
    // ▲ / ▼ move row by row (headings included), scrolling first so focus never jumps (see ListNav).
    val nav = rememberListNav(state, "Groups", rowHeight = 46.u)
    fun move(i: Int, down: Boolean) = nav.step(
        from = i, down = down, size = groups.size,
        focusable = { true },
        keyAt = { keys[it] },
        describe = { k -> groups.getOrNull(keys.indexOf(k))?.name ?: "none" },
    )
    // The channel pane follows the focused group once the remote settles, without moving focus.
    var settle by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun preview(id: String) {
        settle?.cancel()
        settle = scope.launch { delay(300); vm.liveGroup = id }
    }
    fun enter(id: String) { settle?.cancel(); onEnter(id) }
    lf.focusSelectedGroup = {
        scope.launch {
            val region = vm.regionOfGroup(vm.liveGroup)
            var i = groups.indexOfFirst { it.id == vm.liveGroup }
            if (i < 0 && region != null) i = groups.indexOfFirst { it.id == "hdr:$region" }
            if (i < 0) i = 0
            if (i in keys.indices) {
                state.reveal(i, down = true, contextPx = 0f)
                repeat(12) {
                    runCatching { nav.requester(keys[i]).requestFocus() }
                    withFrameNanos { }
                    if (nav.focusedKey == keys[i]) return@launch
                }
            }
        }
    }
    Column(modifier.fillMaxHeight().padding(top = 28.u, start = 20.u, end = 16.u)) {
        Label("Groups", modifier = Modifier.padding(start = 10.u, top = 6.u, bottom = 14.u))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.u), contentPadding = PaddingValues(bottom = 28.u)) {
            itemsIndexed(groups, key = { idx, _ -> keys[idx] }) { i, g ->
                val key = keys[i]
                val navMod = Modifier.focusRequester(nav.requester(key)).verticalKeys { down -> move(i, down) }
                when (g.kind) {
                    GroupKind.HEADER -> RegionHeading(
                        g,
                        selected = !g.open && vm.regionOfGroup(selected) == g.region,
                        onFocus = { nav.focusedKey = key },
                        onToggle = { g.region?.let { vm.toggleRegionOpen(it) } },
                        onRight = {
                            if (!g.open) g.region?.let { vm.toggleRegionOpen(it) } else move(i, true)
                            true
                        },
                        modifier = navMod.padding(top = 10.u),
                    )
                    GroupKind.ACTION -> FocusItem(
                        onClick = {
                            if (g.id == GROUP_ACTION_ALL_REGIONS) vm.showAllRegions = !vm.showAllRegions
                        },
                        onFocus = { nav.focusedKey = key },
                        container = C.Panel,
                        shape = RoundedCornerShape(10.u),
                        modifier = navMod.fillMaxWidth().height(42.u),
                    ) { f ->
                        T(
                            g.name, size = 14, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text2,
                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.u),
                        )
                    }
                    GroupKind.GROUP -> {
                        val isSel = g.id == selected
                        FocusItem(
                            onClick = { enter(g.id) },
                            onFocus = { nav.focusedKey = key; preview(g.id) },
                            selected = isSel,
                            shape = RoundedCornerShape(10.u),
                            modifier = navMod
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                                        enter(g.id); true
                                    } else false
                                }
                                .padding(start = if (g.indent) 14.u else 0.u)
                                .fillMaxWidth().height(42.u),
                        ) { f ->
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 12.u),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(
                                    g.name, size = if (g.indent) 14 else 15,
                                    weight = if (isSel || f) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (f) C.OnFocus else if (isSel) C.Text else C.Muted,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.u))
                                T(Repository.fmt(g.count), size = 13, color = if (f) C.OnFocusMuted else C.Dim)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A region heading in a group list: ▾ open / ▸ closed. OK opens or closes it; ▶ opens a closed
 * heading, or on an open one moves to its first group ([onRight] decides).
 */
@Composable
fun RegionHeading(
    g: GroupItem, selected: Boolean, onFocus: () -> Unit, onToggle: () -> Unit, onRight: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    FocusItem(
        onClick = onToggle,
        onFocus = onFocus,
        selected = selected,
        shape = RoundedCornerShape(10.u),
        modifier = modifier
            .onPreviewKeyEvent { ev ->
                ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && onRight()
            }
            .fillMaxWidth().height(42.u),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.u), verticalAlignment = Alignment.CenterVertically) {
            T(if (g.open) "▾" else "▸", size = 14, color = if (f) C.OnFocus else C.Text2, modifier = Modifier.width(18.u))
            Label(g.name, color = if (f) C.OnFocus else C.Text2, modifier = Modifier.weight(1f))
            T(Repository.fmt(g.count), size = 11, color = if (f) C.OnFocusMuted else C.Dim)
        }
    }
}

@Composable
private fun ChannelList(
    vm: AppViewModel, channels: List<Channel>, moveList: String?, entry: FocusRequester,
    onLeft: () -> Boolean, onFocus: (Channel) -> Unit,
) {
    val now = rememberNow()
    // Start on the channel watched last, so coming back from full screen feels natural.
    val startIndex = remember(channels) {
        val i = channels.indexOfFirst { it.id == vm.prefs.lastChannel && !it.divider }
        if (i >= 0) i else channels.indexOfFirst { !it.divider }.coerceAtLeast(0)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 3).coerceAtLeast(0))
    val keys = remember(channels) { stableKeys(channels) { it.id } }
    // ▲ / ▼ move channel by channel, skipping the provider's divider headings (see ListNav).
    val nav = rememberListNav(state, "Channel list", rowHeight = 68.u)
    fun move(i: Int, down: Boolean) = nav.step(
        from = i, down = down, size = channels.size,
        focusable = { !channels[it].divider },
        keyAt = { keys[it] },
        describe = { k -> channels.getOrNull(keys.indexOf(k))?.let { "${it.num} ${it.display}" } ?: "none" },
    )
    FollowMove(vm, moveList, state) { k -> channels.indexOfFirst { it.favKey == k } }
    LazyColumn(
        state = state,
        verticalArrangement = Arrangement.spacedBy(4.u),
        contentPadding = PaddingValues(bottom = 28.u),
    ) {
        itemsIndexed(channels, key = { idx, _ -> keys[idx] }) { i, ch ->
            if (ch.divider) {
                Row(Modifier.fillMaxWidth().padding(start = 14.u, top = 16.u, bottom = 6.u), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(18.u).height(1.u).background(C.Line2))
                    Spacer(Modifier.width(10.u))
                    Label(Names.dividerTitle(ch.name), color = C.Text2)
                    Spacer(Modifier.width(10.u))
                    Box(Modifier.weight(1f).height(1.u).background(C.Line2))
                }
                return@itemsIndexed
            }
            LaunchedEffect(ch.id) { vm.ensureEpg(ch) }
            val (cur, _) = vm.nowNext(ch.id, now)
            val fav = ch.favKey in vm.favorites
            val (badge, title) = Names.event(ch.display)
            FocusItem(
                onClick = { vm.playLive(channels, i) },
                onLongClick = { vm.holdFavorite(ch.favKey, ch.display, moveList, upDown = 1) },
                onFocus = { nav.focusedKey = keys[i]; onFocus(ch) },
                modifier = Modifier
                    .focusRequester(nav.requester(keys[i]))
                    .verticalKeys { down -> move(i, down) }
                    .leftKey(onLeft)
                    .movingOutline(vm.moving?.key == ch.favKey)
                    .fillMaxWidth()
                    .height(64.u)
                    .let { if (i == startIndex) it.focusRequester(entry) else it },
            ) { f ->
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 14.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(ch.num.toString(), size = 13, weight = FontWeight.SemiBold, color = if (f) C.OnFocusMuted else C.Dim, modifier = Modifier.width(46.u))
                    ChannelLogo(ch.display, ch.logo, 40.u, focused = f)
                    Spacer(Modifier.width(14.u))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.u)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (badge != null) {
                                Badge(badge, focused = f)
                                Spacer(Modifier.width(6.u))
                            }
                            T(title, size = 15, weight = if (f) FontWeight.Bold else FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text, modifier = Modifier.weight(1f))
                            if (fav) Icon(R.drawable.ic_star, tint = if (f) C.OnFocus else C.Text2, size = 13.u)
                            if (cur != null) {
                                Spacer(Modifier.width(8.u))
                                T(fmtLeft(cur.end, now), size = 12, color = if (f) C.OnFocusMuted else C.Dim)
                            }
                        }
                        if (cur != null) {
                            T(cur.title, size = 13, color = if (f) C.OnFocusMuted else C.Muted)
                            val frac = (now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)
                            ProgressBar(frac, track = if (f) C.TrackOnFocus else C.Line, fill = if (f) C.OnFocus else C.Dim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileGrid(
    vm: AppViewModel, channels: List<Channel>, moveList: String?, entry: FocusRequester,
    onLeft: () -> Boolean, onFocus: (Channel) -> Unit,
) {
    val now = rememberNow()
    val playable = remember(channels) { channels.filter { !it.divider } }
    val start = remember(playable) { playable.indexOfFirst { it.id == vm.prefs.lastChannel }.coerceAtLeast(0) }
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = start)
    FollowMoveGrid(vm, moveList, state) { k -> playable.indexOfFirst { it.favKey == k } }
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(14.u),
        verticalArrangement = Arrangement.spacedBy(18.u),
        contentPadding = PaddingValues(start = 6.u, end = 6.u, top = 6.u, bottom = 28.u),
    ) {
        items(playable.size, key = { playable[it].favKey }) { i ->
            val ch = playable[i]
            ChannelTile(
                vm, ch, now, width = null,
                onClick = { vm.playLive(playable, i) },
                onLongClick = { vm.holdFavorite(ch.favKey, ch.display, moveList, upDown = 3, leftRight = 1) },
                modifier = Modifier
                    .let { if (i == start) it.focusRequester(entry) else it }
                    .let { if (i % 3 == 0) it.leftKey(onLeft) else it }
                    .movingOutline(vm.moving?.key == ch.favKey)
                    .onFocusChanged { if (it.hasFocus) onFocus(ch) },
            )
        }
    }
}
