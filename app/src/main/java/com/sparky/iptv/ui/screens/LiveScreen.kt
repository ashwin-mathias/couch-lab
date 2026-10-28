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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.R
import com.sparky.iptv.data.Channel
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.GROUP_ALL
import com.sparky.iptv.ui.GROUP_FAV
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.ChannelLogo
import com.sparky.iptv.ui.components.Clock
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Icon
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.PlayerSurface
import com.sparky.iptv.ui.components.ProgressBar
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtLeft
import com.sparky.iptv.ui.components.fmtShort
import com.sparky.iptv.ui.components.fmtClock
import com.sparky.iptv.ui.components.rememberNow
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

data class GroupItem(val id: String, val name: String, val count: Int)

@Composable
fun liveGroups(vm: AppViewModel): List<GroupItem> {
    val lib = vm.library
    val favs = vm.favorites
    return remember(lib, favs) {
        buildList {
            add(GroupItem(GROUP_FAV, "Favorites", lib.channels.count { it.favKey in favs }))
            add(GroupItem(GROUP_ALL, "All channels", lib.channels.size))
            val counts = lib.channels.groupingBy { it.categoryId }.eachCount()
            lib.liveCats.forEach { c -> counts[c.id]?.let { add(GroupItem(c.id, c.name, it)) } }
        }
    }
}

@Composable
fun LiveScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val groups = liveGroups(vm)
    val group = vm.liveGroup
    val channels = remember(vm.library, vm.favorites, group) { vm.channelsFor(group) }
    var focused by remember { mutableStateOf<Channel?>(null) }
    val previewCh = focused ?: channels.firstOrNull()

    // Start the small preview a moment after the highlight settles on a channel.
    LaunchedEffect(focused?.id) {
        val ch = focused ?: return@LaunchedEffect
        delay(700)
        PlayerHolder.play(ctx, ch.url)
    }
    DisposableEffect(Unit) {
        vm.liveHandoff = false
        onDispose {
            if (!vm.liveHandoff) PlayerHolder.stop()
            vm.liveHandoff = false
        }
    }

    Row(Modifier.fillMaxSize()) {
        GroupList(groups, group, onSelect = { vm.liveGroup = it }, modifier = Modifier.width(220.u))
        Box(Modifier.width(1.u).fillMaxHeight().background(C.Line))
        ChannelList(
            vm = vm,
            title = groups.firstOrNull { it.id == group }?.name ?: "Channels",
            channels = channels,
            onFocus = { focused = it },
            modifier = Modifier.width(470.u),
        )
        Box(Modifier.width(1.u).fillMaxHeight().background(C.Line))
        PreviewPane(vm, previewCh, hasFocus = focused != null, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GroupList(groups: List<GroupItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(top = 28.u, start = 20.u, end = 16.u)) {
        Label("Groups", modifier = Modifier.padding(start = 10.u, top = 6.u, bottom = 14.u))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.u), contentPadding = PaddingValues(bottom = 28.u)) {
            itemsIndexed(groups, key = { _, g -> g.id }) { _, g ->
                val isSel = g.id == selected
                FocusItem(
                    onClick = { onSelect(g.id) },
                    onFocus = { onSelect(g.id) },
                    selected = isSel,
                    shape = RoundedCornerShape(10.u),
                    modifier = Modifier.fillMaxWidth().height(42.u),
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
                        T(g.count.toString(), size = 13, color = if (f) C.OnFocusMuted else C.Dim)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelList(
    vm: AppViewModel,
    title: String,
    channels: List<Channel>,
    onFocus: (Channel) -> Unit,
    modifier: Modifier,
) {
    val now = rememberNow()
    // Start on the channel watched last, so coming back from full screen feels natural.
    val startIndex = remember(channels) {
        channels.indexOfFirst { it.id == vm.prefs.lastChannel }.coerceAtLeast(0)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 3).coerceAtLeast(0))
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Put the highlight on the channel list when Live TV opens.
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
    Column(modifier.fillMaxHeight().padding(top = 28.u, start = 12.u, end = 12.u)) {
        Row(Modifier.padding(start = 10.u, end = 10.u, top = 6.u, bottom = 12.u), verticalAlignment = Alignment.CenterVertically) {
            Label(title, modifier = Modifier.weight(1f))
            T("Hold OK to ★", size = 12, color = C.Dim)
        }
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.u)) {
                T(
                    if (vm.liveGroup == GROUP_FAV) "No favorites yet. Hold OK on any channel to add it here." else "No channels in this group.",
                    size = 15, color = C.Muted, maxLines = 3,
                )
            }
            return@Column
        }
        LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(4.u),
            contentPadding = PaddingValues(bottom = 28.u),
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                LaunchedEffect(ch.id) { vm.ensureEpg(ch) }
                val (cur, _) = vm.nowNext(ch.id, now)
                val fav = ch.favKey in vm.favorites
                FocusItem(
                    onClick = { vm.playLive(channels, i) },
                    onLongClick = { vm.toggleFavorite(ch.favKey, ch.name) },
                    onFocus = { onFocus(ch) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.u)
                        .let { if (i == startIndex) it.focusRequester(firstFocus) else it },
                ) { f ->
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.u),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(ch.num.toString(), size = 13, weight = FontWeight.SemiBold, color = if (f) C.OnFocusMuted else C.Dim, modifier = Modifier.width(40.u))
                        ChannelLogo(ch.name, ch.logo, 40.u, focused = f)
                        Spacer(Modifier.width(14.u))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.u)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(ch.name, size = 15, weight = if (f) FontWeight.Bold else FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text, modifier = Modifier.weight(1f))
                                if (fav) Icon(R.drawable.ic_star, tint = if (f) C.OnFocus else C.Text2, size = 13.u)
                                if (cur != null) {
                                    Spacer(Modifier.width(8.u))
                                    T(fmtLeft(cur.end, now), size = 12, color = if (f) C.OnFocusMuted else C.Dim)
                                }
                            }
                            T(cur?.title ?: " ", size = 13, color = if (f) C.OnFocusMuted else C.Muted)
                            if (cur != null) {
                                val frac = (now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)
                                ProgressBar(frac, track = if (f) C.TrackOnFocus else C.Line, fill = if (f) C.OnFocus else C.Dim)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(vm: AppViewModel, ch: Channel?, hasFocus: Boolean, modifier: Modifier) {
    val now = rememberNow()
    Column(modifier.fillMaxHeight().padding(horizontal = 32.u, vertical = 28.u), verticalArrangement = Arrangement.spacedBy(20.u)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            if (vm.refreshing) {
                T("Updating…", size = 13, color = C.Dim, modifier = Modifier.padding(end = 16.u))
            }
            Clock()
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.u)).background(C.Panel),
            contentAlignment = Alignment.Center,
        ) {
            if (hasFocus && ch != null) {
                PlayerSurface(Modifier.fillMaxSize())
            } else {
                Label("Live preview")
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(12.u).clip(RoundedCornerShape(999.u)).background(C.Bg)
                    .padding(horizontal = 10.u, vertical = 4.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.u).clip(CircleShape).background(C.Accent))
                Spacer(Modifier.width(6.u))
                T("LIVE", size = 11, weight = FontWeight.Bold, letterSpacing = 0.08f)
            }
        }
        if (ch == null) return@Column
        val (cur, next) = vm.nowNext(ch.id, now)
        Column(verticalArrangement = Arrangement.spacedBy(8.u)) {
            Label(
                if (cur != null) "Now · ${fmtShort(cur.start)} – ${fmtClock(cur.end)}" else "${ch.num} · ${ch.name}"
            )
            T(cur?.title ?: ch.name, size = 24, weight = FontWeight.Bold, maxLines = 2)
            if (cur != null) {
                val frac = (now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)
                ProgressBar(frac, track = C.Line, fill = C.Text)
                if (cur.description.isNotBlank()) {
                    T(cur.description, size = 14, color = C.Muted, maxLines = 3, lineHeight = 1.55f, modifier = Modifier.padding(top = 6.u))
                }
            } else {
                T("No guide information for this channel.", size = 14, color = C.Muted)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.u).background(C.Line))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.u)) {
                if (next != null) {
                    Label("Next · ${fmtClock(next.start)}")
                    T(next.title, size = 16, weight = FontWeight.SemiBold)
                }
            }
            T("OK to watch", size = 13, color = C.Dim)
        }
    }
}
