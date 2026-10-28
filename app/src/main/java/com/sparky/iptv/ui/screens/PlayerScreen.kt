package com.sparky.iptv.ui.screens

import android.view.KeyEvent as AKey
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.sparky.iptv.R
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.PlayRequest
import com.sparky.iptv.ui.Section
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.ChannelLogo
import com.sparky.iptv.ui.components.Clock
import com.sparky.iptv.ui.components.Icon
import com.sparky.iptv.ui.components.PlayerSurface
import com.sparky.iptv.ui.components.ProgressBar
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtClock
import com.sparky.iptv.ui.components.fmtShort
import com.sparky.iptv.ui.components.rememberNow
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: AppViewModel, req: PlayRequest) {
    when (req) {
        is PlayRequest.Live -> LivePlayer(vm, req)
        is PlayRequest.Vod -> VodPlayer(vm, req)
    }
}

@Composable
private fun LivePlayer(vm: AppViewModel, req: PlayRequest.Live) {
    val ctx = LocalContext.current
    val channels = req.channels
    var index by remember { mutableIntStateOf(req.index.coerceIn(0, channels.lastIndex)) }
    var previous by remember { mutableIntStateOf(-1) }
    var showInfo by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var triedFallback by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    val ch = channels[index]
    val now = rememberNow(15_000L)
    val focus = remember { FocusRequester() }

    // Back (or ◀) returns to the channel list. On Live TV the stream keeps playing in the preview.
    fun leave() {
        if (vm.section != Section.LIVE) PlayerHolder.stop()
        vm.pop()
    }
    BackHandler { leave() }
    // Registered after the one above, so Back closes the channel list first.
    BackHandler(enabled = listOpen) { listOpen = false }

    LaunchedEffect(ch.id) {
        error = null
        triedFallback = false
        PlayerHolder.play(ctx, ch.url)
        vm.prefs.lastChannel = ch.id
        vm.ensureEpg(ch)
        showInfo = true
        infoTick++
    }
    // Counts as "recently watched" after 10 seconds on a channel.
    LaunchedEffect(ch.id) {
        delay(10_000)
        vm.addRecent("live", ch.id)
    }
    LaunchedEffect(infoTick) {
        delay(5000)
        showInfo = false
    }
    DisposableEffect(ch.id) {
        val player = PlayerHolder.get(ctx)
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                // Some providers only serve one of HLS / MPEG-TS; try the other once.
                if (!triedFallback) {
                    triedFallback = true
                    val alt = when {
                        ch.url.endsWith(".m3u8") -> ch.url.removeSuffix(".m3u8") + ".ts"
                        ch.url.endsWith(".ts") -> ch.url.removeSuffix(".ts") + ".m3u8"
                        else -> null
                    }
                    if (alt != null) {
                        PlayerHolder.play(ctx, alt, force = true)
                        return
                    }
                }
                com.sparky.iptv.data.AppLog.e("Live playback failed on channel ${ch.num} ${ch.name}: ${e.errorCodeName}")
                error = "This channel isn't playing right now."
                showInfo = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(listOpen) {
        if (!listOpen) {
            delay(80)
            runCatching { focus.requestFocus() }
        }
    }

    fun switchTo(i: Int) {
        if (i == index) return
        previous = index
        index = i
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (listOpen) return@onPreviewKeyEvent false
                val code = ev.nativeKeyEvent.keyCode
                when (ev.type) {
                    KeyEventType.KeyDown -> when (code) {
                        AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_CHANNEL_UP -> { switchTo((index + 1) % channels.size); true }
                        AKey.KEYCODE_DPAD_DOWN, AKey.KEYCODE_CHANNEL_DOWN -> { switchTo((index - 1 + channels.size) % channels.size); true }
                        AKey.KEYCODE_DPAD_LEFT -> { leave(); true }
                        AKey.KEYCODE_LAST_CHANNEL -> {
                            if (previous in channels.indices) switchTo(previous)
                            true
                        }
                        AKey.KEYCODE_DPAD_RIGHT -> {
                            showInfo = !showInfo
                            if (showInfo) infoTick++
                            true
                        }
                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> true
                        else -> false
                    }
                    KeyEventType.KeyUp -> when (code) {
                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> {
                            val held = ev.nativeKeyEvent.eventTime - ev.nativeKeyEvent.downTime
                            if (held > 600) {
                                vm.toggleFavorite(ch.favKey, ch.display)
                            } else {
                                listOpen = true
                                showInfo = false
                            }
                            true
                        }
                        else -> false
                    }
                    else -> false
                }
            },
    ) {
        PlayerSurface(Modifier.fillMaxSize())

        if (showInfo) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 32.u, end = 48.u)
                    .clip(RoundedCornerShape(10.u)).background(C.Bg.copy(alpha = 0.85f))
                    .padding(horizontal = 14.u, vertical = 8.u)
            ) { Clock() }

            val (cur, next) = vm.nowNext(ch.id, now)
            val fav = ch.favKey in vm.favorites
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 48.u, end = 48.u, bottom = 40.u)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.u))
                    .background(C.Bg.copy(alpha = 0.92f))
                    .padding(horizontal = 28.u, vertical = 24.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelLogo(ch.display, ch.logo, 64.u)
                Spacer(Modifier.width(24.u))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.u)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(ch.num.toString(), size = 15, weight = FontWeight.Bold, color = C.Muted)
                        Spacer(Modifier.width(14.u))
                        T(ch.display, size = 15, weight = FontWeight.SemiBold, color = C.Text2)
                        if (fav) {
                            Spacer(Modifier.width(10.u))
                            Icon(R.drawable.ic_star, tint = C.Text, size = 16.u)
                        }
                        Spacer(Modifier.weight(1f))
                        T("${index + 1} of ${channels.size}", size = 13, color = C.Dim)
                    }
                    T(error ?: cur?.title ?: ch.display, size = 24, weight = FontWeight.Bold)
                    if (cur != null && error == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(fmtShort(cur.start), size = 13, color = C.Muted)
                            Spacer(Modifier.width(14.u))
                            val frac = (now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)
                            ProgressBar(frac, track = C.Line2, fill = C.Text, height = 4.u, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(14.u))
                            T(fmtShort(cur.end), size = 13, color = C.Muted)
                        }
                    }
                    Row {
                        T(next?.let { "Next · ${fmtClock(it.start)}  ${it.title}" } ?: "", size = 13, color = C.Dim, modifier = Modifier.weight(1f))
                        T("OK Channel list   ·   ▲▼ Change channel   ·   ◀ Back   ·   ▶ Info   ·   Hold OK ♥", size = 13, color = C.Dim)
                    }
                }
            }
        }

        if (listOpen) {
            ChannelOverlay(
                vm = vm,
                channels = channels,
                current = index,
                onPick = { i -> switchTo(i); listOpen = false },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

/** Slim channel list over the video, so you can switch without leaving full screen. */
@Composable
private fun ChannelOverlay(
    vm: AppViewModel,
    channels: List<com.sparky.iptv.data.Channel>,
    current: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow()
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (current - 4).coerceAtLeast(0))
    val sel = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { sel.requestFocus() }
    }
    Column(
        modifier.fillMaxHeight().width(500.u).background(C.Bg.copy(alpha = 0.94f))
            .padding(start = 28.u, end = 20.u, top = 32.u),
        verticalArrangement = Arrangement.spacedBy(12.u),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.sparky.iptv.ui.components.Label("Channels", modifier = Modifier.weight(1f))
            T("Hold OK ♥  ·  Back to close", size = 12, color = C.Dim)
        }
        androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(4.u),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.u),
        ) {
            items(channels.size, key = { "$it:" + (channels[it].id) }) { i ->
                val c = channels[i]
                LaunchedEffect(c.id) { vm.ensureEpg(c) }
                val (cur, _) = vm.nowNext(c.id, now)
                val isCur = i == current
                com.sparky.iptv.ui.components.FocusItem(
                    onClick = { onPick(i) },
                    onLongClick = { vm.toggleFavorite(c.favKey, c.display) },
                    selected = isCur,
                    modifier = Modifier.fillMaxWidth().height(58.u).let { if (isCur) it.focusRequester(sel) else it },
                ) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.u), verticalAlignment = Alignment.CenterVertically) {
                        T(c.num.toString(), size = 13, weight = FontWeight.SemiBold, color = if (f) C.OnFocusMuted else C.Dim, modifier = Modifier.width(46.u))
                        ChannelLogo(c.display, c.logo, 36.u, focused = f)
                        Spacer(Modifier.width(12.u))
                        Column(Modifier.weight(1f)) {
                            T(c.display, size = 15, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text)
                            if (cur != null) T(cur.title, size = 12, color = if (f) C.OnFocusMuted else C.Dim)
                        }
                        if (c.favKey in vm.favorites) Icon(R.drawable.ic_star, tint = if (f) C.OnFocus else C.Text2, size = 13.u)
                    }
                }
            }
        }
    }
}

@Composable
private fun VodPlayer(vm: AppViewModel, req: PlayRequest.Vod) {
    val ctx = LocalContext.current
    var idx by remember { mutableIntStateOf(req.index.coerceIn(0, req.items.lastIndex)) }
    val item = req.items[idx]
    var error by remember { mutableStateOf<String?>(null) }
    var showTitle by remember { mutableStateOf(true) }
    LaunchedEffect(item.key) {
        showTitle = true
        delay(4000)
        showTitle = false
    }

    fun save() {
        val pos = PlayerHolder.currentPosition()
        val dur = PlayerHolder.duration()
        if (dur > 0) vm.saveProgress(item.key, pos, dur)
    }

    BackHandler {
        save()
        PlayerHolder.stop()
        vm.pop()
    }

    LaunchedEffect(item.key) {
        error = null
        val start = if (idx == req.index) req.resumeMs else (vm.progress(item.key)?.pos ?: 0L)
        PlayerHolder.play(ctx, item.url, startMs = start, force = true)
        vm.onEpisodeStarted(item)
        while (true) {
            delay(10_000)
            save()
        }
    }
    DisposableEffect(item.key) {
        val player = PlayerHolder.get(ctx)
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    vm.saveProgress(item.key, PlayerHolder.duration(), PlayerHolder.duration())
                    if (idx < req.items.lastIndex) idx++ else {
                        PlayerHolder.stop()
                        vm.pop()
                    }
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                com.sparky.iptv.data.AppLog.e("Playback failed for ${item.title}: ${e.errorCodeName}")
                error = "This title couldn't be played. (${e.errorCodeName})"
            }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(Modifier.fillMaxSize(), withControls = true)
        if (showTitle) Column(
            Modifier.align(Alignment.TopStart).padding(top = 32.u, start = 48.u, end = 48.u),
            verticalArrangement = Arrangement.spacedBy(4.u),
        ) {
            T(item.title, size = 20, weight = FontWeight.Bold)
            if (item.subtitle.isNotBlank()) T(item.subtitle, size = 14, color = C.Text2)
        }
        error?.let {
            Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.u)).background(C.Bg.copy(alpha = 0.92f))
                    .padding(horizontal = 32.u, vertical = 24.u)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.u)) {
                    T(it, size = 17, weight = FontWeight.SemiBold)
                    T("Press Back to return.", size = 14, color = C.Muted)
                }
            }
        }
    }
}
