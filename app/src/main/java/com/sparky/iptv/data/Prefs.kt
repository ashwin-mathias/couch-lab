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

    var lastChannel: String?
        get() = sp.getString("last_channel", null)
        set(v) = sp.edit().putString("last_channel", v).apply()

    fun seriesLastEpisode(seriesId: String): String? = sp.getString("slast:$seriesId", null)
    fun setSeriesLastEpisode(seriesId: String, episodeId: String) =
        sp.edit().putString("slast:$seriesId", episodeId).apply()
}
