package com.sparky.iptv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.R
import com.sparky.iptv.data.Category
import com.sparky.iptv.data.Movie
import com.sparky.iptv.data.MovieInfo
import com.sparky.iptv.data.Series
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.CAT_ALL
import com.sparky.iptv.ui.CAT_FAV
import com.sparky.iptv.ui.CAT_RECENT
import com.sparky.iptv.ui.Screen
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.Clock
import com.sparky.iptv.ui.components.FocusCard
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Icon
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.Poster
import com.sparky.iptv.ui.components.ProgressBar
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtDuration
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

private data class Chip(val id: String, val name: String)

private fun chipsFor(cats: List<Category>, hasRecent: Boolean = true): List<Chip> = buildList {
    if (hasRecent) add(Chip(CAT_RECENT, "Recently added"))
    add(Chip(CAT_FAV, "Favorites"))
    add(Chip(CAT_ALL, "All"))
    cats.forEach { add(Chip(it.id, it.name)) }
}

@Composable
private fun ChipRow(chips: List<Chip>, selected: String, firstFocus: FocusRequester, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.u), contentPadding = PaddingValues(end = 48.u)) {
        itemsIndexed(chips, key = { idx, c -> "$idx:" + (c.id) }) { i, c ->
            val sel = c.id == selected
            FocusItem(
                onClick = { onSelect(c.id) },
                selected = sel,
                container = C.Panel,
                shape = RoundedCornerShape(999.u),
                modifier = Modifier.height(40.u).let { if (i == 0) it.focusRequester(firstFocus) else it },
            ) { f ->
                T(
                    c.name, size = 14, weight = FontWeight.SemiBold,
                    color = if (f) C.OnFocus else if (sel) C.Text else C.Muted,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.u),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(end = 48.u), verticalAlignment = Alignment.CenterVertically) {
        T(title, size = 30, weight = FontWeight.Bold)
        Spacer(Modifier.width(16.u))
        T("$count titles", size = 14, color = C.Dim)
        Spacer(Modifier.weight(1f))
        Clock()
    }
}

@Composable
fun MoviesScreen(vm: AppViewModel) {
    val chips = remember(vm.library, vm.myRegions) { chipsFor(vm.library.vodCats.filter { vm.vodCatVisible(it.id, series = false) }) }
    val movies = remember(vm.library, vm.favorites, vm.movieCat, vm.myRegions) { vm.moviesFor(vm.movieCat) }
    val resume = remember(vm.library, vm.progressVersion) { vm.continueWatching() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.u, end = 48.u, top = 32.u, bottom = 40.u),
        horizontalArrangement = Arrangement.spacedBy(20.u),
        verticalArrangement = Arrangement.spacedBy(24.u),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Movies", vm.library.movies.size) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChipRow(chips, vm.movieCat, firstFocus) { vm.movieCat = it }
        }
        if (resume.isNotEmpty() && vm.movieCat == CAT_RECENT) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.u)) {
                    Label("Continue watching")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u)) {
                        items(resume, key = { it.first.id }) { (m, p) ->
                            FocusCard(
                                onClick = { vm.playMovie(m, fromStart = false) },
                                onLongClick = { vm.push(Screen.MovieDetail(m)) },
                                modifier = Modifier.width(248.u),
                            ) { _ ->
                                Column {
                                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                                        Poster(m.name, m.poster, Modifier.fillMaxSize())
                                        ProgressBar(p.fraction, track = C.Bg.copy(alpha = 0.6f), fill = C.Text, height = 4.u, modifier = Modifier.align(Alignment.BottomStart))
                                    }
                                    T(m.name, size = 14, weight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.u, vertical = 10.u))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.u))
                    Label(chips.firstOrNull { it.id == vm.movieCat }?.name ?: "")
                }
            }
        }
        if (movies.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                T(
                    if (vm.vodLoading) "Loading movies… ${vm.vodProgress ?: ""}" else if (vm.movieCat == CAT_FAV) "No favorite movies yet. Hold OK on a poster to add one." else "Nothing here yet.",
                    size = 15, color = C.Muted,
                )
            }
        }
        items(movies) { m ->
            PosterTile(
                title = m.name,
                image = m.poster,
                fav = m.favKey in vm.favorites,
                onClick = { vm.push(Screen.MovieDetail(m)) },
                onLongClick = { vm.toggleFavorite(m.favKey, m.name) },
            )
        }
    }
}

@Composable
fun PosterTile(title: String, image: String?, fav: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.u)) {
        FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) { _ ->
            Poster(title, image, Modifier.fillMaxSize())
            if (fav) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.u).clip(RoundedCornerShape(999.u)).background(C.Bg.copy(alpha = 0.75f)).padding(5.u)) {
                    Icon(R.drawable.ic_star, tint = C.Text, size = 14.u)
                }
            }
        }
        T(title, size = 13, color = C.Text2)
    }
}

@Composable
fun MovieDetailScreen(vm: AppViewModel, movie: Movie) {
    BackHandler { vm.pop() }
    val info by produceState<MovieInfo?>(null, movie.id) { value = vm.movieInfo(movie) }
    val progress = remember(vm.progressVersion, movie.id) { vm.progress(movie.progressKey) }
    val fav = movie.favKey in vm.favorites
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { playFocus.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = 64.u, vertical = 48.u), horizontalArrangement = Arrangement.spacedBy(56.u)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.u)) {
            Label("Movie")
            T(movie.name, size = 40, weight = FontWeight.Bold, maxLines = 2, lineHeight = 1.15f)
            val meta = listOfNotNull(
                info?.year, info?.genre, info?.durationSecs?.let { fmtDuration(it) }?.ifBlank { null },
                movie.rating?.let { "★ $it" },
            )
            if (meta.isNotEmpty()) T(meta.joinToString("   ·   "), size = 14, color = C.Text2)
            T(
                info?.plot ?: if (info == null) "Loading details…" else "",
                size = 15, color = C.Muted, maxLines = 6, lineHeight = 1.6f,
                modifier = Modifier.padding(top = 4.u),
            )
            info?.director?.let { T("Director  $it", size = 13, color = C.Dim) }
            info?.cast?.let { T("Cast  $it", size = 13, color = C.Dim, maxLines = 2) }
            Spacer(Modifier.height(8.u))
            if (progress != null) {
                Column(Modifier.width(320.u), verticalArrangement = Arrangement.spacedBy(6.u)) {
                    ProgressBar(progress.fraction, track = C.Line, fill = C.Text)
                    T("${((progress.dur - progress.pos) / 60_000)} min left", size = 12, color = C.Dim)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.u), modifier = Modifier.padding(top = 8.u)) {
                PillButton(
                    if (progress != null) "Resume" else "Play",
                    Modifier.focusRequester(playFocus), primary = true, icon = R.drawable.ic_play,
                ) { vm.playMovie(movie, fromStart = false) }
                if (progress != null) {
                    PillButton("Start over", icon = R.drawable.ic_restart) { vm.playMovie(movie, fromStart = true) }
                }
                PillButton(
                    if (fav) "In favorites" else "Add to favorites",
                    icon = if (fav) R.drawable.ic_star else R.drawable.ic_star_outline,
                ) { vm.toggleFavorite(movie.favKey) }
            }
        }
        Box(Modifier.width(300.u).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.u))) {
            Poster(movie.name, movie.poster, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun SeriesScreen(vm: AppViewModel) {
    val chips = remember(vm.library, vm.myRegions) { chipsFor(vm.library.seriesCats.filter { vm.vodCatVisible(it.id, series = true) }) }
    val list = remember(vm.library, vm.favorites, vm.seriesCat, vm.myRegions) { vm.seriesFor(vm.seriesCat) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.u, end = 48.u, top = 32.u, bottom = 40.u),
        horizontalArrangement = Arrangement.spacedBy(20.u),
        verticalArrangement = Arrangement.spacedBy(24.u),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Series", vm.library.series.size) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChipRow(chips, vm.seriesCat, firstFocus) { vm.seriesCat = it }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                T(
                    if (vm.vodLoading) "Loading series… ${vm.vodProgress ?: ""}" else if (vm.seriesCat == CAT_FAV) "No favorite series yet. Hold OK on a poster to add one." else "Nothing here yet.",
                    size = 15, color = C.Muted,
                )
            }
        }
        items(list) { s: Series ->
            PosterTile(
                title = s.name,
                image = s.cover,
                fav = s.favKey in vm.favorites,
                onClick = { vm.push(Screen.SeriesDetail(s)) },
                onLongClick = { vm.toggleFavorite(s.favKey, s.name) },
            )
        }
    }
}
