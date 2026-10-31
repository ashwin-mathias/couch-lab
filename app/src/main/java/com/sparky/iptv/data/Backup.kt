package com.sparky.iptv.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup file (format "sparky-backup", version 1): login, favorites, regions, playback settings and
 * optionally watch history, as one JSON file kept on the phone. With the login inside, the data part
 * is encrypted with AES-256-GCM, the key coming from the passphrase through PBKDF2-SHA256.
 */
object Backup {
    const val FORMAT = "sparky-backup"
    const val VERSION = 1
    const val ROUNDS = 210_000

    data class Options(val login: Boolean, val favorites: Boolean, val playback: Boolean, val history: Boolean)

    enum class Mode { ADD, REPLACE }

    /** What a checked file holds, ready to apply. */
    class Contents(val data: JSONObject, val created: String, val app: String) {
        val hasLogin get() = data.has("login")
        val favoriteCount get() = data.optJSONArray("favorites")?.length() ?: 0
        fun login(): Playlist? = data.optJSONObject("login")?.let {
            Playlist(it.optString("type", "xtream"), it.optString("name", "Home"), it.optString("url"), it.optString("user"), it.optString("pass"))
        }
    }

    class BackupException(message: String) : Exception(message)
    /** Thrown when the passphrase is wrong (or missing for an encrypted file). */
    class WrongPassphrase(message: String) : Exception(message)

    private val PLAYBACK_KEYS = listOf("sub_style", "sleep_default", "vod_audio_lang", "vod_text_lang", "live_format", "catchup_format")

    // ---------------- making a backup ----------------

    /** Builds the file text. [nameOf] gives a favorite's name (for matching after a provider renumbers). */
    fun create(
        prefs: Prefs, opts: Options, passphrase: String?, device: String, appVersion: String,
        nameOf: (String) -> String?,
    ): String {
        val all = prefs.all()
        val d = JSONObject()
        if (opts.login) {
            val p = prefs.playlist ?: throw BackupException("There's no login on this TV to back up.")
            d.put("login", JSONObject().put("type", p.type).put("name", p.name).put("url", p.url).put("user", p.user).put("pass", p.pass))
        }
        if (opts.favorites) {
            val favs = JSONArray()
            prefs.favorites().forEach { k -> favs.put(JSONObject().put("key", k).apply { nameOf(k)?.let { put("name", it) } }) }
            d.put("favorites", favs)
            prefs.favOrder()?.let { d.put("fav_order", JSONArray(it)) }
            d.put("fav_folders", Favs.foldersToJson(prefs.favFolders()))
            d.put("my_regions", JSONArray(prefs.myRegions().toList()))
            d.put("regions_closed", JSONArray(prefs.regionsClosed().toList()))
        }
        if (opts.playback) {
            val pb = JSONObject()
            PLAYBACK_KEYS.forEach { k -> all[k]?.let { pb.put(k, it) } }
            d.put("playback", pb)
            d.put("tracks", JSONObject().apply { all.filterKeys { it.startsWith("trk:") }.forEach { (k, v) -> put(k, v) } })
            (all["trk_order"] as? String)?.let { d.put("trk_order", it) }
            d.put("tiles", JSONObject().apply { all.filterKeys { it.startsWith("tiles:") }.forEach { (k, v) -> put(k, v) } })
        }
        if (opts.history) {
            d.put("history", JSONObject()
                .put("pos", JSONObject().apply { all.filterKeys { it.startsWith("pos:") }.forEach { (k, v) -> put(k, v) } })
                .put("slast", JSONObject().apply { all.filterKeys { it.startsWith("slast:") }.forEach { (k, v) -> put(k, v) } })
                .put("recents", all["recents"] as? String ?: "[]"))
        }
        val out = JSONObject()
            .put("format", FORMAT).put("version", VERSION).put("app", appVersion)
            .put("created", iso(System.currentTimeMillis())).put("device", device)
        if (opts.login) {
            val pass = passphrase ?: throw BackupException("Enter a passphrase.")
            out.put("encrypted", true).put("data", encrypt(d.toString(), pass))
        } else {
            out.put("encrypted", false).put("data", d)
        }
        return out.toString(2)
    }

    // ---------------- reading a backup ----------------

    /** Checks the file and decrypts it. Throws [BackupException] or [WrongPassphrase] with a message for the phone. */
    fun read(text: String, passphrase: String?): Contents {
        val o = runCatching { JSONObject(text) }.getOrNull()
            ?: throw BackupException("This isn't a Sparky backup file.")
        if (o.optString("format") != FORMAT) throw BackupException("This isn't a Sparky backup file.")
        val v = o.optInt("version", -1)
        if (v > VERSION) throw BackupException("This backup is from a newer Sparky. Update the app first.")
        if (v < 1) throw BackupException("This backup file is damaged.")
        val data = if (o.optBoolean("encrypted")) {
            if (passphrase.isNullOrEmpty()) throw WrongPassphrase("Enter the backup's passphrase.")
            val plain = decrypt(o.optString("data"), passphrase) ?: throw WrongPassphrase("Wrong passphrase")
            runCatching { JSONObject(plain) }.getOrNull() ?: throw BackupException("This backup file is damaged.")
        } else {
            o.optJSONObject("data") ?: throw BackupException("This backup file is damaged.")
        }
        return Contents(data, o.optString("created"), o.optString("app"))
    }

    // ---------------- applying a backup ----------------

    /**
     * Writes everything in one go (all or nothing). The login is not written here; the caller logs in
     * with [Contents.login] first. Returns the favorite names to keep for matching renumbered channels.
     */
    fun apply(prefs: Prefs, c: Contents, mode: Mode): JSONObject {
        val d = c.data
        val all = prefs.all()
        val e = prefs.edit()
        val names = JSONObject()
        d.optJSONArray("favorites")?.let { arr ->
            val fileFavs = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            fileFavs.forEach { f -> f.optString("name").ifBlank { null }?.let { names.put(f.optString("key"), it) } }
            val fileKeys = fileFavs.map { it.optString("key") }.filter { it.contains(':') }
            val fileOrder = d.optJSONArray("fav_order")?.let { a -> List(a.length()) { a.optString(it) } } ?: fileKeys
            val fileFolders = d.optJSONArray("fav_folders")?.let { Favs.foldersFromArray(it) } ?: emptyList()
            val keys: Set<String>
            val order: List<String>
            val folders: List<FavFolder>
            if (mode == Mode.REPLACE) {
                keys = fileKeys.toSet()
                order = Favs.normalize(fileOrder, keys)
                folders = fileFolders.map { it.copy(keys = it.keys.filter { k -> k in keys }) }
            } else {
                val mine = prefs.favorites()
                keys = mine + fileKeys
                order = Favs.normalize((prefs.favOrder() ?: mine.toList()) + fileOrder, keys)
                val merged = prefs.favFolders().toMutableList()
                for (f in fileFolders) {
                    val i = merged.indexOfFirst { it.name.equals(f.name, ignoreCase = true) }
                    if (i >= 0) merged[i] = merged[i].copy(keys = (merged[i].keys + f.keys).distinct())
                    else merged.add(f.copy(id = Favs.newId(merged)))
                }
                folders = merged.map { it.copy(keys = it.keys.filter { k -> k in keys }) }
            }
            e.putStringSet("favorites", HashSet(keys))
            e.putString("fav_order", Favs.orderToJson(order))
            e.putString("fav_folders", Favs.foldersToJson(folders).toString())
            d.optJSONArray("my_regions")?.let { a -> e.putStringSet("my_regions", HashSet(List(a.length()) { a.optString(it) })) }
            d.optJSONArray("regions_closed")?.let { a -> e.putStringSet("regions_closed", HashSet(List(a.length()) { a.optString(it) })) }
        }
        d.optJSONObject("playback")?.let { pb ->
            if (mode == Mode.REPLACE) PLAYBACK_KEYS.forEach { e.remove(it) }
            PLAYBACK_KEYS.forEach { k ->
                if (!pb.has(k)) return@forEach
                when (val v = pb.get(k)) {
                    is Int -> e.putInt(k, v)
                    is Number -> e.putInt(k, v.toInt())
                    else -> e.putString(k, v.toString())
                }
            }
        }
        d.optJSONObject("tracks")?.let { t ->
            if (mode == Mode.REPLACE) all.keys.filter { it.startsWith("trk:") }.forEach { e.remove(it) }
            t.keys().forEach { k -> if (k.startsWith("trk:")) e.putString(k, t.optString(k)) }
            d.optString("trk_order").ifBlank { null }?.let { e.putString("trk_order", it) }
        }
        d.optJSONObject("tiles")?.let { t ->
            if (mode == Mode.REPLACE) all.keys.filter { it.startsWith("tiles:") }.forEach { e.remove(it) }
            t.keys().forEach { k -> if (k.startsWith("tiles:")) e.putBoolean(k, t.optBoolean(k)) }
        }
        d.optJSONObject("history")?.let { h ->
            if (mode == Mode.REPLACE) all.keys.filter { it.startsWith("pos:") || it.startsWith("slast:") }.forEach { e.remove(it) }
            h.optJSONObject("pos")?.let { p ->
                p.keys().forEach { k ->
                    if (!k.startsWith("pos:")) return@forEach
                    val v = p.optString(k)
                    val mineTs = (all[k] as? String)?.split(",")?.getOrNull(2)?.toLongOrNull() ?: -1L
                    val fileTs = v.split(",").getOrNull(2)?.toLongOrNull() ?: 0L
                    if (mode == Mode.REPLACE || fileTs > mineTs) e.putString(k, v)
                }
            }
            h.optJSONObject("slast")?.let { s ->
                s.keys().forEach { k -> if (k.startsWith("slast:") && (mode == Mode.REPLACE || all[k] == null)) e.putString(k, s.optString(k)) }
            }
            h.optString("recents").ifBlank { null }?.let { raw ->
                if (mode == Mode.REPLACE) e.putString("recents", raw)
                else e.putString("recents", mergeRecents(all["recents"] as? String, raw))
            }
        }
        if (!e.commit()) throw BackupException("Couldn't save the settings on the TV.")
        return names
    }

    private fun mergeRecents(mine: String?, file: String): String {
        fun parse(raw: String?) = runCatching {
            val a = JSONArray(raw ?: "[]"); (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }.getOrDefault(emptyList())
        val merged = (parse(mine) + parse(file))
            .sortedByDescending { it.optLong("ts") }
            .distinctBy { it.optString("t") + ":" + it.optString("id") }
            .take(12)
        return JSONArray(merged).toString()
    }

    // ---------------- crypto ----------------

    private fun encrypt(plain: String, pass: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(pbkdf2(pass, salt), "AES"), GCMParameterSpec(128, iv))
        return Base64.encodeToString(salt + iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(b64: String, pass: String): String? = runCatching {
        val raw = Base64.decode(b64, Base64.NO_WRAP)
        if (raw.size < 16 + 12 + 16) return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(pbkdf2(pass, raw.copyOfRange(0, 16)), "AES"), GCMParameterSpec(128, raw.copyOfRange(16, 28)))
        String(c.doFinal(raw.copyOfRange(28, raw.size)), Charsets.UTF_8)
    }.getOrNull()

    /** PBKDF2-HMAC-SHA256, 32-byte key. Written out because the built-in one needs Android 8. */
    fun pbkdf2(pass: String, salt: ByteArray, rounds: Int = ROUNDS): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pass.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val out = u.copyOf()
        for (r in 1 until rounds) {
            u = mac.doFinal(u)
            for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
        }
        return out
    }

    fun iso(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date(ms))

    fun fileName(ms: Long): String =
        "sparky-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms)) + ".json"
}
