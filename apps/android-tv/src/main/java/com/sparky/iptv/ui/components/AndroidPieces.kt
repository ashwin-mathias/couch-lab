package com.sparky.iptv.ui.components

import android.graphics.Color as AColor
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.common.text.Cue
import coil.compose.AsyncImage
import com.sparky.iptv.player.ExoEngine
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.player.SubStyle
import com.sparky.iptv.player.SubtitleStyleState
import com.sparky.iptv.player.applyTo

/*
 * Android's video surface and image loader (3.1: moved out of the shared Pieces.kt unchanged).
 */

/** An image from the web (Coil). [onError] is called if it can't be loaded. */
@Composable
fun RemoteImage(
    model: String?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    AsyncImage(model = model, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier, onError = { onError() })
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
                player = (PlayerHolder.get() as ExoEngine).exo
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
            val p = (PlayerHolder.get() as ExoEngine).exo
            if (it.player !== p) it.player = p
            SubtitleStyleState.style.applyTo(it.subtitleView)
        },
        onRelease = { it.player = null },
    )
}


/** A sample subtitle drawn by ExoPlayer's subtitle view, so it looks exactly like playback. */
@OptIn(UnstableApi::class)
@Composable
fun SubtitleSample(modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SubtitleView(ctx).apply {
                setCues(listOf(Cue.Builder().setText("This is how subtitles will look,\non bright and dark scenes alike.").build()))
            }
        },
        update = { SubtitleStyleState.style.applyTo(it) },
    )
}
