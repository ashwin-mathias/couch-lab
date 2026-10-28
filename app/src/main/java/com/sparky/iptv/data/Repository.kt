package com.sparky.iptv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class StepStatus { PENDING, ACTIVE, DONE, FAILED }

data class LoadStep(val label: String, val status: StepStatus = StepStatus.PENDING, val detail: String = "")

/** Receives progress while the library downloads. Called from a background thread. */
fun interface LoadListener {
    fun update(index: Int, status: StepStatus, detail: String)
}

class Repository(private val ctx: Context, val prefs: Prefs) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private fun cacheFile(name: String) = File(ctx.filesDir, "cache_$name.json")
    private fun tempFile(name: String) = File(ctx.cacheDir, "dl_$name.json")
    private val snapLive get() = File(ctx.filesDir, "snap_live.bin")
    private val snapMovies get() = File(ctx.filesDir, "snap_movies.bin")
    private val snapSeries get() = File(ctx.filesDir, "snap_series.bin")

    fun hasCache(): Boolean = snapLive.exists() || cacheFile("get_live_streams").exists() || cacheFile("m3u").exists()

    fun clearCache() {
        ctx.filesDir.listFiles()?.filter { it.name.startsWith("cache_") || it.name.startsWith("snap_") }?.forEach { it.delete() }
    }

    private fun xtream(): XtreamClient? =
        prefs.playlist?.takeIf { it.type == "xtream" }?.let { XtreamClient(http, it) }

    fun stepsFor(p: Playlist?): List<LoadStep> =
        if (p?.type == "m3u") listOf(LoadStep("Connecting to your playlist"), LoadStep("Channels"))
        else listOf(
            LoadStep("Connecting to your provider"), LoadStep("Live TV"), LoadStep("Movies"),
            LoadStep("Series"), LoadStep("Preparing your library"),
        )

    fun isOnline(): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    /**
     * Fast start, step 1: only the live channels (small), so Home can open right away.
     * Uses the compact saved copy; converts the old raw copy the first time.
     */
    suspend fun loadCachedLive(onProgress: (String) -> Unit = {}): Library? = withContext(Dispatchers.IO) {
        runCatching {
            val p = prefs.playlist ?: return@runCatching null
            if (p.type == "m3u") {
                val f = cacheFile("m3u")
                if (!f.exists()) return@runCatching null
                return@runCatching M3u.parse(f.readText())
            }
            StreamUrls.set(p, prefs.liveFormat)
            val t0 = System.currentTimeMillis()
            val live = Snapshot.readLive(snapLive) { i, n -> onProgress("Channels ${pct(i, n)}") }
                ?: migrate("get_live_categories", "get_live_streams") { cats, items ->
                    val l = StreamParsers.categories(cats) to StreamParsers.live(items, XtreamClient(http, p), prefs.liveFormat)
                    Snapshot.writeLive(snapLive, l.first, l.second)
                    l
                }
                ?: return@runCatching null
            AppLog.i("Opened saved channels: ${live.second.size} in ${System.currentTimeMillis() - t0} ms")
            Library(live.first, live.second, emptyList(), emptyList(), emptyList(), emptyList())
        }.onFailure { AppLog.e("Reading saved channels failed", it) }.getOrNull()
    }

    /** Fast start, step 2 (in the background): movies and series from the saved copy. */
    suspend fun loadCachedVod(base: Library, onProgress: (String) -> Unit = {}): Library = withContext(Dispatchers.IO) {
        val p = prefs.playlist ?: return@withContext base
        if (p.type == "m3u") return@withContext base
        val t0 = System.currentTimeMillis()
        val movies = runCatching {
            Snapshot.readMovies(snapMovies) { i, n -> onProgress("Movies ${pct(i, n)}") }
                ?: migrate("get_vod_categories", "get_vod_streams") { cats, items ->
                    val l = StreamParsers.categories(cats) to StreamParsers.movies(items, XtreamClient(http, p))
                    Snapshot.writeMovies(snapMovies, l.first, l.second)
                    l
                }
        }.onFailure { AppLog.e("Reading saved movies failed", it) }.getOrNull()
        val series = runCatching {
            Snapshot.readSeries(snapSeries) { i, n -> onProgress("Series ${pct(i, n)}") }
                ?: migrate("get_series_categories", "get_series") { cats, items ->
                    val l = StreamParsers.categories(cats) to StreamParsers.series(items)
                    Snapshot.writeSeries(snapSeries, l.first, l.second)
                    l
                }
        }.onFailure { AppLog.e("Reading saved series failed", it) }.getOrNull()
        AppLog.i("Opened saved movies/series: ${movies?.second?.size ?: 0} / ${series?.second?.size ?: 0} in ${System.currentTimeMillis() - t0} ms")
        Library(
            base.liveCats, base.channels,
            movies?.first ?: emptyList(), movies?.second ?: emptyList(),
            series?.first ?: emptyList(), series?.second ?: emptyList(),
        )
    }

    /** One-time move from the old raw saved lists to the compact format; frees the space afterwards. */
    private fun <T> migrate(cats: String, items: String, convert: (File, File) -> T): T? {
        val c = cacheFile(cats)
        val i = cacheFile(items)
        if (!c.exists() || !i.exists()) return null
        AppLog.i("Converting saved $items to the compact format")
        val r = convert(c, i)
        c.delete()
        i.delete()
        return r
    }

    private fun pct(i: Int, n: Int) = if (n > 0) "${(i * 100L / n)}%" else ""

    /** Downloads fresh lists one at a time, reporting progress, then saves them for next launch. */
    suspend fun refresh(listener: LoadListener = LoadListener { _, _, _ -> }): Library = withContext(Dispatchers.IO) {
        val p = prefs.playlist ?: throw IOException("No playlist set up")
        if (!isOnline()) throw IOException("This TV isn't connected to the internet. Check Wi-Fi and try again.")
        AppLog.setSecrets(p.pass, p.user)

        if (p.type == "m3u") {
            listener.update(0, StepStatus.ACTIVE, "")
            val tmp = tempFile("m3u")
            listener.update(0, StepStatus.DONE, "")
            listener.update(1, StepStatus.ACTIVE, "Starting download…")
            download(p.url, tmp) { b -> listener.update(1, StepStatus.ACTIVE, "Downloading · ${mb(b)}") }
            val lib = M3u.parse(tmp.readText())
            tmp.copyTo(cacheFile("m3u"), overwrite = true); tmp.delete()
            listener.update(1, StepStatus.DONE, "${fmt(lib.channels.size)} channels")
            AppLog.i("M3U loaded: ${lib.channels.size} channels")
            return@withContext lib
        }

        val x = XtreamClient(http, p)
        listener.update(0, StepStatus.ACTIVE, "Signing in…")
        AppLog.i("Connecting to ${XtreamClient.normalizeBase(p.url)}")
        try {
            val acct = x.authenticate()
            prefs.account = acct
            prefs.liveFormat = acct.liveFormat
            StreamUrls.set(p, acct.liveFormat)
            listener.update(0, StepStatus.DONE, acct.status ?: "Signed in")
            AppLog.i("Signed in · status ${acct.status} · format ${acct.liveFormat}")
        } catch (e: Exception) {
            listener.update(0, StepStatus.FAILED, e.message ?: "Couldn't sign in")
            AppLog.e("Sign-in failed", e)
            throw e
        }

        // Each section downloads into a temp file; a section that fails keeps last time's copy.
        val sections = listOf(
            Triple(1, "get_live_categories", "get_live_streams"),
            Triple(2, "get_vod_categories", "get_vod_streams"),
            Triple(3, "get_series_categories", "get_series"),
        )
        var anyOk = false
        // Keep what each step reads so the lists are only parsed once.
        val parsed = HashMap<String, List<Any>>()
        for ((step, cats, items) in sections) {
            ensureActive()
            listener.update(step, StepStatus.ACTIVE, "Starting download…")
            try {
                download(x.apiUrl(cats), tempFile(cats)) {}
                download(x.apiUrl(items), tempFile(items)) { b ->
                    listener.update(step, StepStatus.ACTIVE, "Downloading · ${mb(b)}")
                }
                listener.update(step, StepStatus.ACTIVE, "Reading ${mb(tempFile(items).length())}…")
                val catList = StreamParsers.categories(tempFile(cats))
                val list: List<Any> = when (step) {
                    1 -> StreamParsers.live(tempFile(items), x, prefs.liveFormat).also { Snapshot.writeLive(snapLive, catList, it) }
                    2 -> StreamParsers.movies(tempFile(items), x).also { Snapshot.writeMovies(snapMovies, catList, it) }
                    else -> StreamParsers.series(tempFile(items)).also { Snapshot.writeSeries(snapSeries, catList, it) }
                }
                parsed[items] = list
                parsed[cats] = catList
                val count = list.size
                val raw = tempFile(items).length()
                // The compact copy replaces the old raw copies.
                cacheFile(cats).delete()
                cacheFile(items).delete()
                val noun = when (step) { 1 -> "channels"; 2 -> "movies"; else -> "series" }
                listener.update(step, StepStatus.DONE, "${fmt(count)} $noun")
                AppLog.i("$items: ${fmt(count)} $noun (${mb(raw)})")
                anyOk = true
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val msg = if (e is OutOfMemoryError) "Too large for this device" else (e.message ?: "Failed")
                listener.update(step, StepStatus.FAILED, msg)
                AppLog.e("$items failed", e)
            } finally {
                tempFile(cats).delete()
                tempFile(items).delete()
            }
        }
        if (!anyOk) throw IOException("Couldn't download anything from your provider. Check the server URL and try again.")
        listener.update(4, StepStatus.ACTIVE, "Organizing…")
        // Sections that failed fall back to last time's saved copy.
        @Suppress("UNCHECKED_CAST")
        fun <T> got(name: String): List<T>? = parsed[name] as? List<T>
        val live = if (got<Channel>("get_live_streams") != null) null else runCatching { Snapshot.readLive(snapLive) }.getOrNull()
        val mov = if (got<Movie>("get_vod_streams") != null) null else runCatching { Snapshot.readMovies(snapMovies) }.getOrNull()
        val ser = if (got<Series>("get_series") != null) null else runCatching { Snapshot.readSeries(snapSeries) }.getOrNull()
        val lib = Library(
            liveCats = got<Category>("get_live_categories") ?: live?.first ?: emptyList(),
            channels = got<Channel>("get_live_streams") ?: live?.second ?: emptyList(),
            vodCats = got<Category>("get_vod_categories") ?: mov?.first ?: emptyList(),
            movies = got<Movie>("get_vod_streams") ?: mov?.second ?: emptyList(),
            seriesCats = got<Category>("get_series_categories") ?: ser?.first ?: emptyList(),
            series = got<Series>("get_series") ?: ser?.second ?: emptyList(),
        )
        parsed.clear()
        listener.update(4, StepStatus.DONE, "${fmt(lib.channels.size + lib.movies.size + lib.series.size)} items")
        lib
    }

    /** Streams a response straight to disk so large lists never sit in memory. */
    suspend fun download(url: String, dest: File, onBytes: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Server returned HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response from server")
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    var reported = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        if (total - reported >= 256 * 1024) {
                            reported = total
                            onBytes(total)
                            ensureActive()
                        }
                    }
                    onBytes(total)
                }
            }
        }
    }

    /** Checks a login from the phone setup page. Returns the account info on success. */
    suspend fun validate(p: Playlist): AccountInfo = withContext(Dispatchers.IO) {
        AppLog.setSecrets(p.pass, p.user)
        AppLog.i("Checking login for ${XtreamClient.normalizeBase(p.url)} (${p.type})")
        if (p.type == "m3u") {
            val req = Request.Builder().url(p.url).header("User-Agent", USER_AGENT).build()
            val head = http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Server returned HTTP ${r.code}")
                r.body?.source()?.let { src -> src.request(4096); src.buffer.snapshot().utf8() } ?: ""
            }
            if (!head.contains("#EXTM3U", true) && !head.contains("#EXTINF", true)) {
                throw IOException("That link didn't return an M3U playlist.")
            }
            AccountInfo(null, null, null, "ts")
        } else {
            XtreamClient(http, p).authenticate()
        }
    }

    suspend fun shortEpg(ch: Channel): List<EpgEntry> {
        val x = xtream() ?: return emptyList()
        return Parsers.shortEpg(x.raw("get_short_epg", "&stream_id=${ch.id}&limit=12"))
    }

    suspend fun movieInfo(m: Movie): MovieInfo? {
        val x = xtream() ?: return null
        return runCatching { Parsers.movieInfo(x.raw("get_vod_info", "&vod_id=${m.id}")) }
            .onFailure { AppLog.e("Movie details failed", it) }.getOrNull()
    }

    suspend fun seriesInfo(s: Series): SeriesInfo {
        val x = xtream() ?: throw IOException("Series need an Xtream login")
        val text = x.raw("get_series_info", "&series_id=${s.id}")
        return withContext(Dispatchers.Default) { Parsers.seriesInfo(text, s, x) }
    }

    companion object {
        fun mb(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / 1048576.0)
        fun fmt(n: Int): String = "%,d".format(n)
    }
}

object M3u {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): Library {
        val channels = ArrayList<Channel>()
        val cats = LinkedHashMap<String, Category>()
        var pendingAttrs: Map<String, String>? = null
        var pendingName: String? = null
        var n = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingAttrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    pendingName = line.substringAfterLast(",").trim()
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val attrs = pendingAttrs ?: emptyMap()
                    val group = attrs["group-title"]?.ifBlank { null } ?: "Uncategorized"
                    val catId = "m3u:$group"
                    cats.getOrPut(catId) { Category(catId, group) }
                    n++
                    val name = pendingName?.ifBlank { null } ?: attrs["tvg-name"] ?: "Channel $n"
                    channels.add(
                        Channel(
                            id = n.toString(),
                            num = attrs["tvg-chno"]?.toIntOrNull() ?: n,
                            name = name,
                            logo = attrs["tvg-logo"]?.ifBlank { null },
                            categoryId = catId,
                            epgId = attrs["tvg-id"],
                            directUrl = line,
                        )
                    )
                    pendingAttrs = null
                    pendingName = null
                }
            }
        }
        return Library(cats.values.toList(), channels, emptyList(), emptyList(), emptyList(), emptyList())
    }
}
