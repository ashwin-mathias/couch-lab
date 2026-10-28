package com.sparky.iptv.ui

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.LoadListener
import com.sparky.iptv.data.LoadStep
import com.sparky.iptv.data.UpdateInfo
import com.sparky.iptv.data.Updater
import com.sparky.iptv.data.Movie
import com.sparky.iptv.data.MovieInfo
import com.sparky.iptv.data.Prefs
import com.sparky.iptv.data.Progress
import com.sparky.iptv.data.Names
import com.sparky.iptv.data.RecentItem
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
    data object About : Screen
    data object Diagnostics : Screen
    data class Regions(val firstRun: Boolean) : Screen
    data class MovieDetail(val movie: Movie) : Screen
    data class SeriesDetail(val series: Series) : Screen
    data class Player(val request: PlayRequest) : Screen
}

enum class Section { HOME, LIVE, GUIDE, FAVORITES, RECENT, MOVIES, SERIES, SEARCH, SETTINGS }

sealed interface PlayRequest {
    data class Live(val channels: List<Channel>, val index: Int) : PlayRequest
    data class Vod(val items: List<VodItem>, val index: Int, val resumeMs: Long) : PlayRequest
}

data class VodItem(val key: String, val title: String, val subtitle: String, val url: String, val seriesId: String? = null, val episodeId: String? = null)

const val GROUP_FAV = "__fav"
const val GROUP_ALL = "__all"
const val GROUP_RECENT = "__recent"
const val GROUP_ACTION_ALL_REGIONS = "__toggle_regions"

enum class GroupKind { GROUP, HEADER, ACTION }

/** One row in a group list: a channel group, a region heading, or an action. */
data class GroupItem(val id: String, val name: String, val count: Int, val kind: GroupKind = GroupKind.GROUP)

data class RegionInfo(val code: String, val label: String, val channels: Int)

/** Lookups derived from the library: which region each category belongs to, cleaned names, counts. */
class LiveIndex(lib: Library) {
    val catRegion: Map<String, String> = lib.liveCats.associate { it.id to (Names.region(it.name) ?: Names.OTHER) }
    val catName: Map<String, String> = lib.liveCats.associate { it.id to Names.strip(it.name) }
    val vodRegion: Map<String, String> = lib.vodCats.associate { it.id to (Names.region(it.name) ?: Names.OTHER) }
    val seriesRegion: Map<String, String> = lib.seriesCats.associate { it.id to (Names.region(it.name) ?: Names.OTHER) }
    val countByCat: Map<String, Int> = lib.channels.asSequence().filter { !it.divider }.groupingBy { it.categoryId }.eachCount()
    val regions: List<RegionInfo> = run {
        val counts = HashMap<String, Int>()
        for ((cat, n) in countByCat) {
            val r = catRegion[cat] ?: Names.OTHER
            counts[r] = (counts[r] ?: 0) + n
        }
        counts.map { (code, n) -> RegionInfo(code, Names.regionLabel(code), n) }.sortedByDescending { it.channels }
    }

    fun regionOfChannel(ch: Channel): String = catRegion[ch.categoryId] ?: Names.OTHER

    companion object {
        val EMPTY = LiveIndex(Library.EMPTY)
    }
}
const val CAT_RECENT = "__recent"
const val CAT_FAV = "__fav"
const val CAT_ALL = "__all"

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val repo = Repository(app, prefs)

    val stack = mutableStateListOf<Screen>()
    val current: Screen get() = stack.lastOrNull() ?: Screen.Loading

    var section by mutableStateOf(Section.HOME)
    var library by mutableStateOf(Library.EMPTY)
        private set
    var index by mutableStateOf(LiveIndex.EMPTY)
        private set

    private fun setLib(lib: Library) {
        library = lib
        index = LiveIndex(lib)
    }
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


    // ---------- navigation ----------
    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }
    private fun resetTo(s: Screen) { stack.clear(); stack.add(s) }

    // ---------- loading ----------
    val loadSteps = mutableStateListOf<LoadStep>()
    var loadStartedAt by mutableLongStateOf(0L)
        private set

    private val stepListener = LoadListener { i, status, detail ->
        viewModelScope.launch(Dispatchers.Main) {
            if (i in loadSteps.indices) loadSteps[i] = loadSteps[i].copy(status = status, detail = detail)
        }
    }

    fun load() {
        viewModelScope.launch {
            loadError = null
            loadSteps.clear()
            loadSteps.addAll(repo.stepsFor(prefs.playlist))
            loadStartedAt = System.currentTimeMillis()
            val hasSaved = repo.hasCache()
            if (hasSaved) {
                loadSteps.clear()
                loadSteps.add(LoadStep("Opening your saved library", com.sparky.iptv.data.StepStatus.ACTIVE, "Reading…"))
            }
            val cached = try { repo.loadCached() } catch (t: Throwable) { AppLog.e("Saved library unreadable", t); null }
            if (cached != null && !cached.isEmpty) {
                setLib(cached)
                AppLog.i("Opened saved library: ${cached.channels.size} channels, ${cached.movies.size} movies, ${cached.series.size} series")
                resetTo(Screen.Home)
                maybeDailyRefresh()
                checkForUpdate()
                maybePromptRegions()
            } else {
                if (hasSaved) { loadSteps.clear(); loadSteps.addAll(repo.stepsFor(prefs.playlist)) }
                try {
                    refreshing = true
                    setLib(repo.refresh(stepListener))
                    prefs.lastRefresh = System.currentTimeMillis()
                    resetTo(Screen.Home)
                    checkForUpdate()
                    maybePromptRegions()
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    AppLog.e("Loading failed", t)
                    loadError = when (t) {
                        is OutOfMemoryError -> "Your provider's catalog was too large to load on this device."
                        else -> t.message ?: "Couldn't reach your provider."
                    }
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
                setLib(repo.refresh())
                prefs.lastRefresh = System.currentTimeMillis()
                if (showToast) toast = "Playlist updated"
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                AppLog.e("Background update failed", t)
                if (showToast) toast = "Couldn't update: ${t.message}"
            } finally {
                refreshing = false
            }
        }
    }

    // ---------- app updates ----------
    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateProgress by mutableStateOf<String?>(null)
        private set
    private var updateNoticeShown = false

    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) toast = "Checking for updates…"
            val u = Updater.check(getApplication(), repo)
            update = u
            if (u != null && (!updateNoticeShown || manual)) {
                updateNoticeShown = true
                toast = "Update available — open Settings to install"
            } else if (u == null && manual) {
                toast = "You have the latest version"
            }
        }
    }

    fun installUpdate() {
        val u = update ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            toast = "Allow Sparky IPTV to install updates, then press Back"
            Updater.openInstallPermission(app)
            return
        }
        if (updateProgress != null) return
        viewModelScope.launch {
            try {
                updateProgress = "Starting download…"
                val f = Updater.apkFile(app)
                repo.download(u.apkUrl, f) { b ->
                    viewModelScope.launch(Dispatchers.Main) { updateProgress = "Downloading · ${Repository.mb(b)}" }
                }
                AppLog.i("Downloaded update build ${u.build} (${Repository.mb(f.length())})")
                updateProgress = null
                Updater.launchInstaller(app)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                AppLog.e("Update download failed", t)
                updateProgress = null
                toast = "Update failed: ${t.message}"
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
                        setLib(Library.EMPTY)
                        resetTo(Screen.Loading)
                        load()
                        delay(3000)
                        stopSetup()
                    }
                    null
                },
                onFailure = { AppLog.e("Phone setup login failed", it); it.message ?: "Couldn't log in." },
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
    // ---------- regions ----------
    var myRegions by mutableStateOf(prefs.myRegions())
        private set

    /** Temporarily show every region (from the bottom of the group list). */
    var showAllRegions by mutableStateOf(false)

    fun regionVisible(code: String) = showAllRegions || myRegions.isEmpty() || code in myRegions

    fun toggleRegion(code: String) {
        val next = myRegions.toMutableSet()
        if (!next.add(code)) next.remove(code)
        myRegions = next
        prefs.saveMyRegions(next)
        val special = setOf(GROUP_FAV, GROUP_ALL, GROUP_RECENT)
        if (liveGroup !in special && !regionVisible(index.catRegion[liveGroup] ?: Names.OTHER)) liveGroup = GROUP_ALL
    }

    fun resetRegions() {
        myRegions = emptySet()
        prefs.saveMyRegions(emptySet())
    }

    private fun maybePromptRegions() {
        if (prefs.regionsPrompted) return
        prefs.regionsPrompted = true
        if (index.regions.size >= 4) push(Screen.Regions(firstRun = true))
    }

    /** Group list for Live TV and the guide: Favorites, Recent, All, then each visible region's groups. */
    fun groupItems(): List<GroupItem> {
        val idx = index
        val favCount = library.channels.count { it.favKey in favorites }
        val recentCount = recents.count { it.type == "live" }
        val out = ArrayList<GroupItem>()
        out.add(GroupItem(GROUP_FAV, "Favorites", favCount))
        out.add(GroupItem(GROUP_RECENT, "Recently watched", recentCount))
        val allLabel = if (myRegions.isEmpty() || showAllRegions) "All channels" else "All my channels"
        out.add(GroupItem(GROUP_ALL, allLabel, idx.regions.filter { regionVisible(it.code) }.sumOf { it.channels }))
        val byRegion = library.liveCats.groupBy { idx.catRegion[it.id] ?: Names.OTHER }
        val codes = idx.regions.map { it.code }.filter { regionVisible(it) }
        // Your chosen regions come first.
        val order = if (myRegions.isEmpty()) codes else codes.sortedBy { if (it in myRegions) 0 else 1 }
        for (code in order) {
            val cats = byRegion[code].orEmpty().filter { (idx.countByCat[it.id] ?: 0) > 0 }
            if (cats.isEmpty()) continue
            out.add(GroupItem("hdr:$code", Names.regionLabel(code), cats.sumOf { idx.countByCat[it.id] ?: 0 }, GroupKind.HEADER))
            cats.forEach { c -> out.add(GroupItem(c.id, idx.catName[c.id] ?: c.name, idx.countByCat[c.id] ?: 0)) }
        }
        if (myRegions.isNotEmpty()) {
            val label = if (showAllRegions) "Show only my regions" else "Show all regions"
            out.add(GroupItem(GROUP_ACTION_ALL_REGIONS, label, 0, GroupKind.ACTION))
        }
        return out
    }

    // ---------- lists ----------
    fun channelsFor(group: String): List<Channel> {
        val all = library.channels
        return when (group) {
            GROUP_ALL -> if (myRegions.isEmpty() || showAllRegions) all
                else all.filter { regionVisible(index.regionOfChannel(it)) }
            GROUP_FAV -> all.filter { it.favKey in favorites }
            GROUP_RECENT -> recents.filter { it.type == "live" }.mapNotNull { r -> all.firstOrNull { it.id == r.id } }
            else -> all.filter { it.categoryId == group }
        }
    }

    private fun vodRegionOk(region: String?) = region == null || region == Names.OTHER || regionVisible(region)
    fun movieVisible(m: Movie) = vodRegionOk(index.vodRegion[m.categoryId])
    fun seriesVisible(s: Series) = vodRegionOk(index.seriesRegion[s.categoryId])
    fun vodCatVisible(id: String, series: Boolean) = vodRegionOk(if (series) index.seriesRegion[id] else index.vodRegion[id])

    fun moviesFor(cat: String): List<Movie> {
        val all = library.movies
        val mine = if (myRegions.isEmpty()) all else all.filter { movieVisible(it) }
        return when (cat) {
            CAT_RECENT -> mine.sortedByDescending { it.added }.take(300)
            CAT_FAV -> all.filter { it.favKey in favorites }
            CAT_ALL -> mine
            else -> all.filter { it.categoryId == cat }
        }
    }

    fun seriesFor(cat: String): List<Series> {
        val all = library.series
        val mine = if (myRegions.isEmpty()) all else all.filter { seriesVisible(it) }
        return when (cat) {
            CAT_RECENT -> mine.sortedByDescending { it.lastModified }.take(300)
            CAT_FAV -> all.filter { it.favKey in favorites }
            CAT_ALL -> mine
            else -> all.filter { it.categoryId == cat }
        }
    }

    fun continueWatching(): List<Pair<Movie, Progress>> {
        val progress = prefs.allProgress("movie:").associateBy { it.key }
        if (progress.isEmpty()) return emptyList()
        return library.movies.asSequence()
            .mapNotNull { m -> progress[m.progressKey]?.let { m to it } }
            .sortedByDescending { it.second.ts }
            .take(20).toList()
    }

    // ---------- recently watched ----------
    var recents by mutableStateOf(prefs.recents())
        private set

    fun addRecent(type: String, id: String) {
        val next = listOf(RecentItem(type, id, System.currentTimeMillis())) +
            recents.filterNot { it.type == type && it.id == id }
        recents = next.take(12)
        prefs.saveRecents(recents)
    }

    sealed interface RecentEntry {
        data class Ch(val channel: Channel) : RecentEntry
        data class Mv(val movie: Movie) : RecentEntry
        data class Sr(val series: Series) : RecentEntry
    }

    fun recentEntries(): List<RecentEntry> {
        val lib = library
        return recents.mapNotNull { r ->
            when (r.type) {
                "live" -> lib.channels.firstOrNull { it.id == r.id }?.let { RecentEntry.Ch(it) }
                "movie" -> lib.movies.firstOrNull { it.id == r.id }?.let { RecentEntry.Mv(it) }
                "series" -> lib.series.firstOrNull { it.id == r.id }?.let { RecentEntry.Sr(it) }
                else -> null
            }
        }
    }

    // ---------- tile / list view ----------
    private val tileModes = mutableStateMapOf<String, Boolean>()
    fun isTiles(key: String): Boolean = tileModes[key] ?: prefs.tileView(key, default = key == GROUP_RECENT)
    fun setTiles(key: String, on: Boolean) {
        tileModes[key] = on
        prefs.setTileView(key, on)
    }

    // ---------- daily refresh ----------
    fun maybeDailyRefresh() {
        val age = System.currentTimeMillis() - prefs.lastRefresh
        if (age > 24 * 60 * 60_000L) {
            AppLog.i("Daily refresh: last update ${age / 3_600_000}h ago")
            refreshInBackground()
        }
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
        val target = channels.getOrNull(index) ?: return
        if (target.divider) return
        if (channels.any { it.divider }) {
            val playable = channels.filter { !it.divider }
            playLive(playable, playable.indexOf(target).coerceAtLeast(0))
            return
        }
        liveHandoff = true
        prefs.lastChannel = channels[index].id
        push(Screen.Player(PlayRequest.Live(channels, index)))
    }

    fun playMovie(m: Movie, fromStart: Boolean) {
        addRecent("movie", m.id)
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
        addRecent("series", series.id)
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

    // Runs last, so every property above is ready before loading starts.
    init {
        if (prefs.playlist == null) {
            stack.add(Screen.Setup)
        } else {
            stack.add(Screen.Loading)
            load()
        }
        if (favorites.any { it.startsWith("live:") }) liveGroup = GROUP_FAV
    }
}
