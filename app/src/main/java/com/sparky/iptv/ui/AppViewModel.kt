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

    internal fun setLib(lib: Library) {
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
    private var server: PhoneServer? = null
    private var setupRenew: kotlinx.coroutines.Job? = null


    // ---------- navigation ----------
    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }
    internal fun resetTo(s: Screen) { stack.clear(); stack.add(s) }

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
        phone.restoreFails = 0
        val s = SetupServer.startOnFreePort(
            name,
            existing = prefs.playlist != null,
            onRestore = { body, token, lock -> phone.restoreRequest(body, existing = prefs.playlist != null, setupToken = token, lock = lock) },
            restoredNote = {
                phone.pendingRestore?.let { "Backup ready: ${it.favoriteCount} favorites and your settings. Enter your login to finish." }
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
                        phone.pendingRestore?.let { c ->
                            phone.pendingRestore = null
                            runCatching { phone.applyRestore(c, Backup.Mode.REPLACE, null, null) }
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
        // The key stops working after 10 minutes without use; then the Setup screen gets a new QR code (2.8).
        if (s != null) setupRenew = viewModelScope.launch {
            while (true) {
                delay(20_000)
                if (server === s && s.expired && !setupBusy) {
                    AppLog.i("Setup QR code renewed")
                    startSetup()
                    break
                }
            }
        }
    }

    fun stopSetup() {
        setupRenew?.cancel()
        setupRenew = null
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

    internal fun saveFavs(keys: Set<String>, order: List<String>, fs: List<FavFolder>) {
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
    internal fun lookup() {
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
    internal fun present(key: String): Boolean = when {
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
        phone.reconcileRestore()
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

    // ---------- phone pages: backup, restore, favorites folders (see PhoneFeatures) ----------
    enum class PhonePage { BACKUP, RESTORE, FAVORITES }

    val phone = PhoneFeatures(this)
    val phoneUrl: String? get() = phone.phoneUrl
    fun startPhonePage(page: PhonePage) = phone.startPhonePage(page)
    fun stopPhonePage() = phone.stopPhonePage()

    internal fun clearEpg() { epg.clear(); epgFetchedAt.clear() }

    /** Re-reads everything a restore can change. */
    internal fun reloadFromPrefs() {
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
            GROUP_ALL -> if (myRegions.isEmpty() || showAllRegions) all else allMine()
            GROUP_FAV -> favChannels()
            GROUP_RECENT -> recents.filter { it.type == "live" }.mapNotNull { r -> channelForKey("live:" + r.id) }
            else -> if (group.startsWith(GROUP_FOLDER)) folderChannels(group.removePrefix(GROUP_FOLDER))
                else index.byCat[group].orEmpty()
        }
    }

    // "All my channels" filtered once per library and region choice, not on every visit (2.8).
    private var allMineFor: Triple<Library, Set<String>, Boolean>? = null
    private var allMine: List<Channel> = emptyList()
    private fun allMine(): List<Channel> {
        val k = Triple(library, myRegions, showAllRegions)
        if (allMineFor != k) {
            allMine = library.channels.filter { regionVisible(index.regionOfChannel(it)) }
            allMineFor = k
        }
        return allMine
    }

    // Lazy-list keys for the last few channel lists, so reopening "All channels" doesn't rebuild 57k keys (2.8).
    private val keyMemo = ArrayDeque<Pair<List<Channel>, List<String>>>()
    fun channelKeys(list: List<Channel>): List<String> {
        keyMemo.firstOrNull { it.first === list }?.let { return it.second }
        val keys = com.sparky.iptv.ui.components.stableKeys(list) { it.id }
        // Only big lists are worth keeping; small ones are cheap to rebuild.
        if (list.size > 2_000) {
            keyMemo.addFirst(list to keys)
            while (keyMemo.size > 2) keyMemo.removeLast()
        }
        return keys
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
        return recents.mapNotNull { r ->
            when (r.type) {
                "live" -> channelForKey("live:" + r.id)?.let { RecentEntry.Ch(it) }
                "movie" -> movieForKey("movie:" + r.id)?.let { RecentEntry.Mv(it) }
                "series" -> seriesForKey("series:" + r.id)?.let { RecentEntry.Sr(it) }
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
        val needCatchup = !prefs.catchupScanned && prefs.isXtream
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
        if (!prefs.isXtream) return
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
        if (!prefs.isXtream || ch.archiveDays <= 0) return
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
        prefs.isXtream && ch.archiveDays > 0 && e.end <= now &&
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
