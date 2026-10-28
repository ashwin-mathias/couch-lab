package com.sparky.iptv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val build: Int, val title: String, val apkUrl: String)

/** Checks GitHub for a newer build and installs it (Android always asks the user to confirm). */
object Updater {
    private const val LATEST = "https://api.github.com/repos/ashwin-mathias/couch-lab/releases/latest"

    @Suppress("DEPRECATION")
    fun installedBuild(ctx: Context): Int = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }.getOrDefault(0)

    /** Returns the newer release, or null if this is already the newest (or GitHub can't be reached). */
    suspend fun check(ctx: Context, repo: Repository): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject(getText(repo.http, LATEST))
            val build = o.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@runCatching null
            val assets = o.optJSONArray("assets")
            var url: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
            }
            val installed = installedBuild(ctx)
            AppLog.i("Update check: installed build $installed, latest build $build")
            if (url == null || build <= installed) null
            else UpdateInfo(build, o.optString("name").ifBlank { "Build $build" }, url)
        }.onFailure { AppLog.w("Update check failed: ${it.message}") }.getOrNull()
    }

    fun apkFile(ctx: Context) = File(File(ctx.cacheDir, "updates"), "sparky-iptv.apk")

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows this app to install updates. */
    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure { AppLog.e("Couldn't open install permission screen", it) }
    }

    /** Hands the downloaded file to Android's installer. */
    fun launchInstaller(ctx: Context) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apkFile(ctx))
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
