package com.sparky.iptv.platform

import android.app.Activity
import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import com.sparky.iptv.data.KeyValueStore
import com.sparky.iptv.setup.Net
import com.sparky.iptv.ui.platform.AppPlatform
import java.io.File
import java.lang.ref.WeakReference
import java.net.Inet4Address

/** The Google TV app's [AppPlatform] (3.1): everything here behaves as it did in 3.0. */
class AndroidAppPlatform(private val app: Application) : AppPlatform {
    /** The activity on screen, so Back on Home can close it. */
    var activity: WeakReference<Activity>? = null

    override val filesDir: File get() = app.filesDir
    override val cacheDir: File get() = app.cacheDir
    override val store: KeyValueStore by lazy { AndroidPlatform.store(app) }
    override fun isOnline() = AndroidPlatform.isOnline(app)

    override val versionName: String by lazy {
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
    }
    override val deviceLine: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    override val deviceName: String
        get() = runCatching { Settings.Global.getString(app.contentResolver, "device_name") }.getOrNull()
            ?: Build.MODEL ?: "Your TV"

    override fun localIp(): String? {
        runCatching {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val lp = cm.getLinkProperties(cm.activeNetwork)
            lp?.linkAddresses?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress?.let { return it }
        }
        return Net.fromInterfaces()
    }

    override fun readAsset(name: String): String? =
        runCatching { app.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()

    override fun exit() { activity?.get()?.finish() }

    override val selfUpdates = true
    override fun installedBuild() = AndroidUpdates.installedBuild(app)
    override fun updateFile() = AndroidUpdates.apkFile(app)
    override fun canInstall() = AndroidUpdates.canInstall(app)
    override fun openInstallPermission() = AndroidUpdates.openInstallPermission(app)
    override fun launchInstaller() = AndroidUpdates.launchInstaller(app)
}
