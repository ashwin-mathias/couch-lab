package com.sparky.iptv.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sparky.iptv.data.AppLog
import java.io.File

/** Installing an update APK (Android only; moved out of the shared Updater in 3.0). */
object AndroidUpdates {
    @Suppress("DEPRECATION")
    fun installedBuild(ctx: Context): Int = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }.getOrDefault(0)

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
