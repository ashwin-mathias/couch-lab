package com.sparky.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.sparky.iptv.R
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.Screen
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.ChannelLogo
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Icon
import com.sparky.iptv.ui.components.Inter
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.fmtDate
import com.sparky.iptv.ui.components.su
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(vm: AppViewModel) {
    val q = vm.searchQuery.trim()
    val lib = vm.library
    val chans = remember(lib, q) { if (q.length < 2) emptyList() else lib.channels.filter { it.name.contains(q, true) }.take(40) }
    val movies = remember(lib, q) { if (q.length < 2) emptyList() else lib.movies.filter { it.name.contains(q, true) }.take(40) }
    val series = remember(lib, q) { if (q.length < 2) emptyList() else lib.series.filter { it.name.contains(q, true) }.take(40) }
    val fieldFocus = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { delay(150); runCatching { fieldFocus.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.u, end = 48.u, top = 32.u, bottom = 40.u),
        verticalArrangement = Arrangement.spacedBy(28.u),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.u)) {
                T("Search", size = 30, weight = FontWeight.Bold)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.u)
                        .background(C.Panel, RoundedCornerShape(14.u))
                        .border(if (fieldFocused) 2.u else 1.u, if (fieldFocused) C.Text else C.Line2, RoundedCornerShape(14.u))
                        .padding(horizontal = 18.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(R.drawable.ic_search, tint = C.Muted, size = 20.u)
                    Spacer(Modifier.width(14.u))
                    Box(Modifier.weight(1f)) {
                        if (vm.searchQuery.isEmpty()) T("Channels, movies or series", size = 17, color = C.Dim)
                        BasicTextField(
                            value = vm.searchQuery,
                            onValueChange = { vm.searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Inter, fontSize = 17.su, color = C.Text),
                            cursorBrush = SolidColor(C.Text),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(fieldFocus)
                                .onFocusChanged { fieldFocused = it.isFocused },
                        )
                    }
                }
                if (q.length < 2) T("Press OK to type. Results appear as you type.", size = 14, color = C.Dim)
                else if (chans.isEmpty() && movies.isEmpty() && series.isEmpty()) T("No matches for “$q”.", size = 15, color = C.Muted)
            }
        }
        if (chans.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.u)) {
                Label("Channels")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                    items(chans, key = { it.id }) { ch ->
                        FocusItem(
                            onClick = { vm.playLive(chans, chans.indexOf(ch)) },
                            onLongClick = { vm.toggleFavorite(ch.favKey, ch.name) },
                            container = C.Panel,
                            modifier = Modifier.width(260.u).height(64.u),
                        ) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.u), verticalAlignment = Alignment.CenterVertically) {
                                ChannelLogo(ch.name, ch.logo, 40.u, focused = f)
                                Spacer(Modifier.width(12.u))
                                T(ch.name, size = 15, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text)
                            }
                        }
                    }
                }
            }
        }
        if (movies.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.u)) {
                Label("Movies")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u)) {
                    items(movies, key = { it.id }) { m ->
                        Box(Modifier.width(140.u)) {
                            PosterTile(m.name, m.poster, m.favKey in vm.favorites,
                                onClick = { vm.push(Screen.MovieDetail(m)) },
                                onLongClick = { vm.toggleFavorite(m.favKey, m.name) })
                        }
                    }
                }
            }
        }
        if (series.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.u)) {
                Label("Series")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u)) {
                    items(series, key = { it.id }) { s ->
                        Box(Modifier.width(140.u)) {
                            PosterTile(s.name, s.cover, s.favKey in vm.favorites,
                                onClick = { vm.push(Screen.SeriesDetail(s)) },
                                onLongClick = { vm.toggleFavorite(s.favKey, s.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = vm.prefs.playlist
    val acct = vm.prefs.account
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.u, vertical = 32.u), verticalArrangement = Arrangement.spacedBy(24.u)) {
        T("Settings", size = 30, weight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(48.u)) {
            Column(Modifier.width(520.u), verticalArrangement = Arrangement.spacedBy(10.u)) {
                SettingRow("Change login", "Scan a QR code and enter a new login on your phone", Modifier.focusRequester(firstFocus)) { vm.changeLogin() }
                SettingRow(if (vm.refreshing) "Updating playlist…" else "Update playlist", "Fetch the latest channels, movies and series") { vm.refreshInBackground(showToast = true) }
                SettingRow("Clear watch history", "Removes resume points for movies and episodes") { vm.clearHistory() }
                SettingRow("About Sparky IPTV", "Who made it, and what's new in each version") { vm.push(Screen.About) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.u)) {
                Label("Playlist")
                Info("Name", p?.name ?: "—")
                Info("Type", if (p?.type == "m3u") "M3U link" else "Xtream login")
                Info("Server", p?.url ?: "—")
                if (p?.type == "xtream") Info("Username", p?.user ?: "")
                if (acct != null) {
                    acct.status?.let { Info("Status", it) }
                    acct.expires?.let { Info("Expires", fmtDate(it)) }
                    acct.maxConnections?.let { Info("Connections", it) }
                }
                Info("Library", "${vm.library.channels.size} channels · ${vm.library.movies.size} movies · ${vm.library.series.size} series")
                Spacer(Modifier.height(8.u))
                Label("App")
                Info("Version", "Sparky IPTV $version")
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusItem(onClick = onClick, container = C.Panel, modifier = modifier.fillMaxWidth().height(76.u)) { f ->
        Column(Modifier.fillMaxSize().padding(horizontal = 20.u), verticalArrangement = Arrangement.Center) {
            T(title, size = 17, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text)
            Spacer(Modifier.height(4.u))
            T(sub, size = 13, color = if (f) C.OnFocusMuted else C.Dim)
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row {
        T(label, size = 14, color = C.Dim, modifier = Modifier.width(140.u))
        T(value, size = 14, color = C.Text2, modifier = Modifier.weight(1f))
    }
}
