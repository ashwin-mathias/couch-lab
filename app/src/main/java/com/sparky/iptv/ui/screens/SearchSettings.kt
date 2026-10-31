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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One search's matches, tagged with the query they belong to. */
private class SearchResults(
    val channels: List<com.sparky.iptv.data.Channel>,
    val movies: List<com.sparky.iptv.data.Movie>,
    val series: List<com.sparky.iptv.data.Series>,
    val query: String,
) {
    companion object { val EMPTY = SearchResults(emptyList(), emptyList(), emptyList(), "") }
}

@Composable
fun SearchScreen(vm: AppViewModel) {
    val q = vm.searchQuery.trim()
    val lib = vm.library
    // Search only your regions (favorites are always included). It runs off the UI thread, 250 ms
    // after the last key, so typing stays smooth on large catalogs (2.8).
    var results by remember { mutableStateOf(SearchResults.EMPTY) }
    LaunchedEffect(lib, q, vm.myRegions, vm.showAllRegions) {
        if (q.length < 2) { results = SearchResults.EMPTY; return@LaunchedEffect }
        delay(250)
        val favs = vm.favorites
        val index = vm.index
        results = withContext(Dispatchers.Default) {
            SearchResults(
                lib.channels.asSequence()
                    .filter { !it.divider && it.name.contains(q, true) && (it.favKey in favs || vm.regionVisible(index.regionOfChannel(it))) }
                    .take(40).toList(),
                lib.movies.asSequence().filter { it.name.contains(q, true) && vm.movieVisible(it) }.take(40).toList(),
                lib.series.asSequence().filter { it.name.contains(q, true) && vm.seriesVisible(it) }.take(40).toList(),
                q,
            )
        }
    }
    val current = results.takeIf { it.query == q } ?: SearchResults.EMPTY
    val chans = current.channels
    val movies = current.movies
    val series = current.series
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
                // "No matches" only once this query has finished, not while it's still running.
                else if (results.query == q && chans.isEmpty() && movies.isEmpty() && series.isEmpty()) T("No matches for “$q”.", size = 15, color = C.Muted)
            }
        }
        if (chans.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.u)) {
                Label("Channels")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                    items(chans) { ch ->
                        FocusItem(
                            onClick = { vm.playLive(chans, chans.indexOf(ch)) },
                            onLongClick = { vm.holdFavorite(ch.favKey, ch.display) },
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
                    items(movies) { m ->
                        Box(Modifier.width(140.u)) {
                            PosterTile(m.name, m.poster, m.favKey in vm.favorites,
                                onClick = { vm.push(Screen.MovieDetail(m)) },
                                onLongClick = { vm.holdFavorite(m.favKey, m.name) })
                        }
                    }
                }
            }
        }
        if (series.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.u)) {
                Label("Series")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u)) {
                    items(series) { s ->
                        Box(Modifier.width(140.u)) {
                            PosterTile(s.name, s.cover, s.favKey in vm.favorites,
                                onClick = { vm.push(Screen.SeriesDetail(s)) },
                                onLongClick = { vm.holdFavorite(s.favKey, s.name) })
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
    var sleepDefault by remember { mutableIntStateOf(vm.prefs.sleepDefault) }
    fun setSleepDefault(m: Int) { sleepDefault = m; vm.prefs.sleepDefault = m }
    // Clear watch history asks for a second OK within 3 s.
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(confirmClear) { if (confirmClear) { delay(3000); confirmClear = false } }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(start = 48.u, end = 48.u, top = 24.u, bottom = 16.u), verticalArrangement = Arrangement.spacedBy(16.u)) {
        T("Settings", size = 30, weight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(48.u)) {
            // Fits on screen at 1080p; scrolls only as a fallback (for example with an update row).
            Column(Modifier.width(520.u).verticalScroll(rememberScrollState()).padding(bottom = 24.u), verticalArrangement = Arrangement.spacedBy(4.u)) {
                val upd = vm.update
                if (upd != null) {
                    SettingRow(
                        vm.updateProgress ?: "Update available — install now",
                        upd.title,
                        Modifier.focusRequester(firstFocus), highlight = true,
                    ) { vm.installUpdate() }
                }

                GroupHeading("Playback", first = upd == null)
                SettingRow(
                    "Subtitle style", com.sparky.iptv.player.SubtitleStyleState.style.summary,
                    if (upd == null) Modifier.focusRequester(firstFocus) else Modifier,
                ) { vm.push(Screen.SubtitleStyle) }
                // ◀ ▶ change the length; OK steps 30 → 60 → 90 → 30.
                val choices = com.sparky.iptv.data.SLEEP_CHOICES
                SettingRow(
                    "Sleep timer default", "$sleepDefault min",
                    Modifier.onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val i = choices.indexOf(sleepDefault).coerceAtLeast(0)
                        when (ev.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { setSleepDefault(choices[(i - 1).coerceAtLeast(0)]); true }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { setSleepDefault(choices[(i + 1).coerceAtMost(choices.lastIndex)]); true }
                            else -> false
                        }
                    },
                ) {
                    val i = choices.indexOf(sleepDefault).coerceAtLeast(0)
                    setSleepDefault(choices[(i + 1) % choices.size])
                }

                GroupHeading("Library")
                val regionSummary = when {
                    vm.myRegions.isEmpty() -> "All regions"
                    else -> vm.myRegions.joinToString(", ") { com.sparky.iptv.data.Names.regionLabel(it) }
                }
                SettingRow("My regions", regionSummary) { vm.push(Screen.Regions(firstRun = false)) }
                val nf = vm.folders.size
                SettingRow("Favorite folders", if (nf == 0) "None yet" else if (nf == 1) "1 folder" else "$nf folders") { vm.push(Screen.FavFolders) }
                val last = vm.prefs.lastRefresh
                val ago = when {
                    vm.refreshing -> "Updating…"
                    last <= 0L -> "Not updated yet"
                    else -> {
                        val mins = (System.currentTimeMillis() - last) / 60_000
                        when {
                            mins < 1 -> "Updated just now"
                            mins < 60 -> "Updated $mins min ago"
                            mins < 48 * 60 -> "Updated ${mins / 60} h ago"
                            else -> "Updated ${mins / 1440} days ago"
                        }
                    }
                }
                SettingRow("Update playlist now", ago) { vm.refreshInBackground(showToast = true) }
                SettingRow(if (confirmClear) "Press OK again to clear" else "Clear watch history", "") {
                    if (confirmClear) { confirmClear = false; vm.clearHistory() } else confirmClear = true
                }

                GroupHeading("Account")
                SettingRow("Change login", p?.name ?: "") { vm.changeLogin() }

                GroupHeading("App")
                SettingRow("Version", version) { vm.checkForUpdate(manual = true) }
                SettingRow("Back up settings", "To your phone") { vm.push(Screen.BackupRestore(restore = false)) }
                SettingRow("Restore settings", "From a backup file") { vm.push(Screen.BackupRestore(restore = true)) }
                SettingRow("Diagnostics", "") { vm.push(Screen.Diagnostics) }
                SettingRow("About Sparky IPTV", "") { vm.push(Screen.About) }
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
            }
        }
    }
}

/** Group heading in Settings: the Label style, 16 px above (none for the first) and 6 px below. */
@Composable
private fun GroupHeading(text: String, first: Boolean = false) {
    Label(text, modifier = Modifier.padding(start = 4.u, top = if (first) 0.u else 12.u, bottom = 2.u))
}

/** Single-line Settings row, 48 px: title on the left, current value on the right. */
@Composable
private fun SettingRow(title: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    FocusItem(onClick = onClick, container = if (highlight) C.Raised else C.Panel, modifier = modifier.fillMaxWidth().height(48.u)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 20.u), verticalAlignment = Alignment.CenterVertically) {
            T(title, size = 17, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text)
            Spacer(Modifier.width(16.u))
            T(
                value, size = 14, color = if (f) C.OnFocusMuted else C.Dim,
                modifier = Modifier.weight(1f), textAlign = TextAlign.End,
            )
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
