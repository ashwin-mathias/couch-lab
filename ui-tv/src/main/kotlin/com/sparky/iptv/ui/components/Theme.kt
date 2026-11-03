
package com.sparky.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import com.sparky.iptv.ui.platform.painterRes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparky.iptv.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Design tokens (from the approved mockups). */
object C {
    val Bg = Color(0xFF0E0F11)
    val Panel = Color(0xFF16171A)
    val Raised = Color(0xFF1E2024)
    val Line = Color(0xFF22242A)
    val Line2 = Color(0xFF2A2C31)
    val Text = Color(0xFFEEEBE5)
    val Text2 = Color(0xFFCFCBC3)
    val Muted = Color(0xFFA19D96)
    val Dim = Color(0xFF8A8781)
    val Accent = Color(0xFFE07A5F)
    val Ok = Color(0xFF9DB89A)
    val OnFocus = Color(0xFF0E0F11)
    val OnFocusMuted = Color(0xFF4A4843)
    val TrackOnFocus = Color(0xFFCFCBC3)
}

/**
 * The mockups were drawn on a 1280x720 canvas; a TV lays out at 960x540dp.
 * `u` and `su` convert mockup pixels into the right dp / sp so the app matches the design.
 */
val Int.u: Dp get() = (this * 0.75f).dp
val Float.u: Dp get() = (this * 0.75f).dp
val Int.su: TextUnit get() = (this * 0.75f).sp

/** Small uppercase section label, as used throughout the design. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.Dim) {
    T(text.uppercase(), size = 12, color = color, weight = FontWeight.SemiBold, letterSpacing = 0.12f, modifier = modifier)
}

@Composable
fun Icon(res: Int, tint: Color, size: Dp = 22.u, modifier: Modifier = Modifier) {
    Image(
        painter = painterRes(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

@Composable
fun ProgressBar(fraction: Float, track: Color, fill: Color, modifier: Modifier = Modifier, height: Dp = 3.u) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(2.u)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(fill))
    }
}

@Composable
fun rememberNow(periodMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun Clock(modifier: Modifier = Modifier) {
    val now = rememberNow(15_000L)
    T(fmtClock(now), size = 15, weight = FontWeight.SemiBold, color = C.Text2, modifier = modifier)
}

private val clockFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
private val shortFmt = SimpleDateFormat("h:mm", Locale.getDefault())
private val dayFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

fun fmtClock(ms: Long): String = clockFmt.format(Date(ms))
fun fmtShort(ms: Long): String = shortFmt.format(Date(ms))
fun fmtDay(ms: Long): String = dayFmt.format(Date(ms))
fun fmtDate(ms: Long): String = dateFmt.format(Date(ms))

fun fmtLeft(endMs: Long, now: Long): String {
    val mins = ((endMs - now) / 60_000L).coerceAtLeast(0)
    return if (mins >= 60) "${mins / 60}h ${"%02d".format(mins % 60)}m left" else "${mins}m left"
}

fun fmtDuration(secs: Int): String {
    if (secs <= 0) return ""
    val m = secs / 60
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "$m min"
}

/** Two- or three-letter stand-in when a channel has no logo. */
fun monogram(name: String): String {
    val words = name.replace(Regex("[^A-Za-z0-9 ]"), " ").split(" ").filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(3).joinToString("") { it.take(1) }.uppercase()
    }
}
