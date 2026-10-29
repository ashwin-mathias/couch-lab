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
import com.sparky.iptv.player.TrackHelper
import com.sparky.iptv.player.SubtitleStyleState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.media3.common.C as MediaC
import com.sparky.iptv.ui.components.Label
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
    var panelOpen by remember { mutableStateOf(false) }
    var tracksTick by remember { mutableIntStateOf(0) }
    var infoTick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var triedFallback by remember { mutableStateOf(false) }
    val ch = channels[index]
    val now = rememberNow(15_000L)
    val focus = remember { FocusRequester() }

    // Back (or ◀) returns to the page you came from. Pages with a preview keep the stream playing there.
    fun leave() {
        val hasPreview = vm.section in setOf(Section.LIVE, Section.FAVORITES, Section.RECENT)
        if (hasPreview) vm.liveHandoff = true else PlayerHolder.stop()
        vm.pop()
    }
    BackHandler { leave() }
    // Registered after the one above, so Back closes the audio & subtitles panel first.
    BackHandler(enabled = panelOpen) { panelOpen = false }

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
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // Re-apply the audio / subtitle choice remembered for this channel.
                val (audio, text) = vm.prefs.trackChoice(ch.favKey)
                if (audio != null || text != null) TrackHelper.applyRemembered(player, audio, text)
                tracksTick++
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(panelOpen) {
        if (!panelOpen) {
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
                if (panelOpen) return@onPreviewKeyEvent false
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
                        AKey.KEYCODE_DPAD_RIGHT, AKey.KEYCODE_MENU, AKey.KEYCODE_CAPTIONS -> {
                            // Opens the audio & subtitles panel, with the channel info.
                            panelOpen = true
                            showInfo = true
                            infoTick++
                            true
                        }
                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> true
                        else -> false
                    }
                    KeyEventType.KeyUp -> when (code) {
                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> {
                            val held = ev.nativeKeyEvent.eventTime - ev.nativeKeyEvent.downTime
                            // Hold OK toggles the favorite; a short press does nothing.
                            if (held > 600) vm.toggleFavorite(ch.favKey, ch.display)
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
                        T("▲▼ Change channel   ·   ◀ Back   ·   ▶ Audio & subtitles   ·   Hold OK ♥", size = 13, color = C.Dim)
                    }
                }
            }
        }

        if (panelOpen) {
            TrackPanel(
                vm = vm,
                tick = tracksTick,
                current = { vm.prefs.trackChoice(ch.favKey) },
                onSave = { a, t -> vm.prefs.saveTrackChoice(ch.favKey, a, t) },
                onChanged = { tracksTick++ },
                note = "Remembered for this channel.  Back to close.",
                noSubs = "This channel has no subtitles.",
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Side panel listing the stream's audio tracks and subtitles. */
@Composable
private fun TrackPanel(
    vm: AppViewModel,
    tick: Int,
    current: () -> Pair<String?, String?>,
    onSave: (audio: String?, text: String?) -> Unit,
    onChanged: () -> Unit,
    note: String,
    noSubs: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.get(ctx) }
    val audio = remember(tick) { TrackHelper.options(player, MediaC.TRACK_TYPE_AUDIO) }
    val subs = remember(tick) { TrackHelper.options(player, MediaC.TRACK_TYPE_TEXT) }
    val subsOn = remember(tick) { TrackHelper.subtitlesOn(player) }
    val first = remember { FocusRequester() }
    val styleFirst = remember { FocusRequester() }
    var styleOpen by remember { mutableStateOf(false) }
    LaunchedEffect(styleOpen) {
        delay(80)
        runCatching { if (styleOpen) styleFirst.requestFocus() else first.requestFocus() }
    }
    // Registered after the player's handlers, so Back leaves the style options first.
    BackHandler(enabled = styleOpen) { styleOpen = false }

    fun saveChoice(audioLang: String?, textLang: String?) {
        val (a, t) = current()
        onSave(audioLang ?: a, textLang ?: t)
    }

    if (styleOpen) {
        Column(
            modifier.fillMaxHeight().width(380.u).background(C.Bg.copy(alpha = 0.94f))
                .padding(start = 24.u, end = 40.u, top = 40.u, bottom = 40.u),
            verticalArrangement = Arrangement.spacedBy(6.u),
        ) {
            T("Subtitle style", size = 20, weight = FontWeight.Bold)
            T("◀ ▶ to change, shown live on the video.  Back to return.", size = 12, color = C.Dim, maxLines = 2,
                modifier = Modifier.padding(bottom = 10.u))
            SubtitleStyleOptions(vm, styleFirst)
        }
        return
    }

    Column(
        modifier.fillMaxHeight().width(380.u).background(C.Bg.copy(alpha = 0.94f))
            .verticalScroll(rememberScrollState())
            .padding(start = 24.u, end = 40.u, top = 40.u, bottom = 40.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        T("Audio & subtitles", size = 20, weight = FontWeight.Bold)
        T(note, size = 12, color = C.Dim, maxLines = 2, modifier = Modifier.padding(bottom = 10.u))

        Label("Audio", modifier = Modifier.padding(top = 6.u, bottom = 4.u))
        if (audio.isEmpty()) {
            T("Loading…", size = 14, color = C.Dim)
        } else if (audio.size == 1) {
            T("${audio[0].label} (only track)", size = 14, color = C.Muted, modifier = Modifier.padding(start = 4.u))
        }
        if (audio.size > 1) audio.forEachIndexed { i, o ->
            TrackRow(o.label, o.selected, if (i == 0) Modifier.focusRequester(first) else Modifier) {
                TrackHelper.select(player, MediaC.TRACK_TYPE_AUDIO, o)
                saveChoice(o.language, null)
                onChanged()
            }
        }

        Label("Subtitles", modifier = Modifier.padding(top = 16.u, bottom = 4.u))
        TrackRow("Off", !subsOn, if (audio.size <= 1) Modifier.focusRequester(first) else Modifier) {
            TrackHelper.subtitlesOff(player)
            saveChoice(null, "off")
            onChanged()
        }
        if (subs.isEmpty()) {
            T(noSubs, size = 13, color = C.Dim, modifier = Modifier.padding(start = 4.u, top = 4.u))
        }
        subs.forEach { o ->
            TrackRow(o.label, subsOn && o.selected) {
                TrackHelper.select(player, MediaC.TRACK_TYPE_TEXT, o)
                saveChoice(null, o.language ?: "")
                onChanged()
            }
        }

        Spacer(Modifier.height(10.u))
        TrackRow("Subtitle style…", selected = false) { styleOpen = true }
        T(SubtitleStyleState.style.summary, size = 12, color = C.Dim, maxLines = 2, modifier = Modifier.padding(start = 38.u))
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    com.sparky.iptv.ui.components.FocusItem(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(10.u),
        modifier = modifier.fillMaxWidth().height(46.u),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.u), verticalAlignment = Alignment.CenterVertically) {
            T(if (selected) "✓" else "", size = 14, weight = FontWeight.Bold, color = if (f) C.OnFocus else C.Text, modifier = Modifier.width(24.u))
            T(label, size = 15, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (f) C.OnFocus else C.Text)
        }
    }
}

private enum class VodButton(val label: String) {
    PLAY("Pause"), BACK("Back 10s"), FORWARD("Forward 10s"), TRACKS("Audio & subtitles"), NEXT("Next episode"), RESTART("Start over")
}

/** "1:02:05" or "12:05". */
private fun fmtTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/**
 * Movies and episodes. Our own control bar: the player box always keeps focus, so the remote
 * works whether the bar is showing or not.
 *  - Bar hidden: OK = play/pause, ◀ / ▶ = back / forward 10 s (hold to go faster), ▲ / ▼ = show the bar.
 *  - Bar showing: ▲ / ▼ move between the timeline and the buttons; on the timeline ◀ / ▶ seek,
 *    on the buttons ◀ / ▶ move and OK presses.
 *  - Back closes the panel, then the bar, then leaves (progress is saved).
 */
@Composable
private fun VodPlayer(vm: AppViewModel, req: PlayRequest.Vod) {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.get(ctx) }
    var idx by remember { mutableIntStateOf(req.index.coerceIn(0, req.items.lastIndex)) }
    val item = req.items[idx]
    val hasNext = idx < req.items.lastIndex
    var error by remember { mutableStateOf<String?>(null) }
    var barVisible by remember { mutableStateOf(true) }
    var onButtons by remember { mutableStateOf(false) }   // false = timeline row
    var button by remember { mutableIntStateOf(0) }
    var barTick by remember { mutableIntStateOf(0) }       // any key restarts the hide timer
    var panelOpen by remember { mutableStateOf(false) }
    var tracksTick by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekTick by remember { mutableIntStateOf(0) }
    var appliedFor by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    val buttons = remember(hasNext) { VodButton.entries.filter { it != VodButton.NEXT || hasNext } }
    LaunchedEffect(buttons) { button = button.coerceIn(0, buttons.lastIndex) }

    fun save() {
        val pos = PlayerHolder.currentPosition()
        val dur = PlayerHolder.duration()
        if (dur > 0) vm.saveProgress(item.key, pos, dur)
    }
    fun exit() {
        save()
        PlayerHolder.stop()
        vm.pop()
    }
    fun showBar(buttonsRow: Boolean? = null) {
        if (buttonsRow != null) onButtons = buttonsRow
        barVisible = true
        barTick++
    }
    fun seekBy(delta: Long) {
        val base = seekTarget ?: player.currentPosition
        val dur = PlayerHolder.duration()
        var target = (base + delta).coerceAtLeast(0L)
        if (dur > 0) target = target.coerceAtMost((dur - 1000).coerceAtLeast(0L))
        player.seekTo(target)
        seekTarget = target
        position = target
        seekTick++
    }
    fun togglePlay() {
        player.playWhenReady = !player.playWhenReady
        playing = player.playWhenReady
    }
    fun press(b: VodButton) {
        when (b) {
            VodButton.PLAY -> togglePlay()
            VodButton.BACK -> seekBy(-10_000)
            VodButton.FORWARD -> seekBy(10_000)
            VodButton.TRACKS -> panelOpen = true
            VodButton.NEXT -> { save(); if (hasNext) idx++ }
            VodButton.RESTART -> { player.seekTo(0); position = 0; player.playWhenReady = true; playing = true }
        }
    }

    BackHandler { exit() }
    // Registered later, so they take precedence: Back closes the bar, then the panel.
    BackHandler(enabled = barVisible && !panelOpen) { barVisible = false }
    BackHandler(enabled = panelOpen) { panelOpen = false }

    LaunchedEffect(item.key) {
        error = null
        appliedFor = null
        val start = if (idx == req.index) req.resumeMs else (vm.progress(item.key)?.pos ?: 0L)
        PlayerHolder.play(ctx, item.url, startMs = start, force = true)
        playing = true
        vm.onEpisodeStarted(item)
        showBar(false)
        while (true) {
            delay(10_000)
            save()
        }
    }
    // Hide the bar after 5 s without a key press (it stays while paused).
    LaunchedEffect(barTick, barVisible, panelOpen, playing) {
        if (barVisible && !panelOpen && playing) {
            delay(5000)
            barVisible = false
        }
    }
    // A run of seeks counts as one; after a pause the next one starts from the real position.
    LaunchedEffect(seekTick) {
        delay(1200)
        seekTarget = null
    }
    // Keep the timeline current while the bar is up.
    LaunchedEffect(barVisible) {
        while (barVisible) {
            if (seekTarget == null) position = player.currentPosition
            duration = PlayerHolder.duration()
            delay(500)
        }
    }
    LaunchedEffect(panelOpen) {
        if (!panelOpen) {
            delay(80)
            runCatching { focus.requestFocus() }
        }
    }
    DisposableEffect(item.key) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = PlayerHolder.duration()
                if (state == Player.STATE_ENDED) {
                    vm.saveProgress(item.key, PlayerHolder.duration(), PlayerHolder.duration())
                    if (idx < req.items.lastIndex) idx++ else {
                        PlayerHolder.stop()
                        vm.pop()
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = player.playWhenReady
            }
            override fun onPlayerError(e: PlaybackException) {
                com.sparky.iptv.data.AppLog.e("Playback failed for ${item.title}: ${e.errorCodeName}")
                error = "This title couldn't be played. (${e.errorCodeName})"
                barVisible = true
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // Apply the general movies & series setting once per title; changes made in the panel stay put.
                if (appliedFor != item.key && !tracks.isEmpty) {
                    val (audio, text) = vm.prefs.vodTrackChoice()
                    if (audio != null || text != null) TrackHelper.applyRemembered(player, audio, text)
                    appliedFor = item.key
                }
                tracksTick++
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (panelOpen) return@onPreviewKeyEvent false
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val ne = ev.nativeKeyEvent
                // Holding ◀ / ▶ jumps further the longer it's held.
                val step = when {
                    ne.repeatCount >= 20 -> 60_000L
                    ne.repeatCount >= 6 -> 30_000L
                    else -> 10_000L
                }
                when (ne.keyCode) {
                    AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> {
                        if (ne.repeatCount > 0) return@onPreviewKeyEvent true
                        if (barVisible && onButtons) buttons.getOrNull(button)?.let { press(it) } else togglePlay()
                        showBar()
                        true
                    }
                    AKey.KEYCODE_DPAD_LEFT -> {
                        if (barVisible && onButtons) {
                            if (ne.repeatCount == 0) button = (button - 1).coerceAtLeast(0)
                            showBar()
                        } else { seekBy(-step); showBar(false) }
                        true
                    }
                    AKey.KEYCODE_DPAD_RIGHT -> {
                        if (barVisible && onButtons) {
                            if (ne.repeatCount == 0) button = (button + 1).coerceAtMost(buttons.lastIndex)
                            showBar()
                        } else { seekBy(step); showBar(false) }
                        true
                    }
                    AKey.KEYCODE_DPAD_DOWN -> { showBar(if (barVisible) true else onButtons); true }
                    AKey.KEYCODE_DPAD_UP -> { showBar(if (barVisible) false else onButtons); true }
                    AKey.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); showBar(); true }
                    AKey.KEYCODE_MEDIA_PLAY -> { player.playWhenReady = true; playing = true; showBar(); true }
                    AKey.KEYCODE_MEDIA_PAUSE -> { player.playWhenReady = false; playing = false; showBar(); true }
                    AKey.KEYCODE_MEDIA_REWIND -> { seekBy(-step); showBar(false); true }
                    AKey.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(step); showBar(false); true }
                    AKey.KEYCODE_MEDIA_NEXT -> { if (hasNext) press(VodButton.NEXT); true }
                    AKey.KEYCODE_MENU, AKey.KEYCODE_CAPTIONS -> { panelOpen = true; showBar(); true }
                    else -> false
                }
            },
    ) {
        PlayerSurface(Modifier.fillMaxSize())

        if (barVisible || panelOpen) {
            // Title, top left; clock, top right.
            Column(
                Modifier.align(Alignment.TopStart).padding(top = 32.u, start = 48.u, end = 480.u),
                verticalArrangement = Arrangement.spacedBy(4.u),
            ) {
                T(item.title, size = 20, weight = FontWeight.Bold)
                if (item.subtitle.isNotBlank()) T(item.subtitle, size = 14, color = C.Text2)
            }
            if (!panelOpen) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 32.u, end = 48.u)
                    .clip(RoundedCornerShape(10.u)).background(C.Bg.copy(alpha = 0.85f))
                    .padding(horizontal = 14.u, vertical = 8.u)
            ) { Clock() }
        }

        if (barVisible && !panelOpen) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 48.u, end = 48.u, bottom = 40.u)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.u))
                    .background(C.Bg.copy(alpha = 0.92f))
                    .padding(horizontal = 28.u, vertical = 22.u),
                verticalArrangement = Arrangement.spacedBy(16.u),
            ) {
                // Timeline row. Highlighted when ◀ / ▶ seek.
                val timelineActive = !onButtons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(fmtTime(position), size = 14, weight = FontWeight.SemiBold, color = if (timelineActive) C.Text else C.Muted, modifier = Modifier.width(84.u))
                    val frac = if (duration > 0) position.toFloat() / duration else 0f
                    ProgressBar(
                        frac,
                        track = C.Line2,
                        fill = if (timelineActive) C.Text else C.Muted,
                        height = if (timelineActive) 6.u else 4.u,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.u))
                    T(if (duration > 0) "−" + fmtTime(duration - position) else "", size = 14, color = C.Muted, modifier = Modifier.width(84.u))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.u)) {
                    buttons.forEachIndexed { i, b ->
                        val focused = onButtons && i == button
                        val label = if (b == VodButton.PLAY) (if (playing) "Pause" else "Play") else b.label
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.u))
                                .background(if (focused) C.Text else C.Line2.copy(alpha = 0.6f))
                                .padding(horizontal = 16.u, vertical = 9.u),
                        ) {
                            T(label, size = 14, weight = FontWeight.SemiBold, color = if (focused) C.OnFocus else C.Text)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    T(
                        if (onButtons) "◀ ▶ Choose   ·   OK Select   ·   ▲ Timeline"
                        else "◀ ▶ 10 s (hold = faster)   ·   OK Play / pause   ·   ▼ Buttons",
                        size = 12, color = C.Dim,
                    )
                }
            }
        }

        if (panelOpen) {
            TrackPanel(
                vm = vm,
                tick = tracksTick,
                current = { vm.prefs.vodTrackChoice() },
                onSave = { a, t -> vm.prefs.saveVodTrackChoice(a, t) },
                onChanged = { tracksTick++ },
                note = "Used for all movies & series.  Back to close.",
                noSubs = "This title has no subtitles.",
                modifier = Modifier.align(Alignment.CenterEnd),
            )
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
