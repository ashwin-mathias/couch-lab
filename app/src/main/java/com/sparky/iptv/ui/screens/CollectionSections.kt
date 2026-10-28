package com.sparky.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.R
import com.sparky.iptv.data.Channel
import com.sparky.iptv.data.Names
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.Screen
import com.sparky.iptv.ui.components.Badge
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.ChannelLogo
import com.sparky.iptv.ui.components.ChannelTile
import com.sparky.iptv.ui.components.Clock
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Icon
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.PosterCard
import com.sparky.iptv.ui.components.ProgressBar
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtLeft
import com.sparky.iptv.ui.components.rememberNow
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

private const val KEY_FAV_PAGE = "page_favorites"
private const val KEY_RECENT_PAGE = "page_recent"

@Composable
private fun PageHeader(title: String, subtitle: String, tiles: Boolean, onToggle: () -> Unit, toggleFocus: FocusRequester) {
    Row(Modifier.fillMaxWidth().padding(end = 48.u), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.u)) {
            T(title, size = 30, weight = FontWeight.Bold)
            T(subtitle, size = 14, color = C.Dim)
        }
        FocusItem(
            onClick = onToggle,
            container = C.Panel,
            shape = RoundedCornerShape(999.u),
            modifier = Modifier.height(40.u).focusRequester(toggleFocus),
        ) { f ->
            Row(Modifier.align(Alignment.Center).padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (tiles) R.drawable.ic_list else R.drawable.ic_tiles, tint = if (f) C.OnFocus else C.Text2, size = 16.u)
                Spacer(Modifier.width(8.u))
                T(if (tiles) "List view" else "Tile view", size = 14, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text2)
            }
        }
        Spacer(Modifier.width(24.u))
        Clock()
    }
}

/** One channel as a list row (used when the page is in list view). */
@Composable
private fun ChannelRow(vm: AppViewModel, ch: Channel, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(ch.id) { vm.ensureEpg(ch) }
    val (cur, _) = vm.nowNext(ch.id, now)
    val (badge, title) = Names.event(ch.display)
    FocusItem(
        onClick = onClick,
        onLongClick = { vm.toggleFavorite(ch.favKey, ch.display) },
        container = C.Panel,
        modifier = modifier.fillMaxWidth().height(64.u),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
            T(ch.num.toString(), size = 13, weight = FontWeight.SemiBold, color = if (f) C.OnFocusMuted else C.Dim, modifier = Modifier.width(52.u))
            ChannelLogo(ch.display, ch.logo, 40.u, focused = f)
            Spacer(Modifier.width(14.u))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (badge != null) { Badge(badge, focused = f); Spacer(Modifier.width(6.u)) }
                    T(title, size = 15, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text)
                }
                T(cur?.title ?: "No guide information", size = 13, color = if (f) C.OnFocusMuted else C.Dim)
            }
            if (cur != null) {
                Column(Modifier.width(160.u), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.u)) {
                    T(fmtLeft(cur.end, now), size = 12, color = if (f) C.OnFocusMuted else C.Dim)
                    val frac = (now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)
                    ProgressBar(frac, track = if (f) C.TrackOnFocus else C.Line, fill = if (f) C.OnFocus else C.Dim)
                }
            }
        }
    }
}

/** Favorites: channels (tiles or list), then movies and series. */
@Composable
fun FavoritesSection(vm: AppViewModel) {
    val now = rememberNow()
    val tiles = vm.isTiles(KEY_FAV_PAGE)
    val chans = remember(vm.library, vm.favorites) { vm.library.channels.filter { it.favKey in vm.favorites } }
    val movies = remember(vm.library, vm.favorites) { vm.library.movies.filter { it.favKey in vm.favorites } }
    val series = remember(vm.library, vm.favorites) { vm.library.series.filter { it.favKey in vm.favorites } }
    val toggleFocus = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { first.requestFocus() }.onFailure { runCatching { toggleFocus.requestFocus() } }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (tiles) 4 else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.u, end = 48.u, top = 32.u, bottom = 48.u),
        horizontalArrangement = Arrangement.spacedBy(if (tiles) 20.u else 0.u),
        verticalArrangement = Arrangement.spacedBy(if (tiles) 22.u else 6.u),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeader("Favorites", "${chans.size} channels · ${movies.size} movies · ${series.size} series · Hold OK to remove",
                tiles, { vm.setTiles(KEY_FAV_PAGE, !tiles) }, toggleFocus)
        }
        if (chans.isEmpty() && movies.isEmpty() && series.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                T("Nothing here yet. Hold OK on any channel, movie or series to add it to Favorites.", size = 15, color = C.Muted, maxLines = 2)
            }
        }
        if (chans.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Label("Channels", color = C.Muted, modifier = Modifier.padding(top = 8.u)) }
            itemsIndexed(chans, key = { idx, c -> "$idx:" + ("c" + c.id) }) { i, ch ->
                val m = if (i == 0) Modifier.focusRequester(first) else Modifier
                if (tiles) ChannelTile(vm, ch, now, width = null, onClick = { vm.playLive(chans, i) }, modifier = m)
                else ChannelRow(vm, ch, now, onClick = { vm.playLive(chans, i) }, modifier = m)
            }
        }
        if (movies.isNotEmpty() || series.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Label("Movies & series", color = C.Muted, modifier = Modifier.padding(top = 12.u)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u)) {
                    items(movies.size, key = { "$it:" + ("m" + movies[it].id) }) { i ->
                        val mv = movies[i]
                        PosterCard(mv.name, mv.poster, 132.u, true,
                            progress = vm.progress(mv.progressKey)?.fraction,
                            onClick = { vm.push(Screen.MovieDetail(mv)) },
                            onLongClick = { vm.toggleFavorite(mv.favKey, mv.name) })
                    }
                    items(series.size, key = { "$it:" + ("s" + series[it].id) }) { i ->
                        val sr = series[i]
                        PosterCard(sr.name, sr.cover, 132.u, true,
                            onClick = { vm.push(Screen.SeriesDetail(sr)) },
                            onLongClick = { vm.toggleFavorite(sr.favKey, sr.name) })
                    }
                }
            }
        }
    }
}

/** The last 12 things watched: channels, movies and series together, newest first. */
@Composable
fun RecentSection(vm: AppViewModel) {
    val now = rememberNow()
    val tiles = vm.isTiles(KEY_RECENT_PAGE)
    val entries = remember(vm.recents, vm.library) { vm.recentEntries() }
    val liveList = remember(entries) { entries.filterIsInstance<AppViewModel.RecentEntry.Ch>().map { it.channel } }
    val toggleFocus = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { first.requestFocus() }.onFailure { runCatching { toggleFocus.requestFocus() } }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (tiles) 4 else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.u, end = 48.u, top = 32.u, bottom = 48.u),
        horizontalArrangement = Arrangement.spacedBy(if (tiles) 20.u else 0.u),
        verticalArrangement = Arrangement.spacedBy(if (tiles) 22.u else 6.u),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeader("Recently watched", "Your last ${entries.size} channels, movies and series",
                tiles, { vm.setTiles(KEY_RECENT_PAGE, !tiles) }, toggleFocus)
        }
        if (entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                T("Things you watch will appear here.", size = 15, color = C.Muted)
            }
        }
        itemsIndexed(entries) { i, e ->
            val m = if (i == 0) Modifier.focusRequester(first) else Modifier
            when (e) {
                is AppViewModel.RecentEntry.Ch ->
                    if (tiles) ChannelTile(vm, e.channel, now, width = null, modifier = m,
                        onClick = { vm.playLive(liveList, liveList.indexOf(e.channel)) })
                    else ChannelRow(vm, e.channel, now, modifier = m,
                        onClick = { vm.playLive(liveList, liveList.indexOf(e.channel)) })
                is AppViewModel.RecentEntry.Mv -> RecentVodItem(
                    tiles, m, e.movie.name, e.movie.poster, "Movie", vm.progress(e.movie.progressKey)?.fraction,
                    fav = e.movie.favKey in vm.favorites,
                    onClick = { vm.push(Screen.MovieDetail(e.movie)) },
                    onLongClick = { vm.toggleFavorite(e.movie.favKey, e.movie.name) },
                )
                is AppViewModel.RecentEntry.Sr -> RecentVodItem(
                    tiles, m, e.series.name, e.series.cover, "Series", null,
                    fav = e.series.favKey in vm.favorites,
                    onClick = { vm.push(Screen.SeriesDetail(e.series)) },
                    onLongClick = { vm.toggleFavorite(e.series.favKey, e.series.name) },
                )
            }
        }
    }
}

@Composable
private fun RecentVodItem(
    tiles: Boolean,
    modifier: Modifier,
    title: String,
    image: String?,
    kind: String,
    progress: Float?,
    fav: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    if (tiles) {
        Box(modifier) {
            PosterCard(title, image, 150.u, fav, progress = progress, onClick = onClick, onLongClick = onLongClick)
        }
    } else {
        FocusItem(onClick = onClick, onLongClick = onLongClick, container = C.Panel, modifier = modifier.fillMaxWidth().height(64.u)) { f ->
            Row(Modifier.fillMaxSize().padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
                T(kind, size = 12, weight = FontWeight.SemiBold, color = if (f) C.OnFocusMuted else C.Dim, modifier = Modifier.width(52.u + 54.u))
                T(title, size = 15, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text, modifier = Modifier.weight(1f))
                if (progress != null) {
                    Box(Modifier.width(160.u)) {
                        ProgressBar(progress, track = if (f) C.TrackOnFocus else C.Line, fill = if (f) C.OnFocus else C.Dim)
                    }
                }
            }
        }
    }
}
