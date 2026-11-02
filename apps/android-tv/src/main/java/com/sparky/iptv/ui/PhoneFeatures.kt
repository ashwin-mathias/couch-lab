package com.sparky.iptv.ui

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.Backup
import com.sparky.iptv.data.Channel
import com.sparky.iptv.data.FavFolder
import com.sparky.iptv.data.Favs
import com.sparky.iptv.data.Library
import com.sparky.iptv.data.Movie
import com.sparky.iptv.data.Playlist
import com.sparky.iptv.data.Series
import com.sparky.iptv.setup.Net
import com.sparky.iptv.setup.PhonePages
import com.sparky.iptv.setup.PhoneServer
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phone pages for backup, restore and favorites folders, and the restore logic shared with the
 * setup page (moved out of AppViewModel in 2.8). State changes go through the ViewModel.
 */
class PhoneFeatures(private val vm: AppViewModel) {

    var phoneUrl by mutableStateOf<String?>(null)
        private set
    private var phoneServer: PhoneServer? = null
    var restoreFails = 0
    /** A backup without a login, waiting for the login on a fresh install. */
    var pendingRestore: Backup.Contents? = null

    private fun deviceName(): String {
        val app = vm.getApplication<Application>()
        return runCatching { Settings.Global.getString(app.contentResolver, "device_name") }.getOrNull()
            ?: Build.MODEL ?: "Your TV"
    }

    private fun appVersionName(): String {
        val app = vm.getApplication<Application>()
        return runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
    }

    /** Opens a phone page (shown on the TV as a QR code). It stops when the TV screen closes or after 10 minutes. */
    fun startPhonePage(page: AppViewModel.PhonePage) {
        stopPhonePage()
        val ip = Net.localIp(vm.getApplication())
        val device = deviceName()
        restoreFails = 0
        val s = PhoneServer.start(PhoneServer.PAGE_PORTS, 10 * 60_000L) { session -> phoneRequest(page, device, session) }
        phoneServer = s
        phoneUrl = if (s != null && ip != null) "http://$ip:${s.listeningPort}/?k=${s.token}" else null
    }

    fun stopPhonePage() {
        phoneServer?.stop()
        phoneServer = null
        phoneUrl = null
    }

    private fun PhoneServer.phoneRequest(page: AppViewModel.PhonePage, device: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val post = session.method == NanoHTTPD.Method.POST
        return when (page) {
            AppViewModel.PhonePage.BACKUP ->
                if (post && session.uri.startsWith("/make")) makeBackup(this, device, form(session))
                else html(PhonePages.backup(device, token, null, vm.prefs.playlist != null))
            AppViewModel.PhonePage.RESTORE ->
                if (post && session.uri.startsWith("/restore")) json(restoreRequest(body(session), existing = true) { lock() })
                else html(PhonePages.restore(device, token, existing = true))
            AppViewModel.PhonePage.FAVORITES ->
                if (post && session.uri.startsWith("/favs")) json(phoneFavorites(body(session)))
                else html(PhonePages.favorites(device, token, runBlocking(Dispatchers.Main) { favoritesForPhone() }))
        }
    }

    /** Name used to find a favorite again if the provider renumbers it. */
    private fun matchName(key: String): String? =
        vm.channelForKey(key)?.name ?: vm.movieForKey(key)?.name ?: vm.seriesForKey(key)?.name

    private fun makeBackup(server: PhoneServer, device: String, f: Map<String, String>): NanoHTTPD.Response {
        val opts = Backup.Options(
            login = f["login"] != null && vm.prefs.playlist != null,
            favorites = f["favs"] != null, playback = f["playback"] != null, history = f["history"] != null,
        )
        val pass = f["pass"].orEmpty()
        val error = when {
            !opts.login && !opts.favorites && !opts.playback && !opts.history -> "Tick at least one thing to back up."
            opts.login && pass.length < Backup.MIN_PASSPHRASE -> "Use at least ${Backup.MIN_PASSPHRASE} characters for the passphrase. A short sentence works well."
            opts.login && pass != f["pass2"] -> "The passphrases don't match."
            else -> null
        }
        if (error != null) return server.html(PhonePages.backup(device, server.token, error, vm.prefs.playlist != null))
        val names = runBlocking(Dispatchers.Main) { vm.favorites.associateWith { matchName(it) } }
        val text = Backup.create(vm.prefs, opts, pass.takeIf { opts.login }, device, appVersionName()) { names[it] }
        val now = System.currentTimeMillis()
        vm.viewModelScope.launch { vm.toast = "Backup saved to your phone" }
        AppLog.i("Backup made: login ${if (opts.login) "yes (encrypted)" else "no"}, ${if (opts.favorites) "${vm.favorites.size} favorites" else "no favorites"}, " +
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
        val mode = if (o.optString("mode") == "replace" || vm.prefs.playlist == null) Backup.Mode.REPLACE else Backup.Mode.ADD
        val login = c.login()
        val current = vm.prefs.playlist
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
            val r = runBlocking(Dispatchers.IO) { runCatching { vm.repo.validate(login!!) } }
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
    fun applyRestore(c: Backup.Contents, mode: Backup.Mode, login: Playlist?, acct: com.sparky.iptv.data.AccountInfo?): String {
        val names = Backup.apply(vm.prefs, c, mode)
        if (c.data.has("favorites")) vm.prefs.restorePending = JSONObject().put("names", names).put("total", c.favoriteCount).toString()
        if (login != null && acct != null) {
            vm.prefs.playlist = login
            vm.prefs.account = acct
            if (c.data.optJSONObject("playback")?.has("live_format") != true) vm.prefs.liveFormat = acct.liveFormat
        }
        vm.reloadFromPrefs()
        val created = runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(c.created.take(10))
        }.getOrNull()
        val date = created?.let { java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(it) } ?: "your backup"
        val text = "Restored · ${vm.favorites.size} favorites · settings from $date"
        AppLog.i("Restore: ${mode.name.lowercase()}, ${vm.favorites.size} favorites, login ${if (login != null) "replaced" else "kept"}")
        vm.toast = text
        if (login != null) {
            vm.repo.clearCache()
            vm.clearEpg()
            vm.setLib(Library.EMPTY)
            // Let the phone get its answer before the TV screen (and its page) closes.
            vm.viewModelScope.launch {
                delay(1500)
                stopPhonePage()
                vm.stopSetup()
                vm.resetTo(Screen.Loading)
                vm.load()
            }
            return "$text. Your TV is loading your channels now."
        }
        if (!vm.vodLoading && !vm.library.isEmpty) reconcileRestore()
        return "$text."
    }

    /** After a restore: finds favorites the provider renumbered by their exact name, and reports what's gone. */
    fun reconcileRestore() {
        val raw = vm.prefs.restorePending ?: return
        vm.prefs.restorePending = null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val names = o.optJSONObject("names") ?: JSONObject()
        vm.lookup()
        val missing = vm.favorites.filter { !vm.present(it) }
        if (missing.isEmpty()) return
        val lib = vm.library
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
            if (target != null && target !in vm.favorites && target !in remap.values) remap[k] = target else notFound.add(n ?: k)
        }
        if (remap.isNotEmpty()) {
            vm.saveFavs(
                vm.favorites.map { remap[it] ?: it }.toSet(),
                vm.favOrder.map { remap[it] ?: it },
                vm.folders.map { f -> f.copy(keys = f.keys.map { remap[it] ?: it }) },
            )
        }
        val total = o.optInt("total", vm.favorites.size)
        AppLog.i("Restore: ${remap.size} favorites found by name, ${notFound.size} not found")
        if (notFound.isNotEmpty()) {
            vm.toast = "${total - notFound.size} of $total favorites restored · ${notFound.size} not found: " +
                notFound.take(3).joinToString(", ") + if (notFound.size > 3) "…" else ""
        }
    }

    /** Data for the phone's favorites page: your favorite channels in order, and the folders. */
    private fun favoritesForPhone(): String {
        val chans = JSONArray()
        vm.favChannels().forEach { ch -> chans.put(JSONObject().put("key", ch.favKey).put("name", ch.display).put("num", ch.num)) }
        return JSONObject().put("channels", chans).put("folders", Favs.foldersToJson(vm.folders)).toString()
    }

    /** Saves what the phone's favorites page sent. */
    private fun phoneFavorites(body: String): String {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return jsonError("Couldn't read the changes. Try again.")
        val orderArr = o.optJSONArray("order") ?: JSONArray()
        val order = List(orderArr.length()) { orderArr.optString(it) }
        val arr = o.optJSONArray("folders") ?: JSONArray()
        return runBlocking(Dispatchers.Main) {
            val known = vm.folders.map { it.id }.toHashSet()
            val out = ArrayList<FavFolder>()
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val name = f.optString("name").trim()
                Favs.nameError(name, out)?.let { return@runBlocking jsonError(if (name.isEmpty()) "Every folder needs a name." else it) }
                val keysArr = f.optJSONArray("keys") ?: JSONArray()
                val keys = List(keysArr.length()) { keysArr.optString(it) }.filter { it.startsWith("live:") && it in vm.favorites }.distinct()
                val id = f.optString("id").takeIf { it in known && out.none { o2 -> o2.id == it } } ?: Favs.newId(out + vm.folders)
                out.add(FavFolder(id, name, keys))
            }
            vm.applyPhoneFavorites(order, out)
            JSONObject().put("ok", true).put("folders", Favs.foldersToJson(vm.folders)).toString()
        }
    }
}
