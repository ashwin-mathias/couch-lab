package com.sparky.iptv.data

data class Playlist(
    val type: String,          // "xtream" or "m3u"
    val name: String,
    val url: String,
    val user: String,
    val pass: String,
)

data class Category(val id: String, val name: String)

/**
 * Builds stream links on demand instead of storing a full link inside every item.
 * With hundreds of thousands of titles this saves a lot of memory.
 */
object StreamUrls {
    @Volatile private var base = ""
    @Volatile private var user = ""
    @Volatile private var pass = ""
    @Volatile var liveFormat = "m3u8"

    fun set(p: Playlist, fmt: String) {
        base = XtreamClient.normalizeBase(p.url)
        user = p.user
        pass = p.pass
        liveFormat = fmt
    }

    fun live(id: String) = "$base/live/$user/$pass/$id.$liveFormat"

    // ---- catch-up ----
    @Volatile private var offsetMin: Int? = null
    @Volatile private var timezone: String? = null
    /** "path" ({base}/timeshift/….ts), "hls" (the same path ending .m3u8) or "php" ({base}/streaming/timeshift.php?…). */
    @Volatile var catchupFormat = "path"

    fun setServerTime(acct: AccountInfo?) {
        offsetMin = acct?.offsetMin
        timezone = acct?.timezone
    }

    /** The server's offset from UTC at [ms]: measured offset, else its time zone name, else this TV's zone. */
    fun serverOffsetMs(ms: Long): Long {
        offsetMin?.let { return it * 60_000L }
        val tz = timezone?.let { java.util.TimeZone.getTimeZone(it) }?.takeIf { it.id != "GMT" || timezone == "GMT" }
            ?: java.util.TimeZone.getDefault()
        return tz.getOffset(ms).toLong()
    }

    /** Start time in the server's local time, "yyyy-MM-dd:HH-mm" (minute precision). */
    fun serverStamp(ms: Long): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date(ms + serverOffsetMs(ms)))
    }

    /** A recording of a channel from [startMs] (rounded down to the minute) for [minutes]. */
    fun catchup(id: String, startMs: Long, minutes: Int, fmt: String = catchupFormat): String {
        val stamp = serverStamp(startMs)
        return when (fmt) {
            "php" -> "$base/streaming/timeshift.php?username=$user&password=$pass&stream=$id&start=$stamp&duration=$minutes"
            "hls" -> "$base/timeshift/$user/$pass/$minutes/$stamp/$id.m3u8"
            else -> "$base/timeshift/$user/$pass/$minutes/$stamp/$id.ts"
        }
    }

    /** All recording link formats, the remembered working one first. */
    fun catchupFormats(): List<String> = (listOf(catchupFormat) + listOf("path", "hls", "php")).distinct()

    /** Human name for a link format, for messages and the log. */
    fun formatName(fmt: String) = when (fmt) {
        "php" -> "timeshift.php"
        "hls" -> "timeshift .m3u8"
        else -> "timeshift .ts"
    }
    fun movie(id: String, ext: String) = "$base/movie/$user/$pass/$id.$ext"
}

data class Channel(
    val id: String,
    val num: Int,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val epgId: String?,
    val directUrl: String? = null,   // only for M3U playlists
    /** Days of catch-up the provider keeps for this channel; 0 when it has none. */
    val archiveDays: Int = 0,
    /** Name without the region prefix ("US| ABC NEWS" → "ABC NEWS"). */
    val display: String = Names.strip(name),
    /** Provider divider rows like "##### NEWS #####" are shown as headings, not channels. */
    val divider: Boolean = Names.isDivider(name),
) {
    val favKey get() = "live:$id"
    val url: String get() = directUrl ?: StreamUrls.live(id)
}

data class Movie(
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val rating: String?,
    val added: Long,
    val ext: String,
) {
    val url: String get() = StreamUrls.movie(id, ext)
    val favKey get() = "movie:$id"
    val progressKey get() = "movie:$id"
}

data class Series(
    val id: String,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val plot: String?,
    val year: String?,
    val rating: String?,
    val genre: String?,
    val lastModified: Long,
) {
    val favKey get() = "series:$id"
}

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String?,
    val image: String?,
    val durationSecs: Int,
    val url: String,
) {
    val progressKey get() = "episode:$id"
}

data class SeriesInfo(
    val series: Series,
    val seasons: List<Int>,
    val episodes: Map<Int, List<Episode>>,
    val backdrop: String?,
)

data class MovieInfo(
    val plot: String?,
    val year: String?,
    val genre: String?,
    val durationSecs: Int,
    val backdrop: String?,
    val cast: String?,
    val director: String?,
)

data class EpgEntry(
    val title: String,
    val description: String,
    val start: Long,   // epoch millis
    val end: Long,
    /** The provider keeps a recording of this show (catch-up). Only known from the full listing. */
    val archive: Boolean = false,
)

data class AccountInfo(
    val status: String?,
    val expires: Long?,       // epoch millis
    val maxConnections: String?,
    val liveFormat: String,
    /** The server's time zone name and its offset from UTC, used to build catch-up links. */
    val timezone: String? = null,
    val offsetMin: Int? = null,
)

class Library(
    val liveCats: List<Category>,
    val channels: List<Channel>,
    val vodCats: List<Category>,
    val movies: List<Movie>,
    val seriesCats: List<Category>,
    val series: List<Series>,
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()

    companion object {
        val EMPTY = Library(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Something watched recently: a channel, a movie or a series. */
data class RecentItem(val type: String, val id: String, val ts: Long)

data class Progress(val key: String, val pos: Long, val dur: Long, val ts: Long) {
    val fraction: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}
