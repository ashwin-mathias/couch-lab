package com.sparky.iptv.platform

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.B64
import com.sparky.iptv.data.KeyValueStore
import com.sparky.iptv.data.SecretBox
import com.sparky.iptv.data.SecretStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Android side of the shared code (3.0): settings, the login key, the log and the network check.
 * The shared code itself lives in the core module and has no Android in it.
 */
object AndroidPlatform {
    /** Call once, before anything reads settings or logs. */
    fun init(ctx: Context) {
        SecretBox.store = KeystoreSecrets
        AppLog.sink = { Log.d("SparkyIPTV", it) }
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
        AppLog.init(ctx.filesDir, "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", version)
    }

    /** The app's settings file ("sparky", the same one as before 3.0). */
    fun store(ctx: Context): KeyValueStore {
        // The saved login can only be read with the Keystore in place.
        if (SecretBox.store == null) SecretBox.store = KeystoreSecrets
        return SharedPrefsStore(ctx.getSharedPreferences("sparky", Context.MODE_PRIVATE))
    }

    fun isOnline(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)
}

/** SharedPreferences behind the shared [KeyValueStore]. */
class SharedPrefsStore(private val sp: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, def: String?) = sp.getString(key, def)
    override fun getInt(key: String, def: Int) = sp.getInt(key, def)
    override fun getLong(key: String, def: Long) = sp.getLong(key, def)
    override fun getBoolean(key: String, def: Boolean) = sp.getBoolean(key, def)
    override fun getStringSet(key: String, def: Set<String>?): Set<String>? = sp.getStringSet(key, def)
    override fun contains(key: String) = sp.contains(key)
    override fun all(): Map<String, *> = sp.all

    override fun edit(): KeyValueStore.Editor = object : KeyValueStore.Editor {
        private val e = sp.edit()
        override fun putString(key: String, value: String?): KeyValueStore.Editor { e.putString(key, value); return this }
        override fun putInt(key: String, value: Int): KeyValueStore.Editor { e.putInt(key, value); return this }
        override fun putLong(key: String, value: Long): KeyValueStore.Editor { e.putLong(key, value); return this }
        override fun putBoolean(key: String, value: Boolean): KeyValueStore.Editor { e.putBoolean(key, value); return this }
        override fun putStringSet(key: String, value: Set<String>?): KeyValueStore.Editor { e.putStringSet(key, value); return this }
        override fun remove(key: String): KeyValueStore.Editor { e.remove(key); return this }
        override fun clear(): KeyValueStore.Editor { e.clear(); return this }
        override fun apply() = e.apply()
        override fun commit(): Boolean = e.commit()
    }
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore (unchanged from 2.8, moved here in 3.0),
 * so the stored login is useless if the app's data is copied off the device.
 */
object KeystoreSecrets : SecretStore {
    private const val ALIAS = "sparky_prefs_key"
    private const val PROVIDER = "AndroidKeyStore"
    private const val PREFIX = "v1:"

    @Volatile private var cachedKey: SecretKey? = null

    /** The Keystore key, looked up once per run (each lookup is a call into the Keystore service). */
    private fun key(): SecretKey = cachedKey ?: loadKey().also { cachedKey = it }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + B64.encode(out)
    }

    override fun decrypt(stored: String): String? = runCatching {
        if (!stored.startsWith(PREFIX)) return null
        val raw = B64.decode(stored.removePrefix(PREFIX)) ?: return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }.getOrNull()
}
