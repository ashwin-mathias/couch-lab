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
import com.sparky.iptv.data.FavFolder
import com.sparky.iptv.data.Favs
import com.sparky.iptv.data.Backup
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
import com.sparky.iptv.setup.PhoneServer
import com.sparky.iptv.setup.PhonePages
import com.sparky.iptv.data.Playlist
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
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
    data object SubtitleStyle : Screen
    data object FavFolders : Screen
    data class BackupRestore(val restore: Boolean) : Screen
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

data class VodItem(
    val key: String, val title: String, val subtitle: String, val url: String,
    val seriesId: String? = null, val episodeId: String? = null,
    /** Catch-up recordings don't keep resume points. */
    val saveProgress: Boolean = true,
)

const val SLEEP_WARNING = "Sleeping in 1 minute · press any key to keep watching"

const val GROUP_FAV = "__fav"
const val GROUP_ALL = "__all"
const val GROUP_RECENT = "__recent"
const val GROUP_ACTION_ALL_REGIONS = "__toggle_regions"

enum class GroupKind { GROUP, HEADER, ACTION }

/**
 * One row in a group list: a channel group, a region heading, or an action.
 * Headings carry their [region] and whether they're [open]; favorites folders are [indent]ed under Favorites.
 */
data class GroupItem(
    val id: String, val name: String, val count: Int, val kind: GroupKind = GroupKind.GROUP,
    val region: String? = null, val open: Boolean = true, val indent: Boolean = false,
)

/** Favorites folders appear as groups with this prefix plus the folder id. */
const val GROUP_FOLDER = "fav:"

/** Lists that can be reordered: all favorite channels / movies / series, one folder, or the folders themselves. */
const val LIST_LIVE = "all:live"
const val LIST_MOVIE = "all:movie"
const val LIST_SERIES = "all:series"
const val LIST_FOLDERS = "folders"
fun folderList(id: String) = "folder:$id"

/** A favorite being moved: [working] is the list as shown while it moves. Keys move by [upDown] / [leftRight] places. */
data class MoveSession(val listId: String, val key: String, val working: List<String>, val upDown: Int, val leftRight: Int)

/** The hold-OK menu for a favorite. [moveList] is null where the list can't be reordered. */
data class FavMenuRequest(val key: String, val label: String, val moveList: String?, val upDown: Int, val leftRight: Int)

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
    /** Overall loading progress when it can be measured directly (reading the saved copy). */
    var loadFraction by mutableStateOf<Float?>(null)
        private set
    /** True while movies and series are still being read in the background. */
    var vodLoading by mutableStateOf(false)
        private set
    var vodProgress by mutableStateOf<String?>(null)
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
            loadFraction = null
            val hasSaved = repo.hasCache()
            if (hasSaved) {
                loadSteps.clear()
                loadSteps.add(LoadStep("Opening your saved channels", com.sparky.iptv.data.StepStatus.ACTIVE, "Reading…"))
                loadFraction = 0.05f
            }
            // Step 1: live channels only, so Home opens within a few seconds.
            val cached = try {
                repo.loadCachedLive { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (loadSteps.isNotEmpty()) loadSteps[0] = loadSteps[0].copy(detail = msg)
                        msg.substringAfterLast(' ').removeSuffix("%").toIntOrNull()?.let { loadFraction = 0.05f + it / 100f * 0.9f }
                    }
                }
            } catch (t: Throwable) { AppLog.e("Saved library unreadable", t); null }
            if (cached != null && !cached.isEmpty) {
                loadFraction = 1f
                setLib(cached)
                resetTo(Screen.Home)
                checkForUpdate()
                maybePromptRegions()
                // Step 2: movies and series load in the background.
                if (prefs.playlist?.type != "m3u") {
                    vodLoading = true
                    try {
                        val full = repo.loadCachedVod(cached) { msg ->
                            viewModelScope.launch(Dispatchers.Main) { vodProgress = msg }
                        }
                        // A fresh download may have finished meanwhile; don't replace it with older data.
                        if (library.movies.isEmpty() && library.series.isEmpty()) {
                            setLib(Library(library.liveCats, library.channels, full.vodCats, full.movies, full.seriesCats, full.series))
                        }
                        onLibraryComplete()
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        AppLog.e("Loading movies/series failed", t)
                    } finally {
                        vodLoading = false
                        vodProgress = null
                    }
                } else {
                    onLibraryComplete()
                }
                maybeDailyRefresh()
            } else {
                if (hasSaved) { loadSteps.clear(); loadSteps.addAll(repo.stepsFor(prefs.playlist)) }
                try {
                    refreshing = true
                    setLib(repo.refresh(stepListener))
                    onLibraryComplete()
                    prefs.lastRefresh = System.currentTimeMillis()
                prefs.catchupScanned = true
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
                onLibraryComplete()
                prefs.lastRefresh = System.currentTimeMillis()
                prefs.catchupScanned = true
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
    private var installLockUntil = 0L

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
        // Ignore extra presses while downloading, and for 10 s after the installer opens.
        if (updateProgress != null || System.currentTimeMillis() < installLockUntil) return
        installLockUntil = System.currentTimeMillis() + 10_000
        viewModelScope.launch {
            try {
                updateProgress = "Starting download…"
                val f = Updater.apkFile(app)
                repo.download(u.apkUrl, f, expectedSha256 = u.sha256, requireHttps = true) { b ->
                    viewModelScope.launch(Dispatchers.Main) { updateProgress = "Downloading · ${Repository.mb(b)}" }
                }
                AppLog.i("Downloaded update build ${u.build} (${Repository.mb(f.length())})")
                updateProgress = null
                installLockUntil = System.currentTimeMillis() + 10_000
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
        restoreFails = 0
        val s = SetupServer.startOnFreePort(
            name,
            existing = prefs.playlist != null,
            onRestore = { body, token, lock -> restoreRequest(body, existing = prefs.playlist != null, setupToken = token, lock = lock) },
            restoredNote = {
                pendingRestore?.let { "Backup ready: ${it.favoriteCount} favorites and your settings. Enter your login to finish." }
            },
        ) { p ->
            runBlocking(Dispatchers.Main) { setupBusy = true }
            val result = runBlocking(Dispatchers.IO) { runCatching { repo.validate(p) } }
            runBlocking(Dispatchers.Main) { setupBusy = false }
            result.fold(
                onSuccess = { acct ->
                    viewModelScope.launch {
                        prefs.playlist = p
                        prefs.account = acct
                        prefs.liveFormat = acct.liveFormat
                        pendingRestore?.let { c ->
                            pendingRestore = null
                            runCatching { applyRestore(c, Backup.Mode.REPLACE, null, null) }
                                .onFailure { AppLog.e("Restore after login failed", it) }
                        }
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
        setupUrl = if (s != null && ip != null) "http://$ip:${s.listeningPort}/?k=${s.token}" else null
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

    /** Your order of all favorites. Until it's first saved (2.7), lists keep the provider's order. */
    var favOrder by mutableStateOf(Favs.normalize(prefs.favOrder() ?: emptyList(), favorites))
        private set
    private var favOrderSaved = prefs.favOrder() != null
    var folders by mutableStateOf(prefs.favFolders())
        private set

    private fun saveFavs(keys: Set<String>, order: List<String>, fs: List<FavFolder>) {
        favorites = keys
        favOrder = Favs.normalize(order, keys)
        folders = fs.map { f -> f.copy(keys = f.keys.filter { it in keys }) }
        prefs.saveFavorites(keys)
        if (favOrderSaved) prefs.saveFavOrder(favOrder)
        prefs.saveFavFolders(folders)
    }

    /** Adds or removes (used by the player and the series page, where there's no list to reorder). */
    fun toggleFavorite(key: String, label: String? = null) {
        val added = key !in favorites
        if (added) addFavorite(key) else removeFavorite(key)
        toast = if (added) "Added${label?.let { " $it" } ?: ""} to Favorites" else "Removed${label?.let { " $it" } ?: ""} from Favorites"
    }

    private fun addFavorite(key: String) = saveFavs(favorites + key, favOrder + key, folders)
    private fun removeFavorite(key: String) = saveFavs(favorites - key, favOrder - key, folders)

    /** The removal from the hold-OK menu. */
    fun removeFavoriteFromMenu(key: String, label: String) {
        removeFavorite(key)
        toast = "Removed $label from Favorites"
    }

    var favMenu by mutableStateOf<FavMenuRequest?>(null)
    private var favMenuPending: FavMenuRequest? = null
    /** Channel key whose folder checklist is open. */
    var folderPicker by mutableStateOf<String?>(null)

    /**
     * Hold OK on a channel, movie or series. Not a favorite yet: add it. Already a favorite: open the
     * menu (Move · Folders… · Remove) once OK is released, so the release doesn't reach the menu.
     */
    fun holdFavorite(key: String, label: String, moveList: String? = null, upDown: Int = 1, leftRight: Int = 0) {
        if (key !in favorites) {
            addFavorite(key)
            toast = if (key.startsWith("live:")) "Added to Favorites · hold OK again for folders" else "Added $label to Favorites"
            return
        }
        favMenuPending = FavMenuRequest(key, label, moveList, upDown, leftRight)
    }

    /** Hold OK in the full-screen player (already on release): add, or open the menu without Move. */
    fun holdFavoriteNow(key: String, label: String) {
        if (key !in favorites) {
            addFavorite(key)
            toast = "Added $label to Favorites"
            return
        }
        favMenu = FavMenuRequest(key, label, null, 0, 0)
    }

    // Lookups from favorite key to item, rebuilt when the library changes.
    private var lookupFor: Library? = null
    private var chByKey: Map<String, Channel> = emptyMap()
    private var mvByKey: Map<String, Movie> = emptyMap()
    private var srByKey: Map<String, Series> = emptyMap()
    private fun lookup() {
        val lib = library
        if (lookupFor === lib) return
        lookupFor = lib
        chByKey = lib.channels.asSequence().filter { !it.divider }.associateBy { it.favKey }
        mvByKey = lib.movies.associateBy { it.favKey }
        srByKey = lib.series.associateBy { it.favKey }
    }
    fun channelForKey(key: String): Channel? { lookup(); return chByKey[key] }
    fun movieForKey(key: String): Movie? { lookup(); return mvByKey[key] }
    fun seriesForKey(key: String): Series? { lookup(); return srByKey[key] }
    private fun present(key: String): Boolean = when {
        key.startsWith("live:") -> channelForKey(key) != null
        key.startsWith("movie:") -> movieForKey(key) != null
        key.startsWith("series:") -> seriesForKey(key) != null
        else -> folders.any { it.id == key }
    }

    /** Name for toasts and the menu. */
    fun nameOf(key: String): String? =
        channelForKey(key)?.display ?: movieForKey(key)?.name ?: seriesForKey(key)?.name ?: folders.firstOrNull { it.id == key }?.name

    /** Keys of a reorderable list, in the order shown (the moving order while something moves). */
    fun keysFor(listId: String): List<String> {
        moving?.let { if (it.listId == listId) return it.working }
        val prefix = when (listId) {
            LIST_LIVE -> "live:"
            LIST_MOVIE -> "movie:"
            LIST_SERIES -> "series:"
            LIST_FOLDERS -> return folders.map { it.id }
            else -> {
                val id = listId.removePrefix("folder:")
                return folders.firstOrNull { it.id == id }?.keys.orEmpty().filter { it in favorites }
            }
        }
        if (!favOrderSaved) {
            // Before 2.7 first saved an order: the provider's order, as in 2.6.
            lookup()
            val lib = library
            return when (prefix) {
                "live:" -> lib.channels.asSequence().filter { !it.divider && it.favKey in favorites }.map { it.favKey }.toList()
                "movie:" -> lib.movies.filter { it.favKey in favorites }.map { it.favKey }
                else -> lib.series.filter { it.favKey in favorites }.map { it.favKey }
            }
        }
        return favOrder.filter { it.startsWith(prefix) }
    }

    fun favChannels(): List<Channel> = keysFor(LIST_LIVE).mapNotNull { channelForKey(it) }
    fun favMovies(): List<Movie> = keysFor(LIST_MOVIE).mapNotNull { movieForKey(it) }
    fun favSeries(): List<Series> = keysFor(LIST_SERIES).mapNotNull { seriesForKey(it) }
    fun folderChannels(id: String): List<Channel> = keysFor(folderList(id)).mapNotNull { channelForKey(it) }

    /** Called when the whole library (channels, movies and series) is loaded. */
    private fun onLibraryComplete() {
        if (!favOrderSaved && !library.isEmpty) {
            // First start of 2.7: keep today's (provider) order.
            val start = keysFor(LIST_LIVE) + keysFor(LIST_MOVIE) + keysFor(LIST_SERIES)
            favOrderSaved = true
            favOrder = Favs.normalize(start, favorites)
            prefs.saveFavOrder(favOrder)
            AppLog.i("Favorites order set from the provider's order (${favOrder.size} items)")
        }
        reconcileRestore()
    }

    // ---------- moving a favorite ----------
    var moving by mutableStateOf<MoveSession?>(null)
        private set

    fun startMove(listId: String, key: String, upDown: Int, leftRight: Int) {
        val keys = keysFor(listId).filter { present(it) }
        if (key !in keys) return
        moving = MoveSession(listId, key, keys, upDown, leftRight)
    }

    private fun moveStep(delta: Int) {
        val m = moving ?: return
        if (delta == 0) return
        moving = m.copy(working = Favs.moveBy(m.working, m.key, delta))
    }

    private fun commitMove() {
        val m = moving ?: return
        moving = null
        val pos = m.working.indexOf(m.key) + 1
        when {
            m.listId == LIST_FOLDERS -> {
                val byId = folders.associateBy { it.id }
                folders = m.working.mapNotNull { byId[it] }
                prefs.saveFavFolders(folders)
            }
            m.listId.startsWith("folder:") -> {
                val id = m.listId.removePrefix("folder:")
                folders = folders.map { if (it.id == id) it.copy(keys = Favs.merge(it.keys, m.working)) else it }
                prefs.saveFavFolders(folders)
            }
            else -> {
                if (!favOrderSaved) {
                    favOrderSaved = true
                    favOrder = Favs.normalize(keysFor(LIST_LIVE) + keysFor(LIST_MOVIE) + keysFor(LIST_SERIES), favorites)
                }
                favOrder = Favs.merge(favOrder, m.working)
                prefs.saveFavOrder(favOrder)
            }
        }
        toast = "Moved ${nameOf(m.key) ?: "it"} to position $pos"
    }

    private fun cancelMove() {
        moving = null
        toast = "Move cancelled"
    }

    // ---------- favorites folders ----------
    fun folder(id: String): FavFolder? = folders.firstOrNull { it.id == id }

    /** Creates a folder (adding [withKey] to it); returns an error message or null. */
    fun createFolder(name: String, withKey: String? = null): String? {
        Favs.nameError(name, folders)?.let { return it }
        val f = FavFolder(Favs.newId(folders), name.trim(), listOfNotNull(withKey?.takeIf { it in favorites && it.startsWith("live:") }))
        folders = folders + f
        prefs.saveFavFolders(folders)
        toast = if (withKey != null) "Added to ${f.name}" else "Folder ${f.name} created"
        return null
    }

    fun renameFolder(id: String, name: String): String? {
        Favs.nameError(name, folders, exceptId = id)?.let { return it }
        folders = folders.map { if (it.id == id) it.copy(name = name.trim()) else it }
        prefs.saveFavFolders(folders)
        toast = "Renamed to ${name.trim()}"
        return null
    }

    fun deleteFolder(id: String) {
        val f = folder(id) ?: return
        folders = folders.filter { it.id != id }
        prefs.saveFavFolders(folders)
        if (liveGroup == GROUP_FOLDER + id) liveGroup = GROUP_FAV
        toast = "Folder ${f.name} deleted · its channels stay in Favorites"
    }

    fun setInFolder(id: String, key: String, on: Boolean) {
        folders = folders.map { f ->
            if (f.id != id) f
            else if (on) f.copy(keys = (f.keys - key) + key)
            else f.copy(keys = f.keys - key)
        }
        prefs.saveFavFolders(folders)
    }

    /** Applies favorites edited on the phone page (order of channels and the folders). */
    fun applyPhoneFavorites(liveOrder: List<String>, newFolders: List<FavFolder>) {
        if (!favOrderSaved) {
            favOrderSaved = true
            favOrder = Favs.normalize(keysFor(LIST_LIVE) + keysFor(LIST_MOVIE) + keysFor(LIST_SERIES), favorites)
        }
        val live = liveOrder.filter { it in favorites && it.startsWith("live:") }.distinct()
        favOrder = Favs.merge(favOrder, live)
        folders = newFolders.map { f -> f.copy(keys = f.keys.filter { it in favorites }.distinct()) }
        prefs.saveFavOrder(favOrder)
        prefs.saveFavFolders(folders)
        if (liveGroup.startsWith(GROUP_FOLDER) && folder(liveGroup.removePrefix(GROUP_FOLDER)) == null) liveGroup = GROUP_FAV
        toast = "Favorites updated from your phone"
        AppLog.i("Favorites updated from the phone: ${folders.size} folders")
    }

    // ---------- phone pages: backup, restore, favorites folders ----------
    enum class PhonePage { BACKUP, RESTORE, FAVORITES }

    var phoneUrl by mutableStateOf<String?>(null)
        private set
    private var phoneServer: PhoneServer? = null
    private var restoreFails = 0
    /** A backup without a login, waiting for the login on a fresh install. */
    private var pendingRestore: Backup.Contents? = null

    private fun deviceName(): String {
        val app = getApplication<Application>()
        return runCatching { Settings.Global.getString(app.contentResolver, "device_name") }.getOrNull()
            ?: Build.MODEL ?: "Your TV"
    }

    private fun appVersionName(): String {
        val app = getApplication<Application>()
        return runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
    }

    /** Opens a phone page (shown on the TV as a QR code). It stops when the TV screen closes or after 10 minutes. */
    fun startPhonePage(page: PhonePage) {
        stopPhonePage()
        val ip = Net.localIp(getApplication())
        val device = deviceName()
        restoreFails = 0
        val s = PhoneServer.start(10 * 60_000L) { session -> phoneRequest(page, device, session) }
        phoneServer = s
        phoneUrl = if (s != null && ip != null) "http://$ip:${s.listeningPort}/?k=${s.token}" else null
    }

    fun stopPhonePage() {
        phoneServer?.stop()
        phoneServer = null
        phoneUrl = null
    }

    private fun PhoneServer.phoneRequest(page: PhonePage, device: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val post = session.method == NanoHTTPD.Method.POST
        return when (page) {
            PhonePage.BACKUP ->
                if (post && session.uri.startsWith("/make")) makeBackup(this, device, form(session))
                else html(PhonePages.backup(device, token, null, prefs.playlist != null))
            PhonePage.RESTORE ->
                if (post && session.uri.startsWith("/restore")) json(restoreRequest(body(session), existing = true) { lock() })
                else html(PhonePages.restore(device, token, existing = true))
            PhonePage.FAVORITES ->
                if (post && session.uri.startsWith("/favs")) json(phoneFavorites(body(session)))
                else html(PhonePages.favorites(device, token, runBlocking(Dispatchers.Main) { favoritesForPhone() }))
        }
    }

    /** Name used to find a favorite again if the provider renumbers it. */
    private fun matchName(key: String): String? =
        channelForKey(key)?.name ?: movieForKey(key)?.name ?: seriesForKey(key)?.name

    private fun makeBackup(server: PhoneServer, device: String, f: Map<String, String>): NanoHTTPD.Response {
        val opts = Backup.Options(
            login = f["login"] != null && prefs.playlist != null,
            favorites = f["favs"] != null, playback = f["playback"] != null, history = f["history"] != null,
        )
        val pass = f["pass"].orEmpty()
        val error = when {
            !opts.login && !opts.favorites && !opts.playback && !opts.history -> "Tick at least one thing to back up."
            opts.login && pass.length < 6 -> "Use at least 6 characters for the passphrase."
            opts.login && pass != f["pass2"] -> "The passphrases don't match."
            else -> null
        }
        if (error != null) return server.html(PhonePages.backup(device, server.token, error, prefs.playlist != null))
        val names = runBlocking(Dispatchers.Main) { favorites.associateWith { matchName(it) } }
        val text = Backup.create(prefs, opts, pass.takeIf { opts.login }, device, appVersionName()) { names[it] }
        val now = System.currentTimeMillis()
        viewModelScope.launch { toast = "Backup saved to your phone" }
        AppLog.i("Backup made: login ${if (opts.login) "yes (encrypted)" else "no"}, ${if (opts.favorites) "${favorites.size} favorites" else "no favorites"}, " +
            "playback ${if (opts.playback) "yes" else "no"}, history ${if (opts.history) "yes" else "no"}")
        return server.download(Backup.fileName(now), text)
    }

    private fun sameLogin(a: Playlist, b: Playlist) =
        a.type == b.type && a.url.trim().trimEnd('/') == b.url.trim().trimEnd('/') && a.user == b.user && a.pass == b.pass

    private fun jsonError(msg: String, extra: (JSONObject) -> Unit = {}) =
        JSONObject().put("ok", false).put("error", msg).also(extra).toString()

    /**
     * Handles the restore upload (from the restore page or the first-run login page). Returns the
     * JSON answer for the phone. [lock] stops the page after three wrong passphrases.
     */
    fun restoreRequest(body: String, existing: Boolean, setupToken: String? = null, lock: () -> Unit): String {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return jsonError("Couldn't read the upload. Try again.")
        val c = try {
            Backup.read(o.optString("file"), o.optString("pass").ifEmpty { null })
        } catch (e: Backup.WrongPassphrase) {
            if (o.optString("pass").isEmpty()) return jsonError(e.message ?: "Enter the passphrase.")
            restoreFails++
            AppLog.w("Restore: wrong passphrase ($restoreFails of 3)")
            if (restoreFails >= 3) {
                lock()
                return jsonError("Wrong passphrase three times. Open the screen on your TV again and scan the new QR code.") { it.put("locked", true) }
            }
            val left = 3 - restoreFails
            return jsonError("Wrong passphrase · $left ${if (left == 1) "try" else "tries"} left")
        } catch (e: Backup.BackupException) {
            AppLog.w("Restore refused: ${e.message}")
            return jsonError(e.message ?: "This backup file is damaged.")
        }
        val mode = if (o.optString("mode") == "replace" || prefs.playlist == null) Backup.Mode.REPLACE else Backup.Mode.ADD
        val login = c.login()
        val current = prefs.playlist
        // A backup without a login on a TV without one: keep it until the login is entered.
        if (login == null && current == null) {
            pendingRestore = c
            AppLog.i("Restore: backup without a login, waiting for the login")
            return JSONObject().put("ok", true).put("next", "/?k=${setupToken ?: ""}&restored=1").toString()
        }
        val changes = login != null && (current == null || !sameLogin(login, current))
        if (existing && changes && current != null && !o.optBoolean("confirm")) {
            val server = com.sparky.iptv.data.XtreamClient.normalizeBase(login!!.url)
            return JSONObject().put("ok", false)
                .put("confirm", "This backup uses a different login ($server). Restoring it replaces your current login.").toString()
        }
        var acct: com.sparky.iptv.data.AccountInfo? = null
        if (changes) {
            val r = runBlocking(Dispatchers.IO) { runCatching { repo.validate(login!!) } }
            acct = r.getOrElse {
                AppLog.e("Restore: saved login failed", it)
                return jsonError("The login in this backup didn't work: ${it.message ?: "couldn't log in"}")
            }
        }
        return try {
            val msg = runBlocking(Dispatchers.Main) { applyRestore(c, mode, if (changes) login else null, acct) }
            JSONObject().put("ok", true).put("message", msg).toString()
        } catch (e: Exception) {
            AppLog.e("Restore failed", e)
            jsonError(e.message ?: "Couldn't restore.")
        }
    }

    /** Applies a checked backup (main thread). [login] is set only when it replaces the current one. */
    private fun applyRestore(c: Backup.Contents, mode: Backup.Mode, login: Playlist?, acct: com.sparky.iptv.data.AccountInfo?): String {
        val names = Backup.apply(prefs, c, mode)
        if (c.data.has("favorites")) prefs.restorePending = JSONObject().put("names", names).put("total", c.favoriteCount).toString()
        if (login != null && acct != null) {
            prefs.playlist = login
            prefs.account = acct
            if (c.data.optJSONObject("playback")?.has("live_format") != true) prefs.liveFormat = acct.liveFormat
        }
        reloadFromPrefs()
        val created = runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(c.created.take(10))
        }.getOrNull()
        val date = created?.let { java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(it) } ?: "your backup"
        val text = "Restored · ${favorites.size} favorites · settings from $date"
        AppLog.i("Restore: ${mode.name.lowercase()}, ${favorites.size} favorites, login ${if (login != null) "replaced" else "kept"}")
        toast = text
        if (login != null) {
            repo.clearCache()
            epg.clear(); epgFetchedAt.clear()
            setLib(Library.EMPTY)
            // Let the phone get its answer before the TV screen (and its page) closes.
            viewModelScope.launch {
                delay(1500)
                stopPhonePage()
                stopSetup()
                resetTo(Screen.Loading)
                load()
            }
            return "$text. Your TV is loading your channels now."
        }
        if (!vodLoading && !library.isEmpty) reconcileRestore()
        return "$text."
    }

    /** Re-reads everything a restore can change. */
    private fun reloadFromPrefs() {
        favorites = prefs.favorites()
        favOrderSaved = prefs.favOrder() != null
        favOrder = Favs.normalize(prefs.favOrder() ?: emptyList(), favorites)
        folders = prefs.favFolders()
        myRegions = prefs.myRegions()
        regionsClosed = prefs.regionsClosed()
        recents = prefs.recents()
        com.sparky.iptv.player.SubtitleStyleState.style = com.sparky.iptv.player.SubStyle.decode(prefs.subStyle)
        tileModes.clear()
        progressVersion++
        if (liveGroup.startsWith(GROUP_FOLDER) && folder(liveGroup.removePrefix(GROUP_FOLDER)) == null) liveGroup = GROUP_FAV
    }

    /** After a restore: finds favorites the provider renumbered by their exact name, and reports what's gone. */
    private fun reconcileRestore() {
        val raw = prefs.restorePending ?: return
        prefs.restorePending = null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val names = o.optJSONObject("names") ?: JSONObject()
        lookup()
        val missing = favorites.filter { !present(it) }
        if (missing.isEmpty()) return
        val lib = library
        val chByName = HashMap<String, Channel>().apply { lib.channels.forEach { if (!it.divider) putIfAbsent(it.name, it) } }
        val mvByName = HashMap<String, Movie>().apply { lib.movies.forEach { putIfAbsent(it.name, it) } }
        val srByName = HashMap<String, Series>().apply { lib.series.forEach { putIfAbsent(it.name, it) } }
        val remap = HashMap<String, String>()
        val notFound = ArrayList<String>()
        for (k in missing) {
            val n = names.optString(k).ifBlank { null }
            val target = n?.let {
                when {
                    k.startsWith("live:") -> chByName[it]?.favKey
                    k.startsWith("movie:") -> mvByName[it]?.favKey
                    k.startsWith("series:") -> srByName[it]?.favKey
                    else -> null
                }
            }
            if (target != null && target !in favorites && target !in remap.values) remap[k] = target else notFound.add(n ?: k)
        }
        if (remap.isNotEmpty()) {
            saveFavs(
                favorites.map { remap[it] ?: it }.toSet(),
                favOrder.map { remap[it] ?: it },
                folders.map { f -> f.copy(keys = f.keys.map { remap[it] ?: it }) },
            )
        }
        val total = o.optInt("total", favorites.size)
        AppLog.i("Restore: ${remap.size} favorites found by name, ${notFound.size} not found")
        if (notFound.isNotEmpty()) {
            toast = "${total - notFound.size} of $total favorites restored · ${notFound.size} not found: " +
                notFound.take(3).joinToString(", ") + if (notFound.size > 3) "…" else ""
        }
    }

    /** Data for the phone's favorites page: your favorite channels in order, and the folders. */
    private fun favoritesForPhone(): String {
        val chans = JSONArray()
        favChannels().forEach { ch -> chans.put(JSONObject().put("key", ch.favKey).put("name", ch.display).put("num", ch.num)) }
        return JSONObject().put("channels", chans).put("folders", Favs.foldersToJson(folders)).toString()
    }

    /** Saves what the phone's favorites page sent. */
    private fun phoneFavorites(body: String): String {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return jsonError("Couldn't read the changes. Try again.")
        val orderArr = o.optJSONArray("order") ?: JSONArray()
        val order = List(orderArr.length()) { orderArr.optString(it) }
        val arr = o.optJSONArray("folders") ?: JSONArray()
        return runBlocking(Dispatchers.Main) {
            val known = folders.map { it.id }.toHashSet()
            val out = ArrayList<FavFolder>()
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val name = f.optString("name").trim()
                Favs.nameError(name, out)?.let { return@runBlocking jsonError(if (name.isEmpty()) "Every folder needs a name." else it) }
                val keysArr = f.optJSONArray("keys") ?: JSONArray()
                val keys = List(keysArr.length()) { keysArr.optString(it) }.filter { it.startsWith("live:") && it in favorites }.distinct()
                val id = f.optString("id").takeIf { it in known && out.none { o2 -> o2.id == it } } ?: Favs.newId(out + folders)
                out.add(FavFolder(id, name, keys))
            }
            applyPhoneFavorites(order, out)
            JSONObject().put("ok", true).put("folders", Favs.foldersToJson(folders)).toString()
        }
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

    /** Region headings closed in the group lists. */
    var regionsClosed by mutableStateOf(prefs.regionsClosed())
        private set

    fun toggleRegionOpen(code: String) {
        val next = regionsClosed.toMutableSet()
        if (!next.add(code)) next.remove(code)
        regionsClosed = next
        prefs.saveRegionsClosed(next)
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

    /**
     * Group list for Live TV and the guide: Favorites and its folders, Recent, All, then each visible
     * region's heading and, when the heading is open, its groups.
     */
    fun groupItems(): List<GroupItem> {
        val idx = index
        lookup()
        val favCount = favorites.count { chByKey.containsKey(it) }
        val recentCount = recents.count { it.type == "live" }
        val out = ArrayList<GroupItem>()
        out.add(GroupItem(GROUP_FAV, "Favorites", favCount))
        folders.forEach { f ->
            out.add(GroupItem(GROUP_FOLDER + f.id, f.name, f.keys.count { it in favorites && chByKey.containsKey(it) }, indent = true))
        }
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
            val open = code !in regionsClosed
            out.add(GroupItem("hdr:$code", Names.regionLabel(code), cats.sumOf { idx.countByCat[it.id] ?: 0 }, GroupKind.HEADER, region = code, open = open))
            if (open) cats.forEach { c -> out.add(GroupItem(c.id, idx.catName[c.id] ?: c.name, idx.countByCat[c.id] ?: 0, region = code)) }
        }
        if (myRegions.isNotEmpty()) {
            val label = if (showAllRegions) "Show only my regions" else "Show all regions"
            out.add(GroupItem(GROUP_ACTION_ALL_REGIONS, label, 0, GroupKind.ACTION))
        }
        return out
    }

    /** Region of a channel group (null for Favorites, folders, Recent and All). */
    fun regionOfGroup(id: String): String? = index.catRegion[id]

    // ---------- lists ----------
    fun channelsFor(group: String): List<Channel> {
        val all = library.channels
        return when (group) {
            GROUP_ALL -> if (myRegions.isEmpty() || showAllRegions) all
                else all.filter { regionVisible(index.regionOfChannel(it)) }
            GROUP_FAV -> favChannels()
            GROUP_RECENT -> recents.filter { it.type == "live" }.mapNotNull { r -> all.firstOrNull { it.id == r.id } }
            else -> if (group.startsWith(GROUP_FOLDER)) folderChannels(group.removePrefix(GROUP_FOLDER))
                else all.filter { it.categoryId == group }
        }
    }

    /** The reorderable list behind a group, if any (Favorites or a folder). */
    fun moveListFor(group: String): String? = when {
        group == GROUP_FAV -> LIST_LIVE
        group.startsWith(GROUP_FOLDER) -> folderList(group.removePrefix(GROUP_FOLDER))
        else -> null
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
            CAT_FAV -> favMovies()
            CAT_ALL -> mine
            else -> all.filter { it.categoryId == cat }
        }
    }

    fun seriesFor(cat: String): List<Series> {
        val all = library.series
        val mine = if (myRegions.isEmpty()) all else all.filter { seriesVisible(it) }
        return when (cat) {
            CAT_RECENT -> mine.sortedByDescending { it.lastModified }.take(300)
            CAT_FAV -> favSeries()
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
        // Version 2.2 records catch-up info; fetch once so Diagnostics can report it.
        val needCatchup = !prefs.catchupScanned && prefs.playlist?.type == "xtream"
        if (age > 24 * 60 * 60_000L || needCatchup) {
            AppLog.i(if (prefs.lastRefresh <= 0L) "Daily refresh: first update" else "Daily refresh: last update ${age / 3_600_000}h ago")
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
                // Keep past shows already loaded for catch-up.
                val firstNew = list.firstOrNull()?.start ?: Long.MAX_VALUE
                val past = epg[ch.id].orEmpty().filter { it.start < firstNew }
                epg[ch.id] = if (list.isEmpty()) epg[ch.id].orEmpty() else past + list
                epgFetchedAt[ch.id] = System.currentTimeMillis()
            } finally {
                epgInFlight.remove(ch.id)
            }
        }
    }

    private val fullEpgAt = HashMap<String, Long>()
    private val fullEpgInFlight = HashSet<String>()

    /** Loads past shows too (catch-up channels only), for scrolling back in the guide. */
    fun ensureFullEpg(ch: Channel) {
        if (prefs.playlist?.type != "xtream" || ch.archiveDays <= 0) return
        val now = System.currentTimeMillis()
        if (now - (fullEpgAt[ch.id] ?: 0L) < 30 * 60_000L || !fullEpgInFlight.add(ch.id)) return
        viewModelScope.launch {
            try {
                val list = epgLimit.withPermit {
                    runCatching { withContext(Dispatchers.IO) { repo.fullEpg(ch) } }
                        .onFailure { if (!AppLog.isCancellation(it)) AppLog.w("Full guide for ${ch.num} failed: ${it.message}") }
                        .getOrDefault(emptyList())
                }
                if (list.isNotEmpty()) {
                    epg[ch.id] = list.distinctBy { it.start }.sortedBy { it.start }
                    epgFetchedAt[ch.id] = System.currentTimeMillis()
                }
                fullEpgAt[ch.id] = System.currentTimeMillis()
            } finally {
                fullEpgInFlight.remove(ch.id)
            }
        }
    }

    // ---------- catch-up ----------
    /** True when [e] can be replayed: a past show on a catch-up channel, within its catch-up days. */
    fun canReplay(ch: Channel, e: EpgEntry, now: Long = System.currentTimeMillis()): Boolean =
        prefs.playlist?.type == "xtream" && ch.archiveDays > 0 && e.end <= now &&
            e.start >= now - ch.archiveDays * 24 * 3_600_000L

    /** Plays a past show from its start, in the movie player. */
    fun playCatchup(ch: Channel, e: EpgEntry) {
        val minutes = ((e.end - e.start) / 60_000L + 10).toInt().coerceAtLeast(10)
        val url = com.sparky.iptv.data.StreamUrls.catchup(ch.id, e.start, minutes)
        AppLog.i("Catch-up: channel ${ch.num}, show at ${e.start}")
        val sub = "${e.title} · aired ${com.sparky.iptv.ui.components.fmtDay(e.start)}, ${com.sparky.iptv.ui.components.fmtClock(e.start)}"
        push(Screen.Player(PlayRequest.Vod(listOf(VodItem("catchup:${ch.id}:${e.start}", ch.display, sub, url, saveProgress = false)), 0, 0L)))
    }

    var catchupTest by mutableStateOf<String?>(null)
        private set

    fun testCatchup() {
        if (catchupTest == "Testing…") return
        catchupTest = "Testing…"
        viewModelScope.launch {
            val archive = library.channels.filter { it.archiveDays > 0 && !it.divider }
            val ordered = archive.filter { it.favKey in favorites } + archive.filterNot { it.favKey in favorites }
            catchupTest = runCatching { repo.testCatchup(ordered) }.getOrElse { "Catch-up test failed: ${it.message}" }
        }
    }

    // ---------- channel switch timing ----------
    val switchTimes = mutableStateListOf<Long>()

    fun recordSwitch(ms: Long) {
        switchTimes.add(ms)
        while (switchTimes.size > 10) switchTimes.removeAt(0)
        AppLog.i("Channel switch: %.1f s".format(ms / 1000.0))
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

    // ---------- sleep timer ----------
    // Lives here (not in the player), so it keeps running across channel changes and between live
    // TV and movies. It's in memory only: closing the app clears it.

    /** Length of the running timer in minutes; 0 = off. */
    var sleepMinutes by mutableIntStateOf(0)
        private set
    /** When the running timer ends ([android.os.SystemClock.elapsedRealtime]). */
    var sleepEndsAt by mutableLongStateOf(0L)
        private set
    private var sleepJob: kotlinx.coroutines.Job? = null
    /** Key whose release is swallowed after it extended the timer. */
    private var swallowKeyUp = -1

    fun sleepLeftMs(): Long =
        if (sleepMinutes == 0) 0L else (sleepEndsAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    /** Starts the timer for [minutes], or turns it off for 0. */
    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            if (sleepMinutes != 0) AppLog.i("Sleep timer: off")
            sleepMinutes = 0
            sleepEndsAt = 0L
            toast = "Sleep timer off"
            return
        }
        sleepMinutes = minutes
        sleepEndsAt = android.os.SystemClock.elapsedRealtime() + minutes * 60_000L
        toast = "Sleep timer: $minutes min"
        AppLog.i("Sleep timer: $minutes min")
        sleepJob = viewModelScope.launch {
            var warned = false
            while (true) {
                val left = sleepLeftMs()
                if (left <= 0L) break
                if (!warned && left <= 60_000L) {
                    warned = true
                    toast = SLEEP_WARNING
                    AppLog.i("Sleep timer: 1 minute left")
                }
                delay(if (left > 61_000L) left - 60_000L else minOf(left, 1_000L))
            }
            sleepFinished()
        }
    }

    private fun sleepFinished() {
        val m = sleepMinutes
        sleepJob = null
        sleepMinutes = 0
        sleepEndsAt = 0L
        // Release the player so the stream connection closes, and keep previews from restarting
        // until someone presses a key.
        liveHandoff = false
        PlayerHolder.release()
        PlayerHolder.held = true
        if (current is Screen.Player) pop()
        toast = "Sleep timer stopped playback"
        AppLog.i("Sleep timer: stopped playback after $m min")
    }

    /**
     * Sees every remote key before the screens do. Any key lets playback start again after the
     * sleep timer stopped it. In the timer's last minute, a key restarts the timer at the same
     * length and does nothing else (its release is swallowed too). Returns true to swallow the key.
     */
    fun onAnyKey(e: android.view.KeyEvent): Boolean {
        if (e.action == android.view.KeyEvent.ACTION_UP && e.keyCode == swallowKeyUp) {
            swallowKeyUp = -1
            return true
        }
        val code = e.keyCode
        val isOk = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER ||
            code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
        // Hold OK on a favorite: the menu opens once OK is released, after the item has seen the release.
        if (e.action == android.view.KeyEvent.ACTION_UP && isOk && favMenuPending != null) {
            val r = favMenuPending
            favMenuPending = null
            viewModelScope.launch { favMenu = r }
            return false
        }
        // Moving a favorite: the arrows move it, OK drops it, Back puts it back. Nothing else reaches the screen.
        moving?.let { m ->
            val down = e.action == android.view.KeyEvent.ACTION_DOWN
            when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> if (down) moveStep(-m.upDown)
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (down) moveStep(m.upDown)
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> if (down) moveStep(-m.leftRight)
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) moveStep(m.leftRight)
                android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_ESCAPE ->
                    if (!down) cancelMove()
                else -> if (isOk && !down) commitMove()
            }
            return true
        }
        if (e.action != android.view.KeyEvent.ACTION_DOWN) return false
        PlayerHolder.held = false
        if (sleepMinutes > 0 && sleepLeftMs() <= 60_000L) {
            if (e.repeatCount == 0) {
                AppLog.i("Sleep timer: extended by a key press")
                setSleep(sleepMinutes)
            }
            swallowKeyUp = e.keyCode
            return true
        }
        return false
    }

    // ---------- subtitle style ----------
    fun setSubStyle(s: com.sparky.iptv.player.SubStyle) {
        com.sparky.iptv.player.SubtitleStyleState.style = s
        prefs.subStyle = s.encode()
    }

    // Runs last, so every property above is ready before loading starts.
    init {
        com.sparky.iptv.player.SubtitleStyleState.style = com.sparky.iptv.player.SubStyle.decode(prefs.subStyle)
        if (prefs.playlist == null) {
            stack.add(Screen.Setup)
        } else {
            stack.add(Screen.Loading)
            load()
        }
        if (favorites.any { it.startsWith("live:") }) liveGroup = GROUP_FAV
    }
}
