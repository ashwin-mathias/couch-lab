package com.sparky.iptv.ui.platform

import com.sparky.iptv.data.KeyValueStore
import java.io.File

/**
 * What the shared screens and view model need from the device (3.1). Android TV and the desktop
 * app each provide one, and set [Platform.app] before anything else runs.
 *
 * The composable hooks (BackHandler, key events, icons, fonts, video surface, images, theme) are
 * functions with the same names in each app's `com.sparky.iptv.ui.platform` package.
 */
interface AppPlatform {
    val filesDir: File
    val cacheDir: File
    val store: KeyValueStore
    fun isOnline(): Boolean
    /** "3.1 (build 25)". */
    val versionName: String
    /** "Google Chromecast · Android 14 (SDK 34)" or "Windows 11 · x64". */
    val deviceLine: String
    /** The name people gave the device ("Living room TV"), for backups. */
    val deviceName: String
    /** This device's address on the home network, for the phone pages. */
    fun localIp(): String?
    /** A text file shipped with the app (about.json, changelog.json). */
    fun readAsset(name: String): String?
    /** Leaves the app (Back twice on Home). */
    fun exit()

    // ---- app updates: Android installs APKs itself; the stores update the other apps ----
    val selfUpdates: Boolean
    fun installedBuild(): Int
    fun updateFile(): File
    fun canInstall(): Boolean
    fun openInstallPermission()
    fun launchInstaller()
}

object Platform {
    lateinit var app: AppPlatform
    fun isSet() = ::app.isInitialized
}
