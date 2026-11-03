package com.sparky.iptv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sparky.iptv.desktop.VlcEngine
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.player.SubStyle
import com.sparky.iptv.player.SubtitleStyleState
import com.sparky.iptv.ui.platform.tv

/*
 * The desktop's versions of the components Android draws with androidx.tv Material (3.1).
 * They follow DESIGN.md: a focused row fills with the light colour, a focused card gets a light
 * outline and grows 6%. The mouse moves focus on hover and clicks with a tap.
 */

val Inter = FontFamily(
    Font("font/inter_regular.otf", FontWeight.Normal),
    Font("font/inter_medium.otf", FontWeight.Medium),
    Font("font/inter_semibold.otf", FontWeight.SemiBold),
    Font("font/inter_bold.otf", FontWeight.Bold),
)

@Composable
fun T(
    text: String,
    size: Int = 15,
    color: Color = C.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    letterSpacing: Float = 0f,
    lineHeight: Float = 0f,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = Inter,
            fontSize = size.su,
            fontWeight = weight,
            color = color,
            textAlign = textAlign ?: TextAlign.Unspecified,
            letterSpacing = if (letterSpacing != 0f) (letterSpacing * size * 0.75f).sp else TextUnit.Unspecified,
            lineHeight = if (lineHeight != 0f) (lineHeight * size * 0.75f).sp else TextUnit.Unspecified,
        ),
    )
}

@Composable
fun RichText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text = text, style = style, modifier = modifier)
}

@Composable
fun SparkyTheme(content: @Composable () -> Unit) = content()

/** OK (Enter) on a focused item: a press under 600 ms clicks; holding it is the long press, as on the remote. */
private fun Modifier.okKeys(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = this.onKeyEvent { ev ->
    val k = ev.tv()
    val ok = k.keyCode == AKey.KEYCODE_ENTER || k.keyCode == AKey.KEYCODE_NUMPAD_ENTER || k.keyCode == AKey.KEYCODE_DPAD_CENTER
    if (!ok) return@onKeyEvent false
    if (ev.type == KeyEventType.KeyUp) {
        if (k.eventTime - k.downTime > 600 && onLongClick != null) onLongClick() else onClick()
    }
    true
}

/** Mouse: hover focuses, a click clicks, a long press is the long press. */
private fun Modifier.mouse(focus: FocusRequester, onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = this
    .onPointerEvent(PointerEventType.Enter) { runCatching { focus.requestFocus() } }
    .pointerInput(onClick, onLongClick) {
        detectTapGestures(
            onTap = { runCatching { focus.requestFocus() }; onClick() },
            onLongPress = onLongClick?.let { l -> { _: Offset -> l() } },
        )
    }

@Composable
fun FocusItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.u),
    container: Color = Color.Transparent,
    focusedContainer: Color = C.Text,
    selected: Boolean = false,
    scale: Float = 1f,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Box(
        modifier
            .graphicsLayer { if (focused && scale != 1f) { scaleX = scale; scaleY = scale } }
            .clip(shape)
            .background(if (focused) focusedContainer else if (selected) C.Raised else container)
            .focusRequester(focus)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .okKeys(onClick, onLongClick)
            .mouse(focus, onClick, onLongClick)
            .focusable(),
    ) {
        content(focused)
    }
}

@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.u),
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Box(
        modifier
            .graphicsLayer { if (focused) { scaleX = 1.06f; scaleY = 1.06f } }
            .clip(shape)
            .background(C.Raised)
            .then(if (focused) Modifier.border(3.u, C.Text, shape) else Modifier)
            .focusRequester(focus)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .okKeys(onClick, onLongClick)
            .mouse(focus, onClick, onLongClick)
            .focusable(),
    ) {
        content(focused)
    }
}

/** An image from the web (Coil 3). [onError] is called if it can't be loaded. */
@Composable
fun RemoteImage(
    model: String?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    coil3.compose.AsyncImage(
        model = model, contentDescription = contentDescription, contentScale = contentScale,
        modifier = modifier, onError = { onError() },
    )
}

/** The video: libVLC's newest frame, fitted inside the box on black. */
@Composable
fun PlayerSurface(modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val gen = PlayerHolder.generation
    val engine = PlayerHolder.get() as? VlcEngine
    val tick = engine?.frameTick ?: 0
    val image = remember(tick) {
        engine?.frame?.get()?.let { f ->
            runCatching {
                org.jetbrains.skia.Image.makeRaster(
                    org.jetbrains.skia.ImageInfo(f.width, f.height, org.jetbrains.skia.ColorType.BGRA_8888, org.jetbrains.skia.ColorAlphaType.OPAQUE),
                    f.pixels, f.width * 4,
                ).toComposeImageBitmap()
            }.getOrNull()
        }
    }
    Canvas(modifier.background(Color.Black)) {
        val img = image ?: return@Canvas
        val s = minOf(size.width / img.width, size.height / img.height)
        val w = (img.width * s).toInt()
        val h = (img.height * s).toInt()
        drawImage(
            img,
            dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = IntSize(w, h),
        )
    }
}

/**
 * A sample subtitle in the chosen colours. On Windows and Mac, VLC draws the real subtitles in its
 * own style for now; the style setting applies to the Google TV app.
 */
@Composable
fun SubtitleSample(modifier: Modifier) {
    val s = SubtitleStyleState.style
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.padding(bottom = (if (s.position == 1) 60 else 24).u)
                .clip(RoundedCornerShape(4.u))
                .background(Color(SubStyle.BACKGROUND_VALUES[s.background]))
                .padding(horizontal = 10.u, vertical = 4.u),
        ) {
            T(
                "This is how subtitles will look,\non bright and dark scenes alike.",
                size = (22 * SubStyle.SIZE_SCALE[s.size]).toInt(),
                color = Color(SubStyle.COLOR_VALUES[s.color]),
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}
