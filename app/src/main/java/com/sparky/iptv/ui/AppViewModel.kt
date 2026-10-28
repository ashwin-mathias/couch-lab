package com.sparky.iptv.ui

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sparky.iptv.data.Channel
import com.sparky.iptv.data.EpgEntry
import com.sparky.iptv.data.Episode
import com.sparky.iptv.data.Library
import com.sparky.iptv.data.Movie
import com.sparky.iptv.data.MovieInfo
import com.sparky.iptv.data.Prefs
import com.sparky.iptv.data.Progress
import com.sparky.iptv.data.Repository
import com.sparky.iptv.data.Series
import com.sparky.iptv.data.SeriesInfo
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.setup.Net
import com.sparky.iptv.setup.SetupServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Setup : Screen
    data object Loading : Screen
    data object Home : Screen
    data class MovieDetail(val movie: Movie) : Screen
    data class SeriesDetail(val series: Series) : Screen
    data class Player(val request: PlayRequest) : Screen
}

enum class Section { LIVE, GUIDE, MOVIES, SERIES, SEARCH, SETTINGS }

sealed interface PlayRequest {
    data class Live(val channels: List<Channel>, val index: Int) : PlayRequest
    data class Vod(val items: List<VodItem>, val index: Int, val resumeMs: Long) : PlayRequest
}

data class VodItem(val key: String, val title: String, val subtitle: String, val url: String, val seriesId: String? = null, val episodeId: String? = null)

const val GROUP_FAV = "__fav"
const val GROUP_ALL = "__all"
const val CAT_RECENT = "__recent"
const val CAT_FAV = "__fav"
const val CAT_ALL = "__all"

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val repo = Repository(app, prefs)

    val stack = mutableStateListOf<Screen>()
    val current: Screen get() = stack.lastOrNull() ?: Screen.Loading

    var section by mutableStateOf(Section.LIVE)
    var library by mutableStateOf(Library.EMPTY)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var favorites by mutableStateOf(prefs.favorites())
        private set
    var progressVersion by mutableIntStateOf(0)
        private set
    var toast by mutableStateOf<String?>(null)

    var liveGroup by mutableStateOf(GROUP_ALL)
    var movieCat by mutableStateOf(CAT_RECENT)
    var seriesCat by mutableStateOf(CAT_ALL)
    var searchQuery by mutableStateOf("")

    /** Set just before opening the full-screen live player so the preview keeps playing. */
    var liveHandoff = false

    // EPG
    val epg = mutableStateMapOf<String, List<EpgEntry>>()
    private val epgFetchedAt = HashMap<String, Long>()
    private val epgInFlight = HashSet<String>()
    private val epgLimit = Semaphore(4)

    // Phone setup
    var setupUrl by mutableStateOf<String?>(null)
        private set
    var setupBusy by mutableStateOf(false)
        private set
    private var server: SetupServer? = null

    init {
        if (prefs.playlist == null) {
            stack.add(Screen.Setup)
        } else {
            stack.add(Screen.Loading)
            load()
        }
        if (favorites.any { it.startsWith("live:") }) liveGroup = GROUP_FAV
    }

    // ---------- navigation ----------
    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }
    private fun resetTo(s: Screen) { stack.clear(); stack.add(s) }

    // ---------- loading ----------
    fun load() {
        viewModelScope.launch {
            loadError = null
            val cached = repo.loadCached()
            if (cached != null && !cached.isEmpty) {
                library = cached
                resetTo(Screen.Home)
                refreshInBackground()
            } else {
                try {
                    refreshing = true
                    library = repo.refresh()
                    resetTo(Screen.Home)
                } catch (e: Exception) {
                    loadError = e.message ?: "Couldn't reach your provider."
                } finally {
                    refreshing = false
                }
            }
        }
    }

    fun refreshInBackground(showToast: Boolean = false) {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                library = repo.refresh()
                if (showToast) toast = "Playlist updated"
            } catch (e: Exception) {
                if (showToast) toast = "Couldn't update: ${e.message}"
            } finally {
                refreshing = false
            }
        }
    }

    // ---------- setup from phone ----------
    fun startSetup() {
        stopSetup()
        val app = getApplication<Application>()
        val ip = Net.localIp(app)
        val name = runCatching { Settings.Global.getString(app.contentResolver, "device_name") }.getOrNull()
            ?: Build.MODEL ?: "Your TV"
        val s = SetupServer.startOnFreePort(name) { p ->
            runBlocking(Dispatchers.Main) { setupBusy = true }
            val result = runBlocking(Dispatchers.IO) { runCatching { repo.validate(p) } }
            runBlocking(Dispatchers.Main) { setupBusy = false }
            result.fold(
                onSuccess = { acct ->
                    viewModelScope.launch {
                        prefs.playlist = p
                        prefs.account = acct
                        prefs.liveFormat = acct.liveFormat
                        repo.clearCache()
                        epg.clear(); epgFetchedAt.clear()
                        library = Library.EMPTY
                        resetTo(Screen.Loading)
                        load()
                        delay(3000)
                        stopSetup()
                    }
                    null
                },
                onFailure = { it.message ?: "Couldn't log in." },
            )
        }
        server = s
        setupUrl = if (s != null && ip != null) "http://$ip:${s.listeningPort}" else null
    }

    fun stopSetup() {
        server?.stop()
        server = null
    }

    fun changeLogin() {
        push(Screen.Setup)
    }

    override fun onCleared() {
        stopSetup()
        super.onCleared()
    }

    // ---------- favorites ----------
    fun isFav(key: String) = key in favorites

    fun toggleFavorite(key: String, label: String? = null) {
        val now = favorites.toMutableSet()
        val added = now.add(key)
        if (!added) now.remove(key)
        favorites = now
        prefs.saveFavorites(now)
        toast = if (added) "Added${label?.let { " $it" } ?: ""} to Favorites" else "Removed${label?.let { " $it" } ?: ""} from Favorites"
    }

    // ---------- lists ----------
    fun channelsFor(group: String): List<Channel> {
        val all = library.channels
        return when (group) {
            GROUP_ALL -> all
            GROUP_FAV -> all.filter { it.favKey in favorites }
            else -> all.filter { it.categoryId == group }
        }
    }

    fun moviesFor(cat: String): List<Movie> {
        val all = library.movies
        return when (cat) {
            CAT_RECENT -> all.sortedByDescending { it.added }.take(300)
            CAT_FAV -> all.filter { it.favKey in favorites }
            CAT_ALL -> all
            else -> all.filter { it.categoryId == cat }
        }
    }

    fun seriesFor(cat: String): List<Series> {
        val all = library.series
        return when (cat) {
            CAT_RECENT -> all.sortedByDescending { it.lastModified }.take(300)
            CAT_FAV -> all.filter { it.favKey in favorites }
            CAT_ALL -> all
            else -> all.filter { it.categoryId == cat }
        }
    }

    fun continueWatching(): List<Pair<Movie, Progress>> {
        val byId = library.movies.associateBy { it.progressKey }
        return prefs.allProgress("movie:")
            .sortedByDescending { it.ts }
            .mapNotNull { p -> byId[p.key]?.let { it to p } }
            .take(20)
    }

    fun progress(key: String): Progress? = prefs.progress(key)

    fun saveProgress(key: String, pos: Long, dur: Long) {
        if (dur <= 0) return
        if (pos > dur * 0.95 || dur - pos < 60_000) prefs.clearProgress(key)
        else if (pos > 30_000) prefs.saveProgress(key, pos, dur)
        progressVersion++
    }

    // ---------- EPG ----------
    fun ensureEpg(ch: Channel) {
        if (prefs.playlist?.type != "xtream") return
        val now = System.currentTimeMillis()
        val have = epg[ch.id]
        val fetched = epgFetchedAt[ch.id] ?: 0L
        val stale = when {
            have == null -> true
            have.isEmpty() -> now - fetched > 10 * 60_000
            else -> (have.lastOrNull()?.end ?: 0L) < now + 60 * 60_000 && now - fetched > 5 * 60_000
        }
        if (!stale || !epgInFlight.add(ch.id)) return
        viewModelScope.launch {
            try {
                val list = epgLimit.withPermit {
                    runCatching { withContext(Dispatchers.IO) { repo.shortEpg(ch) } }.getOrDefault(emptyList())
                }
                epg[ch.id] = list
                epgFetchedAt[ch.id] = System.currentTimeMillis()
            } finally {
                epgInFlight.remove(ch.id)
            }
        }
    }

    fun nowNext(chId: String, now: Long): Pair<EpgEntry?, EpgEntry?> {
        val list = epg[chId] ?: return null to null
        val i = list.indexOfFirst { now >= it.start && now < it.end }
        return if (i >= 0) list[i] to list.getOrNull(i + 1) else null to list.firstOrNull { it.start > now }
    }

    // ---------- details ----------
    suspend fun movieInfo(m: Movie): MovieInfo? = repo.movieInfo(m)
    suspend fun seriesInfo(s: Series): Result<SeriesInfo> = runCatching { repo.seriesInfo(s) }

    // ---------- playback ----------
    fun playLive(channels: List<Channel>, index: Int) {
        if (channels.isEmpty()) return
        liveHandoff = true
        prefs.lastChannel = channels[index].id
        push(Screen.Player(PlayRequest.Live(channels, index)))
    }

    fun playMovie(m: Movie, fromStart: Boolean) {
        val resume = if (fromStart) 0L else prefs.progress(m.progressKey)?.pos ?: 0L
        push(Screen.Player(PlayRequest.Vod(listOf(VodItem(m.progressKey, m.name, "", m.url)), 0, resume)))
    }

    fun playEpisode(series: Series, info: SeriesInfo, ep: Episode, fromStart: Boolean) {
        val all = info.seasons.flatMap { info.episodes[it].orEmpty() }
        val items = all.map {
            VodItem(it.progressKey, series.name, "S${it.season} · E${it.number}  ${it.title}", it.url, series.id, it.id)
        }
        val index = all.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
        val resume = if (fromStart) 0L else prefs.progress(ep.progressKey)?.pos ?: 0L
        prefs.setSeriesLastEpisode(series.id, ep.id)
        push(Screen.Player(PlayRequest.Vod(items, index, resume)))
    }

    fun onEpisodeStarted(item: VodItem) {
        val sid = item.seriesId ?: return
        val eid = item.episodeId ?: return
        prefs.setSeriesLastEpisode(sid, eid)
    }

    fun clearHistory() {
        prefs.clearAllProgress()
        progressVersion++
        toast = "Watch history cleared"
    }

    fun stopPreview() = PlayerHolder.stop()
}
