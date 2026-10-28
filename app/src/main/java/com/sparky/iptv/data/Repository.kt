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

    private val listFiles = listOf(
        "get_live_categories", "get_live_streams",
        "get_vod_categories", "get_vod_streams",
        "get_series_categories", "get_series",
    )

    private fun cacheFile(name: String) = File(ctx.filesDir, "cache_$name.json")
    private fun tempFile(name: String) = File(ctx.cacheDir, "dl_$name.json")

    fun clearCache() {
        ctx.filesDir.listFiles()?.filter { it.name.startsWith("cache_") }?.forEach { it.delete() }
    }

    private fun xtream(): XtreamClient? =
        prefs.playlist?.takeIf { it.type == "xtream" }?.let { XtreamClient(http, it) }

    fun stepsFor(p: Playlist?): List<LoadStep> =
        if (p?.type == "m3u") listOf(LoadStep("Connecting to your playlist"), LoadStep("Channels"))
        else listOf(LoadStep("Connecting to your provider"), LoadStep("Live TV"), LoadStep("Movies"), LoadStep("Series"))

    fun isOnline(): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    /** Fast start: build the library from the copy saved last time. */
    suspend fun loadCached(): Library? = withContext(Dispatchers.IO) {
        runCatching {
            val p = prefs.playlist ?: return@runCatching null
            if (p.type == "m3u") {
                val f = cacheFile("m3u")
                if (!f.exists()) return@runCatching null
                return@runCatching M3u.parse(f.readText())
            }
            if (listFiles.any { !cacheFile(it).exists() }) return@runCatching null
            buildXtream(p) { cacheFile(it) }
        }.onFailure { AppLog.e("Reading saved library failed", it) }.getOrNull()
    }

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
        for ((step, cats, items) in sections) {
            ensureActive()
            listener.update(step, StepStatus.ACTIVE, "Starting download…")
            try {
                download(x.apiUrl(cats), tempFile(cats)) {}
                download(x.apiUrl(items), tempFile(items)) { b ->
                    listener.update(step, StepStatus.ACTIVE, "Downloading · ${mb(b)}")
                }
                listener.update(step, StepStatus.ACTIVE, "Reading ${mb(tempFile(items).length())}…")
                val count = when (step) {
                    1 -> StreamParsers.live(tempFile(items), x, prefs.liveFormat).size
                    2 -> StreamParsers.movies(tempFile(items), x).size
                    else -> StreamParsers.series(tempFile(items)).size
                }
                tempFile(cats).copyTo(cacheFile(cats), overwrite = true)
                tempFile(items).copyTo(cacheFile(items), overwrite = true)
                val noun = when (step) { 1 -> "channels"; 2 -> "movies"; else -> "series" }
                listener.update(step, StepStatus.DONE, "${fmt(count)} $noun")
                AppLog.i("$items: ${fmt(count)} $noun (${mb(cacheFile(items).length())})")
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
        buildXtream(p) { cacheFile(it) }
    }

    private fun buildXtream(p: Playlist, file: (String) -> File): Library {
        val x = XtreamClient(http, p)
        val fmt = prefs.liveFormat
        return Library(
            liveCats = StreamParsers.categories(file("get_live_categories")),
            channels = StreamParsers.live(file("get_live_streams"), x, fmt),
            vodCats = StreamParsers.categories(file("get_vod_categories")),
            movies = StreamParsers.movies(file("get_vod_streams"), x),
            seriesCats = StreamParsers.categories(file("get_series_categories")),
            series = StreamParsers.series(file("get_series")),
        )
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
                            url = line,
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
