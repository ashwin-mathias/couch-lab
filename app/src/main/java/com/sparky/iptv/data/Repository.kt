package com.sparky.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class Repository(private val ctx: Context, val prefs: Prefs) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val xtreamActions = listOf(
        "get_live_categories", "get_live_streams",
        "get_vod_categories", "get_vod_streams",
        "get_series_categories", "get_series",
    )

    private fun cacheFile(name: String) = File(ctx.filesDir, "cache_$name.json")

    fun clearCache() {
        ctx.filesDir.listFiles()?.filter { it.name.startsWith("cache_") }?.forEach { it.delete() }
    }

    private fun xtream(): XtreamClient? =
        prefs.playlist?.takeIf { it.type == "xtream" }?.let { XtreamClient(http, it) }

    /** Fast start: build the library from what was saved last time, if anything. */
    suspend fun loadCached(): Library? = withContext(Dispatchers.IO) {
        runCatching {
            val p = prefs.playlist ?: return@runCatching null
            val names = if (p.type == "m3u") listOf("m3u") else xtreamActions
            val texts = names.associateWith { n ->
                val f = cacheFile(n)
                if (!f.exists()) return@runCatching null
                f.readText()
            }
            build(texts)
        }.getOrNull()
    }

    /** Download fresh lists from the provider and save them for next launch. */
    suspend fun refresh(): Library = withContext(Dispatchers.IO) {
        val p = prefs.playlist ?: throw IOException("No playlist set up")
        val texts: Map<String, String> = if (p.type == "m3u") {
            mapOf("m3u" to getText(http, p.url))
        } else {
            val x = XtreamClient(http, p)
            // Keep the account details (and best stream format) up to date.
            runCatching { x.authenticate() }.getOrNull()?.let {
                prefs.account = it
                prefs.liveFormat = it.liveFormat
            }
            coroutineScope {
                xtreamActions.map { a -> async { a to x.raw(a) } }.awaitAll().toMap()
            }
        }
        val lib = build(texts)
        texts.forEach { (n, t) -> runCatching { cacheFile(n).writeText(t) } }
        lib
    }

    private fun build(texts: Map<String, String>): Library {
        val p = prefs.playlist ?: return Library.EMPTY
        if (p.type == "m3u") return M3u.parse(texts["m3u"] ?: "")
        val x = XtreamClient(http, p)
        val fmt = prefs.liveFormat
        return Library(
            liveCats = Parsers.categories(texts["get_live_categories"] ?: ""),
            channels = Parsers.live(texts["get_live_streams"] ?: "", x, fmt),
            vodCats = Parsers.categories(texts["get_vod_categories"] ?: ""),
            movies = Parsers.movies(texts["get_vod_streams"] ?: "", x),
            seriesCats = Parsers.categories(texts["get_series_categories"] ?: ""),
            series = Parsers.series(texts["get_series"] ?: ""),
        )
    }

    /** Checks a login from the phone setup page. Returns the account info on success. */
    suspend fun validate(p: Playlist): AccountInfo = withContext(Dispatchers.IO) {
        if (p.type == "m3u") {
            val text = getText(http, p.url)
            if (!text.contains("#EXTINF", ignoreCase = true)) {
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
        return runCatching { Parsers.movieInfo(x.raw("get_vod_info", "&vod_id=${m.id}")) }.getOrNull()
    }

    suspend fun seriesInfo(s: Series): SeriesInfo {
        val x = xtream() ?: throw IOException("Series need an Xtream login")
        val text = x.raw("get_series_info", "&series_id=${s.id}")
        return withContext(Dispatchers.Default) { Parsers.seriesInfo(text, s, x) }
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
