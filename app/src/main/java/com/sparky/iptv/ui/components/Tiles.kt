package com.sparky.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.sparky.iptv.R
import com.sparky.iptv.data.Channel
import com.sparky.iptv.data.Names
import com.sparky.iptv.ui.AppViewModel

/** A small pill like "LIVE" or "NEXT" for event channels. */
@Composable
fun Badge(text: String, focused: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(5.u))
            .background(if (text == "LIVE") C.Accent else if (focused) C.OnFocus else C.Line2)
            .padding(horizontal = 6.u, vertical = 2.u),
    ) {
        T(text, size = 10, weight = FontWeight.Bold, color = if (text == "LIVE") C.Bg else if (focused) C.Text else C.Text2, letterSpacing = 0.06f)
    }
}

/** Channel as a tile: big logo, name and what's on now. */
@Composable
fun ChannelTile(
    vm: AppViewModel,
    ch: Channel,
    now: Long,
    width: Dp?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(ch.id) { vm.ensureEpg(ch) }
    val (cur, _) = vm.nowNext(ch.id, now)
    val (badge, title) = Names.event(ch.display)
    val fav = ch.favKey in vm.favorites
    Column(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth()),
        verticalArrangement = Arrangement.spacedBy(8.u),
    ) {
        FocusCard(
            onClick = onClick,
            onLongClick = { vm.toggleFavorite(ch.favKey, ch.display) },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) { _ ->
            Box(Modifier.fillMaxSize().background(C.Panel), contentAlignment = Alignment.Center) {
                ChannelLogo(ch.display, ch.logo, 64.u)
            }
            Row(Modifier.align(Alignment.TopStart).padding(8.u), verticalAlignment = Alignment.CenterVertically) {
                T(ch.num.toString(), size = 11, weight = FontWeight.SemiBold, color = C.Dim)
            }
            if (fav) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.u)) { Icon(R.drawable.ic_star, tint = C.Text2, size = 13.u) }
            }
            if (cur != null) {
                val frac = (now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)
                ProgressBar(frac, track = C.Line, fill = C.Text, height = 3.u, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.u)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge != null) { Badge(badge); Spacer(Modifier.width(6.u)) }
                T(title, size = 14, weight = FontWeight.SemiBold)
            }
            T(cur?.title ?: " ", size = 12, color = C.Dim)
        }
    }
}

/** Poster tile used for movies and series in Home rows. */
@Composable
fun PosterCard(
    title: String,
    image: String?,
    width: Dp,
    fav: Boolean,
    progress: Float? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.u)) {
        FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) { _ ->
            Poster(title, image, Modifier.fillMaxSize())
            if (fav) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.u).clip(RoundedCornerShape(999.u))
                        .background(C.Bg.copy(alpha = 0.75f)).padding(5.u)
                ) { Icon(R.drawable.ic_star, tint = C.Text, size = 13.u) }
            }
            if (progress != null) {
                ProgressBar(progress, track = C.Bg.copy(alpha = 0.6f), fill = C.Text, height = 4.u, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
        T(title, size = 13, color = C.Text2)
    }
}
