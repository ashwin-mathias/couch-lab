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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.R
import com.sparky.iptv.data.Episode
import com.sparky.iptv.data.Series
import com.sparky.iptv.data.SeriesInfo
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.FocusCard
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.Poster
import com.sparky.iptv.ui.components.ProgressBar
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtDuration
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

@Composable
fun SeriesDetailScreen(vm: AppViewModel, series: Series) {
    BackHandler { vm.pop() }
    val result by produceState<Result<SeriesInfo>?>(null, series.id) { value = vm.seriesInfo(series) }
    val info = result?.getOrNull()
    val fav = series.favKey in vm.favorites
    val lastEpId = remember(vm.progressVersion, series.id) { vm.prefs.seriesLastEpisode(series.id) }
    val lastEp: Episode? = remember(info, lastEpId) {
        info?.episodes?.values?.flatten()?.firstOrNull { it.id == lastEpId }
    }
    val firstEp: Episode? = remember(info) { info?.let { i -> i.seasons.firstOrNull()?.let { s -> i.episodes[s]?.firstOrNull() } } }
    var season by remember(info) { mutableIntStateOf(lastEp?.season ?: info?.seasons?.firstOrNull() ?: 1) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(info != null) {
        if (info != null) { delay(150); runCatching { playFocus.requestFocus() } }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 64.u, vertical = 44.u), verticalArrangement = Arrangement.spacedBy(30.u)) {
        Row(horizontalArrangement = Arrangement.spacedBy(56.u)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.u)) {
                Label("Series")
                T(series.name, size = 40, weight = FontWeight.Bold, maxLines = 2, lineHeight = 1.15f)
                val meta = listOfNotNull(
                    series.year,
                    info?.seasons?.size?.let { if (it == 1) "1 season" else "$it seasons" },
                    (info?.series?.genre ?: series.genre),
                    series.rating?.let { "★ $it" },
                )
                if (meta.isNotEmpty()) T(meta.joinToString("   ·   "), size = 14, color = C.Text2)
                val plot = info?.series?.plot ?: series.plot
                T(
                    plot ?: when {
                        result == null -> "Loading episodes…"
                        result?.isFailure == true -> "Couldn't load episodes: ${result?.exceptionOrNull()?.message}"
                        else -> ""
                    },
                    size = 15, color = C.Muted, maxLines = 4, lineHeight = 1.6f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.u), modifier = Modifier.padding(top = 10.u)) {
                    val target = lastEp ?: firstEp
                    if (info != null && target != null) {
                        val label = if (lastEp != null) "Resume S${lastEp.season} · E${lastEp.number}" else "Play S${target.season} · E${target.number}"
                        PillButton(label, Modifier.focusRequester(playFocus), primary = true, icon = R.drawable.ic_play) {
                            vm.playEpisode(series, info, target, fromStart = false)
                        }
                    }
                    PillButton(
                        if (fav) "In favorites" else "Add to favorites",
                        icon = if (fav) R.drawable.ic_star else R.drawable.ic_star_outline,
                    ) { vm.toggleFavorite(series.favKey) }
                }
            }
            Box(Modifier.width(400.u).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.u))) {
                Poster(series.name, info?.backdrop ?: series.cover, Modifier.fillMaxSize())
            }
        }

        if (info != null && info.seasons.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(18.u)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.u)) {
                    items(info.seasons) { s ->
                        val sel = s == season
                        FocusItem(
                            onClick = { season = s },
                            onFocus = { season = s },
                            selected = sel,
                            container = C.Panel,
                            shape = RoundedCornerShape(999.u),
                            modifier = Modifier.height(40.u),
                        ) { f ->
                            T(
                                "Season $s", size = 14, weight = FontWeight.SemiBold,
                                color = if (f) C.OnFocus else if (sel) C.Text else C.Muted,
                                modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.u),
                            )
                        }
                    }
                }
                val eps = info.episodes[season].orEmpty()
                val listState = rememberLazyListState()
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(20.u),
                    contentPadding = PaddingValues(end = 64.u),
                ) {
                    items(eps, key = { it.id }) { ep ->
                        EpisodeCard(vm, series, info, ep)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(vm: AppViewModel, series: Series, info: SeriesInfo, ep: Episode) {
    val p = remember(vm.progressVersion, ep.id) { vm.progress(ep.progressKey) }
    Column(Modifier.width(266.u), verticalArrangement = Arrangement.spacedBy(10.u)) {
        FocusCard(
            onClick = { vm.playEpisode(series, info, ep, fromStart = false) },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) { _ ->
            Poster("E${ep.number}", ep.image ?: info.backdrop, Modifier.fillMaxSize())
            if (p != null) {
                ProgressBar(
                    p.fraction, track = C.Bg.copy(alpha = 0.6f), fill = C.Text, height = 4.u,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
            val dur = fmtDuration(ep.durationSecs)
            T(if (dur.isNotEmpty()) "E${ep.number} · $dur" else "E${ep.number}", size = 12, color = C.Dim)
            T(ep.title, size = 15, weight = FontWeight.SemiBold)
        }
    }
}
