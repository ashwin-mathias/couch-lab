package com.sparky.iptv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.sparky.iptv.R
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Icon
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.Toast
import com.sparky.iptv.ui.components.u
import com.sparky.iptv.ui.screens.AboutScreen
import com.sparky.iptv.ui.screens.DiagnosticsScreen
import com.sparky.iptv.ui.screens.HomeSection
import com.sparky.iptv.ui.screens.FavoritesSection
import com.sparky.iptv.ui.screens.RecentSection
import com.sparky.iptv.ui.screens.RegionsScreen
import com.sparky.iptv.ui.screens.GuideScreen
import com.sparky.iptv.ui.screens.LiveScreen
import com.sparky.iptv.ui.screens.LoadingScreen
import com.sparky.iptv.ui.screens.MovieDetailScreen
import com.sparky.iptv.ui.screens.MoviesScreen
import com.sparky.iptv.ui.screens.PlayerScreen
import com.sparky.iptv.ui.screens.SearchScreen
import com.sparky.iptv.ui.screens.SeriesDetailScreen
import com.sparky.iptv.ui.screens.SeriesScreen
import com.sparky.iptv.ui.screens.SettingsScreen
import com.sparky.iptv.ui.screens.SetupScreen

@Composable
fun App(vm: AppViewModel) {
    MaterialTheme(colorScheme = darkColorScheme(background = C.Bg, surface = C.Bg, onSurface = C.Text)) {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            when (val s = vm.current) {
                Screen.Setup -> SetupScreen(vm)
                Screen.Loading -> LoadingScreen(vm)
                Screen.Home -> HomeScreen(vm)
                Screen.About -> AboutScreen(vm)
                Screen.Diagnostics -> DiagnosticsScreen(vm)
                is Screen.Regions -> RegionsScreen(vm, s.firstRun)
                is Screen.MovieDetail -> MovieDetailScreen(vm, s.movie)
                is Screen.SeriesDetail -> SeriesDetailScreen(vm, s.series)
                is Screen.Player -> PlayerScreen(vm, s.request)
            }
            Toast(
                text = vm.toast,
                onDone = { vm.toast = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.u),
            )
        }
    }
}

private data class NavEntry(val section: Section, val icon: Int, val label: String)

private val navEntries = listOf(
    NavEntry(Section.HOME, R.drawable.ic_home, "Home"),
    NavEntry(Section.LIVE, R.drawable.ic_live, "Live TV"),
    NavEntry(Section.GUIDE, R.drawable.ic_guide, "TV Guide"),
    NavEntry(Section.FAVORITES, R.drawable.ic_heart, "Favorites"),
    NavEntry(Section.RECENT, R.drawable.ic_recent, "Recently watched"),
    NavEntry(Section.MOVIES, R.drawable.ic_movies, "Movies"),
    NavEntry(Section.SERIES, R.drawable.ic_series, "Series"),
    NavEntry(Section.SEARCH, R.drawable.ic_search, "Search"),
)

@Composable
fun HomeScreen(vm: AppViewModel) {
    val activity = LocalContext.current as? Activity
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler {
        if (vm.section != Section.HOME) {
            vm.section = Section.HOME
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBack < 2000) activity?.finish() else {
                lastBack = now
                vm.toast = "Press back again to exit"
            }
        }
    }
    // Keep everything inside the TV's safe area: many TVs crop the outer few percent of the picture.
    Row(Modifier.fillMaxSize().padding(start = 36.u, end = 24.u, top = 16.u, bottom = 16.u)) {
        NavRail(vm)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (vm.section) {
                Section.HOME -> HomeSection(vm)
                Section.LIVE -> LiveScreen(vm)
                Section.GUIDE -> GuideScreen(vm)
                Section.FAVORITES -> FavoritesSection(vm)
                Section.RECENT -> RecentSection(vm)
                Section.MOVIES -> MoviesScreen(vm)
                Section.SERIES -> SeriesScreen(vm)
                Section.SEARCH -> SearchScreen(vm)
                Section.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun NavRail(vm: AppViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (expanded) 240.u else 72.u)
            .background(if (expanded) C.Panel else C.Bg)
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 22.u, horizontal = 12.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.u)) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(48.u).clip(RoundedCornerShape(12.u)),
            )
            if (expanded) {
                Spacer(Modifier.width(12.u))
                T("Sparky IPTV", size = 15, weight = FontWeight.Bold)
            }
        }
        navEntries.forEach { e -> RailButton(e, vm, expanded) }
        Spacer(Modifier.weight(1f))
        RailButton(NavEntry(Section.SETTINGS, R.drawable.ic_settings, "Settings"), vm, expanded)
    }
}

@Composable
private fun RailButton(e: NavEntry, vm: AppViewModel, expanded: Boolean) {
    val selected = vm.section == e.section
    FocusItem(
        onClick = { vm.section = e.section },
        selected = selected,
        modifier = Modifier.height(48.u).let { if (expanded) it.width(216.u) else it.width(48.u) },
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 13.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Icon(e.icon, tint = if (focused) C.OnFocus else if (selected) C.Text else C.Muted)
                if (e.section == Section.SETTINGS && vm.update != null) {
                    Box(Modifier.align(Alignment.TopEnd).size(8.u).clip(RoundedCornerShape(50)).background(C.Accent))
                }
            }
            if (expanded) {
                Spacer(Modifier.width(14.u))
                T(
                    e.label, size = 15,
                    weight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (focused) C.OnFocus else if (selected) C.Text else C.Muted,
                )
            }
        }
    }
}
