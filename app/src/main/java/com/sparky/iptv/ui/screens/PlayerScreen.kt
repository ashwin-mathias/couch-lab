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
import androidx.compose.runtime.mutableLongStateOf
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
import com.sparky.iptv.player.PlaybackErrors
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

/** Buttons on the live control bar while paused or behind live. */
private enum class LiveButton { PLAY, START, LIVE, TRACKS, SLEEP }

/**
 * Full-screen live TV.
 *  - At live: ▲▼ change channel, ◀ back to the page you came from, ▶ audio & subtitles,
 *    OK pause, hold OK favourite.
 *  - Paused or behind live: a control bar like the movie player. ◀ ▶ rewind / forward (catch-up
 *    channels), ▼ buttons (Play/Pause, Start of show, Go live, Audio & subtitles, Sleep), OK play/pause.
 *    Back always leaves. CH+/CH− still change channel.
 */
@Composable
private fun LivePlayer(vm: AppViewModel, req: PlayRequest.Live) {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.get(ctx) }
    val channels = req.channels
    var index by remember { mutableIntStateOf(req.index.coerceIn(0, channels.lastIndex)) }
    var previous by remember { mutableIntStateOf(-1) }
    var showInfo by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var tracksTick by remember { mutableIntStateOf(0) }
    var infoTick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var triedFallback by remember { mutableStateOf(false) }
    // Pause / catch-up state.
    var shiftStart by remember { mutableStateOf<Long?>(null) }   // start of the recording being played; null = live
    var paused by remember { mutableStateOf(false) }             // paused (live or recording)
    var pausedAt by remember { mutableLongStateOf(0L) }          // wall-clock time of a live pause
    var resumedLiveAt by remember { mutableLongStateOf(0L) }
    var pendingOffset by remember { mutableLongStateOf(0L) }     // sub-minute seek once a recording is ready
    var shiftRetried by remember { mutableStateOf(false) }
    var fmtIdx by remember { mutableIntStateOf(0) }                 // which recording link format is in use
    var watchTick by remember { mutableIntStateOf(0) }              // starts the "is it playing?" check
    var connRetried by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(false) }
    var onButtons by remember { mutableStateOf(false) }
    var button by remember { mutableIntStateOf(0) }
    var barTick by remember { mutableIntStateOf(0) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekTick by remember { mutableIntStateOf(0) }
    var clockTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Channel switch timing.
    var switchStartedAt by remember { mutableLongStateOf(0L) }
    var firstPlay by remember { mutableStateOf(true) }

    val ch = channels[index]
    val now = rememberNow(15_000L)
    val focus = remember { FocusRequester() }
    val canShift = ch.archiveDays > 0 && ch.directUrl == null
    val behind = shiftStart != null || paused

    fun wallNow() = System.currentTimeMillis()
    /** The moment of the broadcast on screen. */
    fun watchedTime(): Long = shiftStart?.let { it + player.currentPosition } ?: if (paused) pausedAt else wallNow()

    fun leave() {
        val hasPreview = vm.section in setOf(Section.LIVE, Section.FAVORITES, Section.RECENT)
        if (hasPreview && !behind) vm.liveHandoff = true else PlayerHolder.stop()
        vm.pop()
    }
    fun showBar(buttonsRow: Boolean? = null) {
        if (buttonsRow != null) onButtons = buttonsRow
        barVisible = true
        barTick++
    }
    fun goLive() {
        shiftStart = null
        paused = false
        seekTarget = null
        barVisible = false
        error = null
        PlayerHolder.play(ctx, ch.url, force = true)
    }
    /** Plays the recording from [t] (catch-up channels only). Near "now" it simply goes live. */
    fun startShift(t: Long, formatIndex: Int = 0) {
        val n = wallNow()
        val earliest = n - ch.archiveDays * 24 * 3_600_000L
        val target = t.coerceAtLeast(earliest)
        if (!canShift || target >= n - 30_000) { goLive(); return }
        val start = target - target % 60_000
        val minutes = ((n - start) / 60_000 + 60).toInt().coerceIn(30, 24 * 60)
        val formats = com.sparky.iptv.data.StreamUrls.catchupFormats()
        fmtIdx = formatIndex.coerceIn(0, formats.lastIndex)
        val fmt = formats[fmtIdx]
        shiftStart = start
        paused = false
        pendingOffset = target - start
        error = null
        com.sparky.iptv.data.AppLog.i("Catch-up: ${com.sparky.iptv.data.StreamUrls.formatName(fmt)} from ${(n - start) / 60_000} min ago on channel ${ch.num}")
        PlayerHolder.play(ctx, com.sparky.iptv.data.StreamUrls.catchup(ch.id, start, minutes, fmt), force = true)
        watchTick++
    }
    fun togglePause() {
        when {
            shiftStart != null -> {
                player.playWhenReady = !player.playWhenReady
                paused = !player.playWhenReady
            }
            !paused -> {
                player.playWhenReady = false
                paused = true
                pausedAt = wallNow()
                com.sparky.iptv.data.AppLog.i("Paused live on channel ${ch.num}${if (canShift) " (catch-up)" else " (no catch-up)"}")
            }
            // Resume a live pause: from the recording if it's been a while, else straight on.
            canShift && wallNow() - pausedAt > 15_000 -> startShift(pausedAt)
            else -> {
                paused = false
                resumedLiveAt = wallNow()
                player.playWhenReady = true
                com.sparky.iptv.data.AppLog.i("Resumed live on channel ${ch.num} after ${(wallNow() - pausedAt) / 1000} s")
                watchTick++
            }
        }
    }
    fun seekBy(delta: Long) {
        if (!canShift) return
        val base = seekTarget ?: watchedTime()
        val target = (base + delta).coerceAtLeast(wallNow() - ch.archiveDays * 24 * 3_600_000L)
        if (target >= wallNow() - 30_000) { goLive(); return }
        seekTarget = target
        seekTick++
    }
    fun switchTo(i: Int) {
        if (i == index) return
        previous = index
        index = i
        shiftStart = null
        paused = false
        barVisible = false
        seekTarget = null
        switchStartedAt = android.os.SystemClock.elapsedRealtime()
    }

    val currentShow = remember(ch.id, clockTick, shiftStart, paused) {
        val w = watchedTime()
        vm.epg[ch.id].orEmpty().firstOrNull { w >= it.start && w < it.end }
    }
    val buttons = remember(canShift, currentShow != null) {
        LiveButton.entries.filter { it != LiveButton.START || (canShift && currentShow != null) }
    }
    LaunchedEffect(buttons) { button = button.coerceIn(0, buttons.lastIndex) }
    fun press(b: LiveButton) {
        when (b) {
            LiveButton.PLAY -> togglePause()
            LiveButton.START -> currentShow?.let { startShift(it.start) }
            LiveButton.LIVE -> goLive()
            LiveButton.TRACKS -> panelOpen = true
            LiveButton.SLEEP -> sleepOpen = true
        }
    }

    // Back always leaves; it closes the audio & subtitles or sleep panel first.
    BackHandler { leave() }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = sleepOpen) { sleepOpen = false }

    // The info bar updates at once; the stream starts once the remote has settled (300 ms),
    // so scrolling past channels opens no streams.
    LaunchedEffect(ch.id) {
        error = null
        triedFallback = false
        shiftRetried = false
        connRetried = false
        vm.ensureEpg(ch)
        showInfo = true
        infoTick++
        if (!firstPlay) delay(300)
        firstPlay = false
        PlayerHolder.play(ctx, ch.url)
        vm.prefs.lastChannel = ch.id
    }
    LaunchedEffect(ch.id) {
        delay(10_000)
        vm.addRecent("live", ch.id)
    }
    LaunchedEffect(infoTick) {
        delay(5000)
        showInfo = false
    }
    // The control bar hides after 5 s while playing; it stays while paused.
    LaunchedEffect(barTick, barVisible, panelOpen, sleepOpen, paused) {
        if (barVisible && !panelOpen && !sleepOpen && !paused) {
            delay(5000)
            barVisible = false
        }
    }
    // A run of ◀ / ▶ presses becomes one jump.
    LaunchedEffect(seekTick) {
        if (seekTick == 0) return@LaunchedEffect
        delay(600)
        val t = seekTarget ?: return@LaunchedEffect
        val s = shiftStart
        if (s != null && t >= s && player.isCurrentMediaItemSeekable) player.seekTo(t - s) else startShift(t)
        seekTarget = null
    }
    // Never just sit there: if a recording or a resumed pause isn't playing within 10 s,
    // try the next recording link format, then go live with a message.
    LaunchedEffect(watchTick) {
        if (watchTick == 0) return@LaunchedEffect
        delay(10_000)
        if (paused || player.isPlaying) return@LaunchedEffect
        val state = when (player.playbackState) {
            Player.STATE_BUFFERING -> "still buffering"
            Player.STATE_IDLE -> "idle"
            Player.STATE_ENDED -> "ended"
            else -> "not playing"
        }
        val s = shiftStart
        if (s != null) {
            val formats = com.sparky.iptv.data.StreamUrls.catchupFormats()
            com.sparky.iptv.data.AppLog.w("Catch-up stalled on channel ${ch.num}: ${com.sparky.iptv.data.StreamUrls.formatName(formats[fmtIdx])} $state after 10 s")
            if (fmtIdx < formats.lastIndex) startShift(watchedTime().coerceAtLeast(s), fmtIdx + 1)
            else { goLive(); vm.toast = "Couldn't play the recording ($state) — back to live" }
        } else {
            com.sparky.iptv.data.AppLog.w("Live resume stalled on channel ${ch.num}: $state after 10 s; going live")
            goLive()
            vm.toast = "Couldn't resume ($state) — back to live"
        }
    }
    // Keep the times on screen current while behind live.
    LaunchedEffect(behind) {
        while (behind) {
            clockTick = wallNow()
            delay(500)
        }
    }
    DisposableEffect(ch.id) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val detail = PlaybackErrors.logDetail(e)
                if (shiftStart != null) {
                    // A recording stopped: try once more from where we were, then go live.
                    val formats = com.sparky.iptv.data.StreamUrls.catchupFormats()
                    if (fmtIdx < formats.lastIndex) {
                        com.sparky.iptv.data.AppLog.w("Catch-up failed on channel ${ch.num} with ${com.sparky.iptv.data.StreamUrls.formatName(formats[fmtIdx])} ($detail); trying the next format")
                        startShift(watchedTime(), fmtIdx + 1)
                    } else if (!shiftRetried) {
                        shiftRetried = true
                        com.sparky.iptv.data.AppLog.w("Catch-up stopped on channel ${ch.num} ($detail); retrying")
                        startShift(watchedTime())
                    } else {
                        com.sparky.iptv.data.AppLog.e("Catch-up failed on channel ${ch.num}: $detail")
                        goLive()
                        vm.toast = PlaybackErrors.message(e, "recording")
                    }
                    return
                }
                // Resuming a long live pause on a channel without catch-up.
                if (wallNow() - resumedLiveAt < 10_000 || e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    resumedLiveAt = 0L
                    goLive()
                    vm.toast = "This channel can't pause that long — back to live"
                    return
                }
                // The provider may still be counting the stream we just closed: wait 3 s and try once more.
                if (PlaybackErrors.httpCode(e) in setOf(429, 458, 509) && !connRetried) {
                    connRetried = true
                    com.sparky.iptv.data.AppLog.w("Channel ${ch.num}: provider says too many connections ($detail); retrying in 3 s")
                    val url = ch.url
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (channels.getOrNull(index)?.url == url && shiftStart == null) PlayerHolder.play(ctx, url, force = true)
                    }, 3000)
                    return
                }
                // Some providers only serve one of HLS / MPEG-TS; try the other once (not for login or connection limits).
                if (!triedFallback && !PlaybackErrors.isAccountProblem(e)) {
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
                com.sparky.iptv.data.AppLog.e("Live playback failed on channel ${ch.num} ${ch.name}: $detail")
                error = PlaybackErrors.message(e, "channel")
                showInfo = true
                infoTick++
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    error = null
                    if (shiftStart != null) {
                        val fmt = com.sparky.iptv.data.StreamUrls.catchupFormats()[fmtIdx]
                        if (fmt != com.sparky.iptv.data.StreamUrls.catchupFormat) {
                            com.sparky.iptv.data.StreamUrls.catchupFormat = fmt
                            vm.prefs.catchupFormat = fmt
                            fmtIdx = 0   // the working format is now first in the list
                        }
                    }
                    if (shiftStart != null && pendingOffset > 1000 && player.isCurrentMediaItemSeekable) {
                        player.seekTo(pendingOffset)
                    }
                    pendingOffset = 0L
                }
                // The recording ran out (it was requested up to about now): carry on live.
                if (state == Player.STATE_ENDED && shiftStart != null) startShift(watchedTime())
            }
            override fun onRenderedFirstFrame() {
                if (switchStartedAt > 0) {
                    vm.recordSwitch(android.os.SystemClock.elapsedRealtime() - switchStartedAt)
                    switchStartedAt = 0L
                }
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
    LaunchedEffect(panelOpen, sleepOpen) {
        if (!panelOpen && !sleepOpen) {
            delay(80)
            runCatching { focus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (panelOpen || sleepOpen) return@onPreviewKeyEvent false
                val ne = ev.nativeKeyEvent
                val code = ne.keyCode
                val step = when {
                    ne.repeatCount >= 20 -> 60_000L
                    ne.repeatCount >= 6 -> 30_000L
                    else -> 10_000L
                }
                when (ev.type) {
                    KeyEventType.KeyDown -> when (code) {
                        AKey.KEYCODE_CHANNEL_UP -> { switchTo((index + 1) % channels.size); true }
                        AKey.KEYCODE_CHANNEL_DOWN -> { switchTo((index - 1 + channels.size) % channels.size); true }
                        AKey.KEYCODE_LAST_CHANNEL -> {
                            if (previous in channels.indices) switchTo(previous)
                            true
                        }
                        AKey.KEYCODE_DPAD_UP -> {
                            if (behind) showBar(if (barVisible) false else onButtons) else switchTo((index + 1) % channels.size)
                            true
                        }
                        AKey.KEYCODE_DPAD_DOWN -> {
                            if (behind) showBar(if (barVisible) true else onButtons) else switchTo((index - 1 + channels.size) % channels.size)
                            true
                        }
                        AKey.KEYCODE_DPAD_LEFT -> {
                            when {
                                !behind -> leave()
                                barVisible && onButtons -> { if (ne.repeatCount == 0) button = (button - 1).coerceAtLeast(0); showBar() }
                                else -> { seekBy(-step); showBar(false) }
                            }
                            true
                        }
                        AKey.KEYCODE_DPAD_RIGHT -> {
                            when {
                                !behind -> { panelOpen = true; showInfo = true; infoTick++ }
                                barVisible && onButtons -> { if (ne.repeatCount == 0) button = (button + 1).coerceAtMost(buttons.lastIndex); showBar() }
                                else -> { seekBy(step); showBar(false) }
                            }
                            true
                        }
                        AKey.KEYCODE_MENU, AKey.KEYCODE_CAPTIONS -> {
                            panelOpen = true
                            showInfo = true
                            infoTick++
                            true
                        }
                        AKey.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePause(); showBar(); true }
                        AKey.KEYCODE_MEDIA_PAUSE -> { if (!paused) togglePause(); showBar(); true }
                        AKey.KEYCODE_MEDIA_PLAY -> { if (paused) togglePause(); showBar(); true }
                        AKey.KEYCODE_MEDIA_REWIND -> { seekBy(-step); showBar(false); true }
                        AKey.KEYCODE_MEDIA_FAST_FORWARD -> { if (behind) seekBy(step); showBar(false); true }
                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> true
                        else -> false
                    }
                    KeyEventType.KeyUp -> when (code) {
                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> {
                            val held = ne.eventTime - ne.downTime
                            when {
                                held > 600 -> vm.holdFavoriteNow(ch.favKey, ch.display)   // hold OK = favourite (menu if it is one)
                                behind && barVisible && onButtons -> { buttons.getOrNull(button)?.let { press(it) }; showBar() }
                                else -> { togglePause(); showBar(false) }                // OK = pause / play
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

        if ((showInfo || behind) && !sleepOpen) {
            PlayerClock(vm, Modifier.align(Alignment.TopEnd))
        }

        if (behind) {
            // Small reminder at the top left while not at live.
            val t0 = clockTick   // read so this redraws every half second
            val lag = (t0.coerceAtLeast(wallNow()) - (seekTarget ?: watchedTime())).coerceAtLeast(0L)
            Row(
                Modifier.align(Alignment.TopStart).padding(top = 32.u, start = 48.u)
                    .clip(RoundedCornerShape(10.u)).background(C.Bg.copy(alpha = 0.85f))
                    .padding(horizontal = 14.u, vertical = 8.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(if (paused) "PAUSED" else "BEHIND LIVE", size = 12, weight = FontWeight.Bold, color = C.Accent, letterSpacing = 0.08f)
                Spacer(Modifier.width(10.u))
                T(fmtTime(lag), size = 15, weight = FontWeight.SemiBold, color = C.Text2)
            }
        }

        if (behind && barVisible && !panelOpen && !sleepOpen) {
            val t0 = clockTick
            val w = seekTarget ?: watchedTime()
            val lag = (t0.coerceAtLeast(wallNow()) - w).coerceAtLeast(0L)
            val show = vm.epg[ch.id].orEmpty().firstOrNull { w >= it.start && w < it.end }
            val frac = show?.let { (w - it.start).toFloat() / (it.end - it.start).coerceAtLeast(1) } ?: 1f
            ControlBar(
                heading = "${ch.num}  ${ch.display}" + (show?.let { "   ·   ${it.title}" } ?: ""),
                left = fmtClock(w),
                right = "−" + fmtTime(lag) + " behind live",
                fraction = frac,
                timelineActive = !onButtons,
                buttons = buttons.map {
                    when (it) {
                        LiveButton.PLAY -> if (paused) "Play" else "Pause"
                        LiveButton.START -> "Start of show"
                        LiveButton.LIVE -> "Go live"
                        LiveButton.TRACKS -> "Audio & subtitles"
                        LiveButton.SLEEP -> "Sleep"
                    }
                },
                selected = if (onButtons) button else -1,
                hint = when {
                    onButtons -> "◀ ▶ Choose   ·   OK Select   ·   ▲ Timeline"
                    canShift -> "◀ ▶ Rewind / forward   ·   OK Play / pause   ·   ▼ Buttons"
                    else -> "Pause only · no rewind   ·   OK Play / pause   ·   ▼ Buttons"
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (showInfo && !behind) {
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
                        if (canShift) {
                            Spacer(Modifier.width(12.u))
                            T("CATCH-UP ${ch.archiveDays}D", size = 11, weight = FontWeight.Bold, color = C.Dim, letterSpacing = 0.06f)
                        }
                        Spacer(Modifier.weight(1f))
                        T("${index + 1} of ${channels.size}", size = 13, color = C.Dim)
                    }
                    T(error ?: cur?.title ?: ch.display, size = if (error != null) 20 else 24, weight = FontWeight.Bold, maxLines = 2)
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
                        T("▲▼ Channel   ·   OK Pause   ·   ◀ Back   ·   ▶ Audio & subtitles   ·   Hold OK ♥", size = 13, color = C.Dim)
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
                onSleep = { panelOpen = false; sleepOpen = true },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (sleepOpen) {
            SleepPanel(vm, onDone = { sleepOpen = false }, modifier = Modifier.align(Alignment.CenterEnd))
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
    onSleep: () -> Unit,
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

        // Also here, so the sleep timer can be set at live without pausing first.
        Spacer(Modifier.height(10.u))
        TrackRow("Sleep timer…", selected = false) { onSleep() }
        val left = sleepLeftText(vm)
        T(left?.let { "Sleep in $it" } ?: "Off", size = 12, color = C.Dim, modifier = Modifier.padding(start = 38.u))
    }
}

/** "42 min" while the sleep timer runs (rounded up), else null. Refreshes every 15 s. */
@Composable
private fun sleepLeftText(vm: AppViewModel): String? {
    @Suppress("UNUSED_VARIABLE") val tick = rememberNow(15_000L)
    if (vm.sleepMinutes == 0) return null
    val left = vm.sleepLeftMs()
    return "${((left + 59_999L) / 60_000L).coerceAtLeast(1L)} min"
}

/** The clock at the top right of the player, with "Sleep in 42 min" beside it while the timer runs. */
@Composable
private fun PlayerClock(vm: AppViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 32.u, end = 48.u)
            .clip(RoundedCornerShape(10.u)).background(C.Bg.copy(alpha = 0.85f))
            .padding(horizontal = 14.u, vertical = 8.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sleepLeftText(vm)?.let {
            T("Sleep in $it", size = 13, weight = FontWeight.SemiBold, color = C.Text2)
            T("   ·   ", size = 13, color = C.Dim)
        }
        Clock()
    }
}

/**
 * "Sleep timer" panel: Off · 30 · 60 · 90 min. Focus starts on the running choice, or on the
 * default from Settings when no timer runs. OK starts it and closes; Back closes unchanged.
 */
@Composable
private fun SleepPanel(vm: AppViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val choices = listOf(0) + com.sparky.iptv.data.SLEEP_CHOICES
    val start = if (vm.sleepMinutes > 0) vm.sleepMinutes else vm.prefs.sleepDefault
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    val left = sleepLeftText(vm)
    Column(
        modifier.fillMaxHeight().width(380.u).background(C.Bg.copy(alpha = 0.94f))
            .padding(start = 24.u, end = 40.u, top = 40.u, bottom = 40.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        T("Sleep timer", size = 20, weight = FontWeight.Bold)
        T(
            (left?.let { "Stops playback in $it.  " } ?: "Stops playback after the time you choose.  ") + "Back to close.",
            size = 12, color = C.Dim, maxLines = 2, modifier = Modifier.padding(bottom = 10.u),
        )
        choices.forEach { m ->
            TrackRow(
                if (m == 0) "Off" else "$m min",
                selected = m == vm.sleepMinutes,
                modifier = if (m == start) Modifier.focusRequester(first) else Modifier,
            ) {
                vm.setSleep(m)
                onDone()
            }
        }
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
    PLAY("Pause"), BACK("Back 10s"), FORWARD("Forward 10s"), TRACKS("Audio & subtitles"), SLEEP("Sleep"),
    NEXT("Next episode"), RESTART("Start over")
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
    var sleepOpen by remember { mutableStateOf(false) }
    var tracksTick by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekTick by remember { mutableIntStateOf(0) }
    var appliedFor by remember { mutableStateOf<String?>(null) }
    var connRetried by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val buttons = remember(hasNext) { VodButton.entries.filter { it != VodButton.NEXT || hasNext } }
    LaunchedEffect(buttons) { button = button.coerceIn(0, buttons.lastIndex) }

    fun save() {
        if (!item.saveProgress) return
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
            VodButton.SLEEP -> sleepOpen = true
            VodButton.NEXT -> { save(); if (hasNext) idx++ }
            VodButton.RESTART -> { player.seekTo(0); position = 0; player.playWhenReady = true; playing = true }
        }
    }

    BackHandler { exit() }
    // Registered later, so they take precedence: Back closes the bar, then the panel.
    BackHandler(enabled = barVisible && !panelOpen && !sleepOpen) { barVisible = false }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = sleepOpen) { sleepOpen = false }

    LaunchedEffect(item.key) {
        error = null
        appliedFor = null
        connRetried = false
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
    LaunchedEffect(barTick, barVisible, panelOpen, sleepOpen, playing) {
        if (barVisible && !panelOpen && !sleepOpen && playing) {
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
    LaunchedEffect(panelOpen, sleepOpen) {
        if (!panelOpen && !sleepOpen) {
            delay(80)
            runCatching { focus.requestFocus() }
        }
    }
    DisposableEffect(item.key) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = PlayerHolder.duration()
                if (state == Player.STATE_ENDED) {
                    if (item.saveProgress) vm.saveProgress(item.key, PlayerHolder.duration(), PlayerHolder.duration())
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
                // The provider may still be counting the stream we just closed: wait 3 s and try once more.
                if (PlaybackErrors.httpCode(e) in setOf(429, 458, 509) && !connRetried) {
                    connRetried = true
                    val pos = PlayerHolder.currentPosition()
                    com.sparky.iptv.data.AppLog.w("${item.title}: provider says too many connections (${PlaybackErrors.logDetail(e)}); retrying in 3 s")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (req.items.getOrNull(idx)?.key == item.key) PlayerHolder.play(ctx, item.url, startMs = pos, force = true)
                    }, 3000)
                    return
                }
                com.sparky.iptv.data.AppLog.e("Playback failed for ${item.title}: ${PlaybackErrors.logDetail(e)}")
                error = PlaybackErrors.message(e, if (item.saveProgress) "title" else "recording")
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
                if (panelOpen || sleepOpen) return@onPreviewKeyEvent false
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

        if (barVisible || panelOpen || sleepOpen) {
            // Title, top left; clock, top right.
            Column(
                Modifier.align(Alignment.TopStart).padding(top = 32.u, start = 48.u, end = 480.u),
                verticalArrangement = Arrangement.spacedBy(4.u),
            ) {
                T(item.title, size = 20, weight = FontWeight.Bold)
                if (item.subtitle.isNotBlank()) T(item.subtitle, size = 14, color = C.Text2)
            }
            if (!panelOpen && !sleepOpen) PlayerClock(vm, Modifier.align(Alignment.TopEnd))
        }

        if (barVisible && !panelOpen && !sleepOpen) {
            ControlBar(
                heading = null,
                left = fmtTime(position),
                right = if (duration > 0) "−" + fmtTime(duration - position) else "",
                fraction = if (duration > 0) position.toFloat() / duration else 0f,
                timelineActive = !onButtons,
                buttons = buttons.map { b -> if (b == VodButton.PLAY) (if (playing) "Pause" else "Play") else b.label },
                selected = if (onButtons) button else -1,
                hint = if (onButtons) "◀ ▶ Choose   ·   OK Select   ·   ▲ Timeline"
                    else "◀ ▶ 10 s (hold = faster)   ·   OK Play / pause   ·   ▼ Buttons",
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
                onSleep = { panelOpen = false; sleepOpen = true },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (sleepOpen) {
            SleepPanel(vm, onDone = { sleepOpen = false }, modifier = Modifier.align(Alignment.CenterEnd))
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

/**
 * Bottom control card shared by the movie player and live TV when paused or behind live:
 * an optional heading, a timeline row and a row of buttons. Focus is virtual: [selected] is the
 * highlighted button (-1 = the timeline row is active).
 */
@Composable
private fun ControlBar(
    heading: String?,
    left: String,
    right: String,
    fraction: Float,
    timelineActive: Boolean,
    buttons: List<String>,
    selected: Int,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(start = 48.u, end = 48.u, bottom = 40.u)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.u))
            .background(C.Bg.copy(alpha = 0.92f))
            .padding(horizontal = 28.u, vertical = 22.u),
        verticalArrangement = Arrangement.spacedBy(16.u),
    ) {
        if (heading != null) T(heading, size = 15, weight = FontWeight.SemiBold, color = C.Text2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(left, size = 14, weight = FontWeight.SemiBold, color = if (timelineActive) C.Text else C.Muted, modifier = Modifier.width(96.u))
            ProgressBar(
                fraction,
                track = C.Line2,
                fill = if (timelineActive) C.Text else C.Muted,
                height = if (timelineActive) 6.u else 4.u,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.u))
            T(right, size = 14, color = C.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.u)) {
            buttons.forEachIndexed { i, label ->
                val focused = i == selected
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
            T(hint, size = 12, color = C.Dim)
        }
    }
}
