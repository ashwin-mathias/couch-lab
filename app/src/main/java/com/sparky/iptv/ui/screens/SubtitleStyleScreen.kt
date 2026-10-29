package com.sparky.iptv.ui.screens

import android.view.KeyEvent as AKey
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.sparky.iptv.player.SubStyle
import com.sparky.iptv.player.SubtitleStyleState
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

/** Settings → Subtitle style: the options on the left, a live preview on the right. */
@Composable
fun SubtitleStyleScreen(vm: AppViewModel) {
    BackHandler { vm.pop() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = 64.u, vertical = 44.u), horizontalArrangement = Arrangement.spacedBy(48.u)) {
        Column(Modifier.width(460.u).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.u)) {
            T("Subtitle style", size = 30, weight = FontWeight.Bold)
            T(
                "Used for movies, series and live TV. ◀ ▶ to change, OK to step through.",
                size = 14, color = C.Muted, maxLines = 2, lineHeight = 1.5f,
                modifier = Modifier.padding(bottom = 12.u),
            )
            SubtitleStyleOptions(vm, first)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.u)) {
            Label("Preview", modifier = Modifier.padding(top = 10.u))
            SubtitlePreview(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.u)))
            T("Shown over a bright scene, the hardest case for reading subtitles.", size = 13, color = C.Dim)
        }
    }
}

/** The five style options plus Reset. Shared by Settings and the player's Audio & subtitles panel. */
@Composable
fun SubtitleStyleOptions(vm: AppViewModel, first: FocusRequester) {
    val s = SubtitleStyleState.style
    Column(verticalArrangement = Arrangement.spacedBy(6.u)) {
        OptionRow("Size", SubStyle.SIZES, s.size, { vm.setSubStyle(s.copy(size = it)) }, Modifier.focusRequester(first))
        OptionRow("Text colour", SubStyle.COLORS, s.color, { vm.setSubStyle(s.copy(color = it)) })
        OptionRow("Background", SubStyle.BACKGROUNDS, s.background, { vm.setSubStyle(s.copy(background = it)) })
        OptionRow("Edge", SubStyle.EDGES, s.edge, { vm.setSubStyle(s.copy(edge = it)) })
        OptionRow("Position", SubStyle.POSITIONS, s.position, { vm.setSubStyle(s.copy(position = it)) })
        FocusItem(
            onClick = { vm.setSubStyle(SubStyle()); vm.toast = "Subtitle style reset" },
            shape = RoundedCornerShape(10.u),
            modifier = Modifier.fillMaxWidth().height(46.u).padding(top = 0.u),
        ) { f ->
            T(
                "Reset to default", size = 14, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Muted,
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.u),
            )
        }
    }
}

/** One option: ◀ / ▶ change the value, OK steps to the next one. */
@Composable
private fun OptionRow(label: String, values: List<String>, index: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    FocusItem(
        onClick = { onChange((index + 1) % values.size) },
        container = C.Panel,
        shape = RoundedCornerShape(10.u),
        modifier = modifier
            .fillMaxWidth()
            .height(54.u)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.nativeKeyEvent.keyCode) {
                    AKey.KEYCODE_DPAD_LEFT -> { if (index > 0) onChange(index - 1); true }
                    AKey.KEYCODE_DPAD_RIGHT -> { if (index < values.lastIndex) onChange(index + 1); true }
                    else -> false
                }
            },
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
            T(label, size = 14, color = if (f) C.OnFocusMuted else C.Muted, modifier = Modifier.weight(1f))
            T(if (index > 0) "‹" else " ", size = 18, weight = FontWeight.Bold, color = if (f) C.OnFocus else C.Dim)
            T(
                values[index], size = 15, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text,
                modifier = Modifier.padding(horizontal = 10.u),
            )
            T(if (index < values.lastIndex) "›" else " ", size = 18, weight = FontWeight.Bold, color = if (f) C.OnFocus else C.Dim)
        }
    }
}

/** A sample subtitle drawn by the same view the player uses, so it looks exactly like playback. */
@OptIn(UnstableApi::class)
@Composable
private fun SubtitlePreview(modifier: Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF3C82DC), Color(0xFF66A3EA), Color(0xFFB9D5F2), Color(0xFF7A5A48)))
        )
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SubtitleView(ctx).apply {
                    setCues(listOf(Cue.Builder().setText("This is how subtitles will look,\non bright and dark scenes alike.").build()))
                }
            },
            update = { SubStyle.apply(it, SubtitleStyleState.style) },
        )
    }
}
