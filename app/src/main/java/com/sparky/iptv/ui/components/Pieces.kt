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
fun PlayerSurface(modifier: Modifier = Modifier, withControls: Boolean = false) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = withControls
                player = PlayerHolder.get(ctx)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(AColor.BLACK)
                setBackgroundColor(AColor.BLACK)
                keepScreenOn = true
                if (withControls) {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    controllerShowTimeoutMs = 3500
                    post {
                        requestFocus()
                        showController()
                    }
                } else {
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            }
        },
        onRelease = { it.player = null },
    )
}

@Composable
fun Toast(text: String?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (text == null) return
    LaunchedEffect(text) {
        delay(2000)
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
