package com.sparky.iptv.ui.components

import android.graphics.Color as AColor
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.player.SubStyle
import com.sparky.iptv.player.SubtitleStyleState
import kotlinx.coroutines.delay

/** Channel logo, falling back to a monogram tile when there is no logo (or it fails). */
@Composable
fun ChannelLogo(name: String, url: String?, size: Dp, focused: Boolean = false) {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    val bg = if (focused) C.OnFocus else C.Raised
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.u)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            T(monogram(name), size = 12, weight = FontWeight.Bold, color = if (focused) C.Text else C.Text2)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size * 0.86f),
                onError = { failed = true },
            )
        }
    }
}

/** Poster image; shows the title on a quiet tile while there is no artwork. */
@Composable
fun Poster(title: String, url: String?, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    Box(modifier.background(C.Raised), contentAlignment = Alignment.BottomStart) {
        if (failed) {
            T(title, size = 15, weight = FontWeight.Bold, maxLines = 3, modifier = Modifier.padding(14.u))
        } else {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { failed = true },
            )
        }
    }
}

/** The video surface. The one shared player is attached while this is on screen. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                // Our own Compose controls handle the remote, so the stock controller is off and the view never takes focus.
                useController = false
                player = PlayerHolder.get(ctx)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(AColor.BLACK)
                setBackgroundColor(AColor.BLACK)
                keepScreenOn = true
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        // Reading the shared style here re-applies it whenever it changes. Reading the generation
        // re-attaches the view after the sleep timer released the player.
        update = {
            @Suppress("UNUSED_VARIABLE") val gen = PlayerHolder.generation
            val p = PlayerHolder.get(it.context)
            if (it.player !== p) it.player = p
            SubStyle.apply(it.subtitleView, SubtitleStyleState.style)
        },
        onRelease = { it.player = null },
    )
}

@Composable
fun Toast(text: String?, onDone: () -> Unit, modifier: Modifier = Modifier, durationMs: Long = 2_000L) {
    if (text == null) return
    LaunchedEffect(text) {
        delay(durationMs)
        onDone()
    }
    Box(
        modifier
            .clip(RoundedCornerShape(999.u))
            .background(Color(0xF01E2024)),
    ) {
        T(text, size = 15, weight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.u, vertical = 12.u))
    }
}
