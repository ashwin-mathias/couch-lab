package com.sparky.iptv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Talks to an Xtream Codes compatible server (player_api.php). */
class XtreamClient(private val http: OkHttpClient, private val p: Playlist) {
    private val base = normalizeBase(p.url)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun api(extra: String) =
        "$base/player_api.php?username=${enc(p.user)}&password=${enc(p.pass)}$extra"

    suspend fun raw(action: String, extra: String = ""): String = getText(http, api("&action=$action$extra"))

    suspend fun authenticate(): AccountInfo {
        val text = getText(http, api(""))
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IOException("The server didn't answer like an IPTV server. Check the URL.")
        val ui = root.optJSONObject("user_info") ?: throw IOException("Login failed. Check username and password.")
        val auth = ui.s("auth")
        if (auth != "1") throw IOException("Login failed. Check username and password.")
        val status = ui.s("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Account status: $status")
        }
        val formats = ui.optJSONArray("allowed_output_formats")
        val fmtList = buildList { if (formats != null) for (i in 0 until formats.length()) add(formats.optString(i)) }
        val fmt = when {
            fmtList.isEmpty() || "m3u8" in fmtList -> "m3u8"
            "ts" in fmtList -> "ts"
            else -> fmtList.first()
        }
        return AccountInfo(
            status = status,
            expires = ui.s("exp_date")?.toLongOrNull()?.times(1000),
            maxConnections = ui.s("max_connections"),
            liveFormat = fmt,
        )
    }

    fun liveUrl(id: String, fmt: String) = "$base/live/${p.user}/${p.pass}/$id.$fmt"
    fun movieUrl(id: String, ext: String) = "$base/movie/${p.user}/${p.pass}/$id.$ext"
    fun episodeUrl(id: String, ext: String) = "$base/series/${p.user}/${p.pass}/$id.$ext"

    companion object {
        fun normalizeBase(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
            // People often paste a full player_api.php or get.php link; keep only the server part.
            listOf("/player_api.php", "/get.php", "/panel_api.php").forEach { suffix ->
                val i = u.indexOf(suffix, ignoreCase = true)
                if (i > 0) u = u.substring(0, i)
            }
            return u.trimEnd('/')
        }
    }
}

const val USER_AGENT = "SparkyIPTV/1.0 (Linux; Android TV)"

suspend fun getText(http: OkHttpClient, url: String): String = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
    http.newCall(req).execute().use { r ->
        if (!r.isSuccessful) throw IOException("Server returned HTTP ${r.code}")
        r.body?.string() ?: ""
    }
}

/** Null-safe, trimmed string read that copes with numbers, nulls and empty strings. */
fun JSONObject.s(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = opt(key)?.toString()?.trim() ?: return null
    return v.ifEmpty { null }
}

object Parsers {
    private fun array(text: String): JSONArray {
        val t = text.trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONArray()
    }

    private fun objects(text: String): Sequence<JSONObject> {
        val arr = array(text)
        return (0 until arr.length()).asSequence().mapNotNull { arr.optJSONObject(it) }
    }

    fun categories(text: String): List<Category> = objects(text).mapNotNull { o ->
        val id = o.s("category_id") ?: return@mapNotNull null
        Category(id, o.s("category_name") ?: "Other")
    }.toList()

    fun live(text: String, x: XtreamClient, fmt: String): List<Channel> = objects(text).mapIndexedNotNull { i, o ->
        val id = o.s("stream_id") ?: return@mapIndexedNotNull null
        Channel(
            id = id,
            num = o.s("num")?.toDoubleOrNull()?.toInt() ?: (i + 1),
            name = o.s("name") ?: "Channel ${i + 1}",
            logo = o.s("stream_icon"),
            categoryId = o.s("category_id") ?: "",
            epgId = o.s("epg_channel_id"),
            url = x.liveUrl(id, fmt),
        )
    }.sortedBy { it.num }.toList()

    fun movies(text: String, x: XtreamClient): List<Movie> = objects(text).mapNotNull { o ->
        val id = o.s("stream_id") ?: return@mapNotNull null
        Movie(
            id = id,
            name = o.s("name") ?: "Untitled",
            poster = o.s("stream_icon"),
            categoryId = o.s("category_id") ?: "",
            rating = o.s("rating")?.takeIf { it != "0" },
            added = o.s("added")?.toLongOrNull() ?: 0L,
            url = x.movieUrl(id, o.s("container_extension") ?: "mp4"),
        )
    }.toList()

    fun series(text: String): List<Series> = objects(text).mapNotNull { o ->
        val id = o.s("series_id") ?: return@mapNotNull null
        Series(
            id = id,
            name = o.s("name") ?: "Untitled",
            cover = o.s("cover"),
            categoryId = o.s("category_id") ?: "",
            plot = o.s("plot"),
            year = (o.s("releaseDate") ?: o.s("release_date") ?: o.s("year"))?.take(4),
            rating = o.s("rating")?.takeIf { it != "0" },
            genre = o.s("genre"),
            lastModified = o.s("last_modified")?.toLongOrNull() ?: 0L,
        )
    }.toList()

    fun movieInfo(text: String): MovieInfo? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val info = root.optJSONObject("info") ?: return null
        val backdrop = info.optJSONArray("backdrop_path")?.optString(0)?.ifBlank { null }
        val dur = info.s("duration_secs")?.toIntOrNull() ?: parseDuration(info.s("duration"))
        return MovieInfo(
            plot = info.s("plot") ?: info.s("description"),
            year = (info.s("releasedate") ?: info.s("release_date"))?.take(4),
            genre = info.s("genre"),
            durationSecs = dur,
            backdrop = backdrop,
            cast = info.s("cast") ?: info.s("actors"),
            director = info.s("director"),
        )
    }

    fun seriesInfo(text: String, s: Series, x: XtreamClient): SeriesInfo {
        val root = JSONObject(text)
        val info = root.optJSONObject("info")
        val backdrop = info?.optJSONArray("backdrop_path")?.optString(0)?.ifBlank { null }
        val eps = LinkedHashMap<Int, MutableList<Episode>>()

        fun addEp(e: JSONObject, seasonKey: Int) {
            val id = e.s("id") ?: return
            val season = e.s("season")?.toIntOrNull() ?: seasonKey
            val ei = e.optJSONObject("info")
            val dur = ei?.s("duration_secs")?.toIntOrNull() ?: parseDuration(ei?.s("duration"))
            eps.getOrPut(season) { ArrayList() }.add(
                Episode(
                    id = id,
                    season = season,
                    number = e.s("episode_num")?.toIntOrNull() ?: 0,
                    title = e.s("title") ?: "Episode",
                    plot = ei?.s("plot"),
                    image = ei?.s("movie_image"),
                    durationSecs = dur,
                    url = x.episodeUrl(id, e.s("container_extension") ?: "mp4"),
                )
            )
        }

        when (val any = root.opt("episodes")) {
            is JSONObject -> for (k in any.keys()) {
                val arr = any.optJSONArray(k) ?: continue
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEp(it, k.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (i in 0 until any.length()) {
                val inner = any.optJSONArray(i)
                if (inner != null) {
                    for (j in 0 until inner.length()) inner.optJSONObject(j)?.let { addEp(it, i + 1) }
                } else {
                    any.optJSONObject(i)?.let { addEp(it, 1) }
                }
            }
        }
        eps.values.forEach { list -> list.sortBy { it.number } }
        return SeriesInfo(
            series = s.copy(plot = info?.s("plot") ?: s.plot, genre = info?.s("genre") ?: s.genre),
            seasons = eps.keys.sorted(),
            episodes = eps.mapValues { it.value.toList() },
            backdrop = backdrop,
        )
    }

    fun shortEpg(text: String): List<EpgEntry> {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<EpgEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = o.s("start_timestamp")?.toLongOrNull() ?: continue
            val end = o.s("stop_timestamp")?.toLongOrNull() ?: continue
            out.add(EpgEntry(b64(o.s("title")), b64(o.s("description")), start * 1000, end * 1000))
        }
        return out.sortedBy { it.start }
    }

    private fun b64(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim() }.getOrDefault(s)
    }

    private fun parseDuration(s: String?): Int {
        if (s.isNullOrBlank()) return 0
        val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0
        }
    }
}
