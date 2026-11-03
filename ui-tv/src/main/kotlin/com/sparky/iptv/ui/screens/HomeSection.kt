package com.sparky.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.sparky.iptv.ui.LIST_LIVE
import com.sparky.iptv.ui.LIST_MOVIE
import com.sparky.iptv.ui.LIST_SERIES
import com.sparky.iptv.ui.folderList
import androidx.compose.foundation.lazy.rememberLazyListState
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

private val ROW_PADDING = PaddingValues(start = 8.u, end = 48.u, top = 8.u, bottom = 8.u)

/** The start screen: Favorites, then Recently watched, then Recently added. */
@Composable
fun HomeSection(vm: AppViewModel) {
    val now = rememberNow()
    val recents = remember(vm.recents, vm.library) { vm.recentEntries() }
    val favChannels = remember(vm.library, vm.favorites, vm.favOrder, vm.moving) { vm.favChannels() }
    val favMovies = remember(vm.library, vm.favorites, vm.favOrder, vm.moving) { vm.favMovies() }
    val favSeries = remember(vm.library, vm.favorites, vm.favOrder, vm.moving) { vm.favSeries() }
    // One row per folder that has channels, in your folder order.
    val folderRows = remember(vm.library, vm.favorites, vm.folders, vm.moving) {
        vm.folders.map { it to vm.folderChannels(it.id) }.filter { it.second.isNotEmpty() }
    }
    val newMovies = remember(vm.library, vm.myRegions) { vm.moviesFor(CAT_RECENT).take(20) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { first.requestFocus() } }
    // Stops the tile preview when leaving Home (unless full screen takes over the stream).
    PreviewDriver(vm, null)

    val greeting = remember(now / 3_600_000) {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val hasFavs = favChannels.isNotEmpty() || favMovies.isNotEmpty() || favSeries.isNotEmpty()
    // The first row on screen receives the initial focus.
    val firstRow = when {
        hasFavs -> "fav"
        recents.isNotEmpty() -> "recent"
        newMovies.isNotEmpty() -> "new"
        else -> "empty"
    }
    fun focusMod(row: String, i: Int): Modifier = if (row == firstRow && i == 0) Modifier.focusRequester(first) else Modifier

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 40.u, top = 24.u, bottom = 48.u),
        verticalArrangement = Arrangement.spacedBy(26.u),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.u, end = 48.u), verticalAlignment = Alignment.CenterVertically) {
                T(greeting, size = 30, weight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (vm.refreshing) T("Updating playlist…", size = 13, color = C.Dim, modifier = Modifier.padding(end = 16.u))
                Clock()
            }
        }

        // 1. Favorites: channels (live tiles), then movies and series, each in your order.
        if (hasFavs) {
            item(key = "fav") {
                HomeRow("Favorites") {
                    val rowState = rememberLazyListState()
                    FollowMove(vm, LIST_LIVE, rowState) { k -> favChannels.indexOfFirst { it.favKey == k } }
                    FollowMove(vm, LIST_MOVIE, rowState) { k -> favMovies.indexOfFirst { it.favKey == k }.let { if (it < 0) -1 else favChannels.size + it } }
                    FollowMove(vm, LIST_SERIES, rowState) { k ->
                        favSeries.indexOfFirst { it.favKey == k }.let { if (it < 0) -1 else favChannels.size + favMovies.size + it }
                    }
                    LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = ROW_PADDING) {
                        itemsIndexed(favChannels, key = { _, c -> c.favKey }) { i, ch ->
                            ChannelTile(vm, ch, now, 220.u, modifier = focusMod("fav", i).movingOutline(vm.moving?.key == ch.favKey && vm.moving?.listId == LIST_LIVE), livePreview = true,
                                onClick = { vm.playLive(favChannels, i) },
                                onLongClick = { vm.holdFavorite(ch.favKey, ch.display, LIST_LIVE, upDown = 0, leftRight = 1) })
                        }
                        itemsIndexed(favMovies, key = { _, x -> x.favKey }) { i, mv ->
                            Column(focusMod("fav", favChannels.size + i).movingOutline(vm.moving?.key == mv.favKey)) {
                                PosterCard(mv.name, mv.poster, 132.u, true,
                                    progress = vm.progress(mv.progressKey)?.fraction,
                                    onClick = { vm.push(Screen.MovieDetail(mv)) },
                                    onLongClick = { vm.holdFavorite(mv.favKey, mv.name, LIST_MOVIE, upDown = 0, leftRight = 1) })
                            }
                        }
                        itemsIndexed(favSeries, key = { _, x -> x.favKey }) { i, sr ->
                            Column(focusMod("fav", favChannels.size + favMovies.size + i).movingOutline(vm.moving?.key == sr.favKey)) {
                                PosterCard(sr.name, sr.cover, 132.u, true,
                                    onClick = { vm.push(Screen.SeriesDetail(sr)) },
                                    onLongClick = { vm.holdFavorite(sr.favKey, sr.name, LIST_SERIES, upDown = 0, leftRight = 1) })
                            }
                        }
                    }
                }
            }
        }

        // 1b. Your favorites folders, one row each.
        folderRows.forEach { (f, chans) ->
            item(key = "folder-" + f.id) {
                HomeRow(f.name) {
                    val rowState = rememberLazyListState()
                    val listId = folderList(f.id)
                    FollowMove(vm, listId, rowState) { k -> chans.indexOfFirst { it.favKey == k } }
                    LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = ROW_PADDING) {
                        itemsIndexed(chans, key = { _, c -> c.favKey }) { i, ch ->
                            ChannelTile(vm, ch, now, 220.u, modifier = Modifier.movingOutline(vm.moving?.key == ch.favKey && vm.moving?.listId == listId),
                                livePreview = true,
                                onClick = { vm.playLive(chans, i) },
                                onLongClick = { vm.holdFavorite(ch.favKey, ch.display, listId, upDown = 0, leftRight = 1) })
                        }
                    }
                }
            }
        }

        // 2. Recently watched: channels, movies (with progress) and series, newest first.
        if (recents.isNotEmpty()) {
            item {
                HomeRow("Recently watched") {
                    val liveList = recents.filterIsInstance<AppViewModel.RecentEntry.Ch>().map { it.channel }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = ROW_PADDING) {
                        itemsIndexed(recents, key = { idx, _ -> "r$idx" }) { i, e ->
                            val mod = focusMod("recent", i)
                            when (e) {
                                is AppViewModel.RecentEntry.Ch -> ChannelTile(vm, e.channel, now, 220.u, modifier = mod, livePreview = true,
                                    onClick = { vm.playLive(liveList, liveList.indexOf(e.channel)) })
                                is AppViewModel.RecentEntry.Mv -> Column(mod) {
                                    PosterCard(e.movie.name, e.movie.poster, 132.u, e.movie.favKey in vm.favorites,
                                        progress = vm.progress(e.movie.progressKey)?.fraction,
                                        onClick = { vm.push(Screen.MovieDetail(e.movie)) },
                                        onLongClick = { vm.holdFavorite(e.movie.favKey, e.movie.name) })
                                }
                                is AppViewModel.RecentEntry.Sr -> Column(mod) {
                                    PosterCard(e.series.name, e.series.cover, 132.u, e.series.favKey in vm.favorites,
                                        onClick = { vm.push(Screen.SeriesDetail(e.series)) },
                                        onLongClick = { vm.holdFavorite(e.series.favKey, e.series.name) })
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Recently added movies.
        if (newMovies.isNotEmpty()) {
            item {
                HomeRow("Recently added") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u), contentPadding = ROW_PADDING) {
                        itemsIndexed(newMovies, key = { idx, x -> "n$idx:${x.id}" }) { i, mv ->
                            Column(focusMod("new", i)) {
                                PosterCard(mv.name, mv.poster, 132.u, mv.favKey in vm.favorites,
                                    onClick = { vm.push(Screen.MovieDetail(mv)) },
                                    onLongClick = { vm.holdFavorite(mv.favKey, mv.name) })
                            }
                        }
                    }
                }
            }
        } else if (vm.vodLoading) {
            item {
                HomeRow("Recently added") {
                    T("Loading movies… ${vm.vodProgress ?: ""}", size = 14, color = C.Dim, modifier = Modifier.padding(start = 8.u))
                }
            }
        }

        if (firstRow == "empty" && !vm.vodLoading) {
            item {
                Column(Modifier.padding(start = 8.u, end = 48.u), verticalArrangement = Arrangement.spacedBy(14.u)) {
                    T("Your channels will appear here", size = 20, weight = FontWeight.SemiBold)
                    T(
                        "Watch something, or hold OK on any channel, movie or series to add it to Favorites.",
                        size = 15, color = C.Muted, maxLines = 2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                        PillButton("Browse Live TV", Modifier.focusRequester(first), primary = true) { vm.section = Section.LIVE }
                        PillButton("Movies") { vm.section = Section.MOVIES }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRow(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.u)) {
        Label(title, color = C.Muted, modifier = Modifier.padding(start = 8.u))
        content()
    }
}
