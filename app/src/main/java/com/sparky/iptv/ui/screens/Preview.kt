package com.sparky.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.data.Channel
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.Clock
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.PlayerSurface
import com.sparky.iptv.ui.components.ProgressBar
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtClock
import com.sparky.iptv.ui.components.fmtShort
import com.sparky.iptv.ui.components.rememberNow
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

/**
 * Plays the highlighted channel in the preview a moment after the highlight settles,
 * and stops it when the page closes (unless full screen is taking over the stream).
 */
@Composable
fun PreviewDriver(vm: AppViewModel, focused: Channel?) {
    val ctx = LocalContext.current
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
}

@Composable
fun ChannelPreviewPane(vm: AppViewModel, ch: Channel?, hasFocus: Boolean, modifier: Modifier) {
    val now = rememberNow()
    Column(modifier.fillMaxHeight().padding(horizontal = 28.u, vertical = 28.u), verticalArrangement = Arrangement.spacedBy(20.u)) {
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
                if (cur != null) "Now · ${fmtShort(cur.start)} – ${fmtClock(cur.end)}" else "${ch.num} · ${ch.display}"
            )
            T(cur?.title ?: ch.display, size = 24, weight = FontWeight.Bold, maxLines = 2)
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
