package com.sparky.iptv.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Reads the big provider lists straight from disk, one item at a time, keeping only the
 * fields the app uses. This keeps memory low even for catalogs with tens of thousands of titles.
 */
object StreamParsers {

    private fun JsonReader.value(): String? = when (peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> nextString().trim().ifEmpty { null }
        JsonToken.BOOLEAN -> nextBoolean().toString()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    /** Calls [onItem] for every object in the file's top-level array. Anything else is ignored. */
    fun readObjects(file: File, fields: Set<String>, onItem: (Map<String, String?>) -> Unit) {
        if (!file.exists() || file.length() == 0L) return
        JsonReader(InputStreamReader(BufferedInputStream(FileInputStream(file), 64 * 1024), Charsets.UTF_8)).use { r ->
            r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_ARRAY) return
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                r.beginObject()
                val m = HashMap<String, String?>(fields.size * 2)
                while (r.hasNext()) {
                    val k = r.nextName()
                    if (k in fields) m[k] = r.value() else r.skipValue()
                }
                r.endObject()
                onItem(m)
            }
            r.endArray()
        }
    }

    fun categories(file: File): List<Category> {
        val out = ArrayList<Category>()
        readObjects(file, setOf("category_id", "category_name")) { m ->
            val id = m["category_id"] ?: return@readObjects
            out.add(Category(id, m["category_name"] ?: "Other"))
        }
        return out
    }

    fun live(file: File, x: XtreamClient, fmt: String): List<Channel> {
        val out = ArrayList<Channel>()
        readObjects(file, setOf("stream_id", "num", "name", "stream_icon", "category_id", "epg_channel_id")) { m ->
            val id = m["stream_id"] ?: return@readObjects
            val n = out.size + 1
            out.add(
                Channel(
                    id = id,
                    num = m["num"]?.toDoubleOrNull()?.toInt() ?: n,
                    name = m["name"] ?: "Channel $n",
                    logo = m["stream_icon"],
                    categoryId = m["category_id"] ?: "",
                    epgId = m["epg_channel_id"],
                    url = x.liveUrl(id, fmt),
                )
            )
        }
        out.sortBy { it.num }
        return out
    }

    fun movies(file: File, x: XtreamClient): List<Movie> {
        val out = ArrayList<Movie>()
        readObjects(file, setOf("stream_id", "name", "stream_icon", "category_id", "rating", "added", "container_extension")) { m ->
            val id = m["stream_id"] ?: return@readObjects
            out.add(
                Movie(
                    id = id,
                    name = m["name"] ?: "Untitled",
                    poster = m["stream_icon"],
                    categoryId = m["category_id"] ?: "",
                    rating = m["rating"]?.takeIf { it != "0" },
                    added = m["added"]?.toLongOrNull() ?: 0L,
                    url = x.movieUrl(id, m["container_extension"] ?: "mp4"),
                )
            )
        }
        return out
    }

    fun series(file: File): List<Series> {
        val out = ArrayList<Series>()
        readObjects(
            file,
            setOf("series_id", "name", "cover", "category_id", "plot", "releaseDate", "release_date", "year", "rating", "genre", "last_modified"),
        ) { m ->
            val id = m["series_id"] ?: return@readObjects
            out.add(
                Series(
                    id = id,
                    name = m["name"] ?: "Untitled",
                    cover = m["cover"],
                    categoryId = m["category_id"] ?: "",
                    plot = m["plot"],
                    year = (m["releaseDate"] ?: m["release_date"] ?: m["year"])?.take(4),
                    rating = m["rating"]?.takeIf { it != "0" },
                    genre = m["genre"],
                    lastModified = m["last_modified"]?.toLongOrNull() ?: 0L,
                )
            )
        }
        return out
    }
}
