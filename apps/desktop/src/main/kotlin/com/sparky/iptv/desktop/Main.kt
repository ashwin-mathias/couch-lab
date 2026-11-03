package com.sparky.iptv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.ui.App
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.platform.BackDispatcher
import com.sparky.iptv.ui.platform.Platform
import com.sparky.iptv.ui.platform.tv

/**
 * Sparky IPTV for Windows and Mac (3.1). The TV layout (960 × 540 design units) is scaled to fill
 * the window, letterboxed to 16:9. Keyboard: arrows = D-pad, Enter = OK (hold for the long press),
 * Esc = Back, Space = play/pause, Page Up/Down = channel, M = audio & subtitles, F11 = full screen.
 */
fun main() = application {
    val platform = remember {
        DesktopPlatform(onExit = ::exitApplication).also {
            Platform.app = it
            DesktopPlatform.init(it)
            PlayerHolder.factory = { VlcEngine() }
        }
    }
    val vm = remember { AppViewModel() }
    val state = rememberWindowState(size = DpSize(1280.dp, 760.dp))
    Window(
        onCloseRequest = { PlayerHolder.release(); exitApplication() },
        title = "Sparky IPTV",
        icon = painterResource("drawable/logo.webp"),
        state = state,
        onPreviewKeyEvent = { ev ->
            when {
                ev.key == Key.F11 && ev.type == KeyEventType.KeyDown -> {
                    state.placement = if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                    true
                }
                vm.onAnyKey(ev.tv()) -> true
                ev.key == Key.Escape -> {
                    // Back on key release, like the remote.
                    if (ev.type == KeyEventType.KeyUp && !BackDispatcher.back() && state.placement == WindowPlacement.Fullscreen) {
                        state.placement = WindowPlacement.Floating
                    }
                    true
                }
                else -> false
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val scale = minOf(maxWidth.value / 960f, maxHeight.value / 540f)
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
                Box(Modifier.requiredSize(960.dp, 540.dp)) { App(vm) }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") platform
}
