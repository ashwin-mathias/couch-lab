package com.sparky.iptv.data

import android.content.Context
import org.json.JSONObject

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("sparky", Context.MODE_PRIVATE)

    var playlist: Playlist?
        get() = sp.getString("playlist", null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                Playlist(
                    type = o.getString("type"),
                    name = o.optString("name", "Home"),
                    url = o.getString("url"),
                    user = o.optString("user", ""),
                    pass = o.optString("pass", ""),
                )
            }.getOrNull()
        }
        set(v) {
            val e = sp.edit()
            if (v == null) e.remove("playlist") else e.putString(
                "playlist",
                JSONObject()
                    .put("type", v.type).put("name", v.name).put("url", v.url)
                    .put("user", v.user).put("pass", v.pass).toString()
            )
            e.apply()
        }

    var liveFormat: String
        get() = sp.getString("live_format", "m3u8") ?: "m3u8"
        set(v) = sp.edit().putString("live_format", v).apply()

    var account: AccountInfo?
        get() = sp.getString("account", null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                AccountInfo(
                    status = o.optString("status").ifBlank { null },
                    expires = o.optLong("expires", 0L).takeIf { it > 0 },
                    maxConnections = o.optString("max").ifBlank { null },
                    liveFormat = o.optString("format", "m3u8"),
                )
            }.getOrNull()
        }
        set(v) {
            if (v == null) { sp.edit().remove("account").apply(); return }
            sp.edit().putString(
                "account",
                JSONObject().put("status", v.status ?: "").put("expires", v.expires ?: 0L)
                    .put("max", v.maxConnections ?: "").put("format", v.liveFormat).toString()
            ).apply()
        }

    fun favorites(): Set<String> = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    fun saveFavorites(s: Set<String>) = sp.edit().putStringSet("favorites", HashSet(s)).apply()

    fun progress(key: String): Progress? {
        val parts = sp.getString("pos:$key", null)?.split(",") ?: return null
        if (parts.size != 3) return null
        return runCatching { Progress(key, parts[0].toLong(), parts[1].toLong(), parts[2].toLong()) }.getOrNull()
    }

    fun saveProgress(key: String, pos: Long, dur: Long) {
        sp.edit().putString("pos:$key", "$pos,$dur,${System.currentTimeMillis()}").apply()
    }

    fun clearProgress(key: String) = sp.edit().remove("pos:$key").apply()

    fun allProgress(prefix: String): List<Progress> =
        sp.all.keys.filter { it.startsWith("pos:$prefix") }.mapNotNull { progress(it.removePrefix("pos:")) }

    fun clearAllProgress() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("pos:") || it.startsWith("slast:") }.forEach { e.remove(it) }
        e.apply()
    }

    // ---- regions ----
    fun myRegions(): Set<String> = sp.getStringSet("my_regions", emptySet())?.toSet() ?: emptySet()
    fun saveMyRegions(s: Set<String>) = sp.edit().putStringSet("my_regions", HashSet(s)).apply()

    var regionsPrompted: Boolean
        get() = sp.getBoolean("regions_prompted", false)
        set(v) = sp.edit().putBoolean("regions_prompted", v).apply()

    // ---- recently watched (newest first, up to 12) ----
    fun recents(): List<RecentItem> = runCatching {
        val arr = org.json.JSONArray(sp.getString("recents", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            RecentItem(o.optString("t"), o.optString("id"), o.optLong("ts"))
        }
    }.getOrDefault(emptyList())

    fun saveRecents(list: List<RecentItem>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.type).put("id", it.id).put("ts", it.ts)) }
        sp.edit().putString("recents", arr.toString()).apply()
    }

    // ---- tile or list view, per list ----
    fun tileView(key: String, default: Boolean): Boolean = sp.getBoolean("tiles:$key", default)
    fun setTileView(key: String, on: Boolean) = sp.edit().putBoolean("tiles:$key", on).apply()

    /** Set once the playlist has been downloaded by a version that records catch-up info. */
    var catchupScanned: Boolean
        get() = sp.getBoolean("catchup_scanned", false)
        set(v) = sp.edit().putBoolean("catchup_scanned", v).apply()

    // ---- audio / subtitle choice per channel ("audioLang|textLang", text "off" = subtitles off) ----
    fun trackChoice(key: String): Pair<String?, String?> {
        val raw = sp.getString("trk:$key", null) ?: return null to null
        val parts = raw.split("|")
        return parts.getOrNull(0)?.ifBlank { null } to parts.getOrNull(1)?.ifBlank { null }
    }

    fun saveTrackChoice(key: String, audio: String?, text: String?) =
        sp.edit().putString("trk:$key", "${audio ?: ""}|${text ?: ""}").apply()

    // ---- daily refresh ----
    var lastRefresh: Long
        get() = sp.getLong("last_refresh", 0L)
        set(v) = sp.edit().putLong("last_refresh", v).apply()

    var lastChannel: String?
        get() = sp.getString("last_channel", null)
        set(v) = sp.edit().putString("last_channel", v).apply()

    fun seriesLastEpisode(seriesId: String): String? = sp.getString("slast:$seriesId", null)
    fun setSeriesLastEpisode(seriesId: String, episodeId: String) =
        sp.edit().putString("slast:$seriesId", episodeId).apply()
}
