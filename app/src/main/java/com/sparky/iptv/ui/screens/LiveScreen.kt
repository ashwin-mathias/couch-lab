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
    remember(vm.library, vm.favorites, vm.myRegions, vm.showAllRegions, vm.recents) { vm.groupItems() }

/** Lists where the tile/list toggle is offered. */
private fun toggleable(group: String) = group == GROUP_FAV || group == GROUP_RECENT

@Composable
fun LiveScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val groups = liveGroups(vm)
    val group = vm.liveGroup
    val channels = remember(vm.library, vm.favorites, group, vm.myRegions, vm.showAllRegions, vm.recents) { vm.channelsFor(group) }
    var focused by remember { mutableStateOf<Channel?>(null) }
    val previewCh = focused ?: channels.firstOrNull { !it.divider }
    val tiles = toggleable(group) && vm.isTiles(group)

    PreviewDriver(vm, focused)

    Row(Modifier.fillMaxSize()) {
        GroupList(vm, groups, group, modifier = Modifier.width(270.u))
        Box(Modifier.width(1.u).fillMaxHeight().background(C.Line))
        Column(Modifier.width(470.u).fillMaxHeight().padding(top = 28.u, start = 12.u, end = 12.u)) {
            Row(Modifier.padding(start = 10.u, end = 4.u, top = 2.u, bottom = 10.u).height(36.u), verticalAlignment = Alignment.CenterVertically) {
                Label(groups.firstOrNull { it.id == group }?.name ?: "Channels", modifier = Modifier.weight(1f))
                if (toggleable(group) && channels.isNotEmpty()) {
                    FocusItem(
                        onClick = { vm.setTiles(group, !tiles) },
                        container = C.Panel,
                        shape = RoundedCornerShape(999.u),
                        modifier = Modifier.height(34.u),
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
                        when (group) {
                            GROUP_FAV -> "No favorites yet. Hold OK on any channel to add it here."
                            GROUP_RECENT -> "Channels you watch will appear here."
                            else -> "No channels in this group."
                        },
                        size = 15, color = C.Muted, maxLines = 3,
                    )
                }
            } else if (tiles) {
                TileGrid(vm, channels, onFocus = { focused = it })
            } else {
                ChannelList(vm, channels, onFocus = { focused = it })
            }
        }
        Box(Modifier.width(1.u).fillMaxHeight().background(C.Line))
        ChannelPreviewPane(vm, previewCh, hasFocus = focused != null, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GroupList(vm: AppViewModel, groups: List<GroupItem>, selected: String, modifier: Modifier) {
    val selIndex = remember(groups, selected) { groups.indexOfFirst { it.id == selected }.coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selIndex - 3).coerceAtLeast(0))
    val keys = remember(groups) { stableKeys(groups) { it.id } }
    // ▲ / ▼ move between groups, skipping region headings (see ListNav).
    val nav = rememberListNav(state, "Groups", rowHeight = 46.u)
    fun move(i: Int, down: Boolean) = nav.step(
        from = i, down = down, size = groups.size,
        focusable = { groups[it].kind != GroupKind.HEADER },
        keyAt = { keys[it] },
        describe = { k -> groups.getOrNull(keys.indexOf(k))?.name ?: "none" },
    )
    Column(modifier.fillMaxHeight().padding(top = 28.u, start = 20.u, end = 16.u)) {
        Label("Groups", modifier = Modifier.padding(start = 10.u, top = 6.u, bottom = 14.u))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.u), contentPadding = PaddingValues(bottom = 28.u)) {
            itemsIndexed(groups, key = { idx, _ -> keys[idx] }) { i, g ->
                val key = keys[i]
                val navMod = Modifier.focusRequester(nav.requester(key)).verticalKeys { down -> move(i, down) }
                when (g.kind) {
                    GroupKind.HEADER -> Row(
                        Modifier.fillMaxWidth().padding(start = 10.u, end = 12.u, top = 18.u, bottom = 6.u),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label(g.name, color = C.Text2, modifier = Modifier.weight(1f))
                        T(Repository.fmt(g.count), size = 11, color = C.Dim)
                    }
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
                            onClick = { vm.liveGroup = g.id },
                            onFocus = { nav.focusedKey = key; vm.liveGroup = g.id },
                            selected = isSel,
                            shape = RoundedCornerShape(10.u),
                            modifier = navMod.fillMaxWidth().height(42.u),
                        ) { f ->
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 12.u),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(
                                    g.name, size = 15,
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

@Composable
private fun ChannelList(vm: AppViewModel, channels: List<Channel>, onFocus: (Channel) -> Unit) {
    val now = rememberNow()
    // Start on the channel watched last, so coming back from full screen feels natural.
    val startIndex = remember(channels) {
        val i = channels.indexOfFirst { it.id == vm.prefs.lastChannel && !it.divider }
        if (i >= 0) i else channels.indexOfFirst { !it.divider }.coerceAtLeast(0)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 3).coerceAtLeast(0))
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(channels) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
    val keys = remember(channels) { stableKeys(channels) { it.id } }
    // ▲ / ▼ move channel by channel, skipping the provider's divider headings (see ListNav).
    val nav = rememberListNav(state, "Channel list", rowHeight = 68.u)
    fun move(i: Int, down: Boolean) = nav.step(
        from = i, down = down, size = channels.size,
        focusable = { !channels[it].divider },
        keyAt = { keys[it] },
        describe = { k -> channels.getOrNull(keys.indexOf(k))?.let { "${it.num} ${it.display}" } ?: "none" },
    )
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
                onLongClick = { vm.toggleFavorite(ch.favKey, ch.display) },
                onFocus = { nav.focusedKey = keys[i]; onFocus(ch) },
                modifier = Modifier
                    .focusRequester(nav.requester(keys[i]))
                    .verticalKeys { down -> move(i, down) }
                    .fillMaxWidth()
                    .height(64.u)
                    .let { if (i == startIndex) it.focusRequester(firstFocus) else it },
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
private fun TileGrid(vm: AppViewModel, channels: List<Channel>, onFocus: (Channel) -> Unit) {
    val now = rememberNow()
    val playable = remember(channels) { channels.filter { !it.divider } }
    val first = remember { FocusRequester() }
    LaunchedEffect(playable) { delay(150); runCatching { first.requestFocus() } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(14.u),
        verticalArrangement = Arrangement.spacedBy(18.u),
        contentPadding = PaddingValues(start = 6.u, end = 6.u, top = 6.u, bottom = 28.u),
    ) {
        items(playable.size, key = { "$it:" + (playable[it].id) }) { i ->
            val ch = playable[i]
            ChannelTile(
                vm, ch, now, width = null,
                onClick = { vm.playLive(playable, i) },
                modifier = Modifier
                    .let { if (i == 0) it.focusRequester(first) else it }
                    .onFocusChanged { if (it.hasFocus) onFocus(ch) },
            )
        }
    }
}

