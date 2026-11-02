package com.sparky.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val build: Int, val title: String, val apkUrl: String, val sha256: String)

/**
 * Checks GitHub for a newer build (3.0: the shared part; installing an APK is in the Android app's
 * AndroidUpdates).
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/ashwin-mathias/couch-lab/releases/latest"
    /** Downloads are only accepted from this repository's releases. */
    private const val TRUSTED_PREFIX = "https://github.com/ashwin-mathias/couch-lab/releases/download/"
    private val SHA256 = Regex("^[0-9a-fA-F]{64}")

    /** Returns the newer release, or null if this is already the newest (or GitHub can't be reached). */
    suspend fun check(repo: Repository, installed: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject(getText(repo.http, LATEST))
            val build = o.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@runCatching null
            val assets = o.optJSONArray("assets")
            var url: String? = null
            var shaUrl: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                val link = a.optString("browser_download_url")
                if (!link.startsWith(TRUSTED_PREFIX)) continue
                if (name.endsWith(".apk")) url = link
                if (name.endsWith(".apk.sha256")) shaUrl = link
            }
            AppLog.i("Update check: installed build $installed, latest build $build")
            if (url == null || build <= installed) return@runCatching null
            if (shaUrl == null) {
                AppLog.w("Build $build has no checksum file; not offering it")
                return@runCatching null
            }
            val sha = SHA256.find(getText(repo.http, shaUrl).trim())?.value
            if (sha == null) {
                AppLog.w("Build $build checksum file is unreadable; not offering it")
                return@runCatching null
            }
            UpdateInfo(build, o.optString("name").ifBlank { "Build $build" }, url, sha)
        }.onFailure { AppLog.w("Update check failed: ${it.message}") }.getOrNull()
    }

    /** True if [f] is already the update with this SHA-256, so it needn't be downloaded again (2.9). */
    suspend fun alreadyDownloaded(f: File, sha256: String): Boolean = withContext(Dispatchers.IO) {
        if (!f.exists() || f.length() == 0L) return@withContext false
        runCatching {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            f.inputStream().use { inp ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }.equals(sha256, ignoreCase = true)
        }.getOrDefault(false)
    }
}
