@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.sparky.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.sparky.iptv.R

/*
 * Android TV's versions of the components drawn with androidx.tv Material (3.1: moved here unchanged
 * from Theme.kt). The shared screens call them by name; the desktop app has its own versions.
 */

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
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
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        style = TextStyle(
            fontFamily = Inter,
            fontSize = size.su,
            fontWeight = weight,
            color = color,
            letterSpacing = if (letterSpacing != 0f) (letterSpacing * size * 0.75f).sp else TextUnit.Unspecified,
            lineHeight = if (lineHeight != 0f) (lineHeight * size * 0.75f).sp else TextUnit.Unspecified,
        ),
    )
}

/**
 * Focusable row/pill/button. When focused it fills with the light colour and its content
 * turns dark, which reads clearly from across the room.
 */
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
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus?.invoke()
        },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) C.Raised else container,
            contentColor = C.Text,
            focusedContainerColor = focusedContainer,
            focusedContentColor = C.OnFocus,
            pressedContainerColor = focusedContainer,
            pressedContentColor = C.OnFocus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = scale),
    ) {
        content(focused)
    }
}

/** Poster/thumbnail card: a light outline and a gentle lift when focused. */
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
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus?.invoke()
        },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = C.Raised,
            contentColor = C.Text,
            focusedContainerColor = C.Raised,
            focusedContentColor = C.Text,
            pressedContainerColor = C.Raised,
            pressedContentColor = C.Text,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.u, C.Text), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        content(focused)
    }
}


/** The app's colour theme around everything (was in App.kt). */
@Composable
fun SparkyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = C.Bg, surface = C.Bg, onSurface = C.Text)) { content() }
}

/** Text with mixed styles (About page). */
@Composable
fun RichText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = text, style = style, modifier = modifier)
}
