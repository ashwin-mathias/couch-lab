package com.sparky.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.CAT_RECENT
import com.sparky.iptv.ui.Screen
import com.sparky.iptv.ui.Section
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.ChannelTile
import com.sparky.iptv.ui.components.Clock
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.PosterCard
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.rememberNow
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay
import java.util.Calendar

/** The start screen: recently watched, favorites and what's new, as rows of tiles. */
@Composable
fun HomeSection(vm: AppViewModel) {
    val now = rememberNow()
    val recents = remember(vm.recents, vm.library) { vm.recentEntries() }
    val favChannels = remember(vm.library, vm.favorites) { vm.library.channels.filter { it.favKey in vm.favorites } }
    val favMovies = remember(vm.library, vm.favorites) { vm.library.movies.filter { it.favKey in vm.favorites } }
    val favSeries = remember(vm.library, vm.favorites) { vm.library.series.filter { it.favKey in vm.favorites } }
    val resume = remember(vm.library, vm.progressVersion) { vm.continueWatching() }
    val newMovies = remember(vm.library, vm.myRegions) { vm.moviesFor(CAT_RECENT).take(20) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { first.requestFocus() } }

    val greeting = remember(now / 3_600_000) {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    var firstAssigned = false
    fun takeFirst(): Modifier = if (!firstAssigned) { firstAssigned = true; Modifier.focusRequester(first) } else Modifier

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.u, top = 32.u, bottom = 48.u),
        verticalArrangement = Arrangement.spacedBy(30.u),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(end = 48.u), verticalAlignment = Alignment.CenterVertically) {
                T(greeting, size = 30, weight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (vm.refreshing) T("Updating playlist…", size = 13, color = C.Dim, modifier = Modifier.padding(end = 16.u))
                Clock()
            }
        }

        if (recents.isNotEmpty()) {
            val m = takeFirst()
            item {
                HomeRow("Recently watched") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = PaddingValues(end = 48.u)) {
                        itemsIndexed(recents) { i, e ->
                            val mod = if (i == 0) m else Modifier
                            when (e) {
                                is AppViewModel.RecentEntry.Ch -> ChannelTile(vm, e.channel, now, 220.u, modifier = mod, onClick = {
                                    val list = recents.filterIsInstance<AppViewModel.RecentEntry.Ch>().map { it.channel }
                                    vm.playLive(list, list.indexOf(e.channel))
                                })
                                is AppViewModel.RecentEntry.Mv -> Column(mod) {
                                    PosterCard(e.movie.name, e.movie.poster, 132.u, e.movie.favKey in vm.favorites,
                                        progress = vm.progress(e.movie.progressKey)?.fraction,
                                        onClick = { vm.push(Screen.MovieDetail(e.movie)) },
                                        onLongClick = { vm.toggleFavorite(e.movie.favKey, e.movie.name) })
                                }
                                is AppViewModel.RecentEntry.Sr -> Column(mod) {
                                    PosterCard(e.series.name, e.series.cover, 132.u, e.series.favKey in vm.favorites,
                                        onClick = { vm.push(Screen.SeriesDetail(e.series)) },
                                        onLongClick = { vm.toggleFavorite(e.series.favKey, e.series.name) })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (favChannels.isNotEmpty()) {
            val m = takeFirst()
            item {
                HomeRow("Favorite channels") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = PaddingValues(end = 48.u)) {
                        itemsIndexed(favChannels, key = { idx, c -> "$idx:" + (c.id) }) { i, ch ->
                            ChannelTile(vm, ch, now, 220.u, modifier = if (i == 0) m else Modifier, onClick = { vm.playLive(favChannels, i) })
                        }
                    }
                }
            }
        }

        if (resume.isNotEmpty()) {
            val m = takeFirst()
            item {
                HomeRow("Continue watching") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = PaddingValues(end = 48.u)) {
                        itemsIndexed(resume, key = { idx, p -> "$idx:" + (p.first.id) }) { i, (mv, p) ->
                            Column(if (i == 0) m else Modifier) {
                                PosterCard(mv.name, mv.poster, 132.u, mv.favKey in vm.favorites, progress = p.fraction,
                                    onClick = { vm.playMovie(mv, fromStart = false) },
                                    onLongClick = { vm.push(Screen.MovieDetail(mv)) })
                            }
                        }
                    }
                }
            }
        }

        if (favMovies.isNotEmpty() || favSeries.isNotEmpty()) {
            val m = takeFirst()
            item {
                HomeRow("Favorite movies & series") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = PaddingValues(end = 48.u)) {
                        itemsIndexed(favMovies, key = { idx, x -> "$idx:" + ("m" + x.id) }) { i, mv ->
                            Column(if (i == 0) m else Modifier) {
                                PosterCard(mv.name, mv.poster, 132.u, true,
                                    onClick = { vm.push(Screen.MovieDetail(mv)) },
                                    onLongClick = { vm.toggleFavorite(mv.favKey, mv.name) })
                            }
                        }
                        itemsIndexed(favSeries, key = { idx, x -> "$idx:" + ("s" + x.id) }) { i, sr ->
                            Column(if (i == 0 && favMovies.isEmpty()) m else Modifier) {
                                PosterCard(sr.name, sr.cover, 132.u, true,
                                    onClick = { vm.push(Screen.SeriesDetail(sr)) },
                                    onLongClick = { vm.toggleFavorite(sr.favKey, sr.name) })
                            }
                        }
                    }
                }
            }
        }

        if (newMovies.isNotEmpty()) {
            val m = takeFirst()
            item {
                HomeRow("Recently added movies") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = PaddingValues(end = 48.u)) {
                        itemsIndexed(newMovies, key = { idx, x -> "$idx:" + (x.id) }) { i, mv ->
                            Column(if (i == 0) m else Modifier) {
                                PosterCard(mv.name, mv.poster, 132.u, mv.favKey in vm.favorites,
                                    onClick = { vm.push(Screen.MovieDetail(mv)) },
                                    onLongClick = { vm.toggleFavorite(mv.favKey, mv.name) })
                            }
                        }
                    }
                }
            }
        }

        if (recents.isEmpty() && favChannels.isEmpty()) {
            item {
                Column(Modifier.padding(end = 48.u), verticalArrangement = Arrangement.spacedBy(14.u)) {
                    T("Your channels will appear here", size = 20, weight = FontWeight.SemiBold)
                    T(
                        "Watch something, or hold OK on any channel, movie or series to add it to Favorites.",
                        size = 15, color = C.Muted, maxLines = 2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                        PillButton("Browse Live TV", takeFirst(), primary = true) { vm.section = Section.LIVE }
                        PillButton("Movies") { vm.section = Section.MOVIES }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRow(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.u)) {
        Label(title, color = C.Muted)
        content()
    }
}
