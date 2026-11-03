package com.sparky.iptv.desktop

import com.sparky.iptv.DesktopRes
import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.B64
import com.sparky.iptv.data.KeyValueStore
import com.sparky.iptv.data.SecretBox
import com.sparky.iptv.data.SecretStore
import com.sparky.iptv.setup.Net
import com.sparky.iptv.ui.platform.AppPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Windows or Mac. */
object Os {
    private val name = System.getProperty("os.name").lowercase()
    val isWindows = name.contains("win")
    val isMac = name.contains("mac")
}

/** The desktop app's [AppPlatform] (3.1). */
class DesktopPlatform(private val onExit: () -> Unit) : AppPlatform {
    /** %LOCALAPPDATA%\Sparky IPTV on Windows, ~/Library/Application Support/Sparky IPTV on Mac. */
    private val dataDir: File = when {
        Os.isWindows -> File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Sparky IPTV")
        Os.isMac -> File(System.getProperty("user.home"), "Library/Application Support/Sparky IPTV")
        else -> File(System.getProperty("user.home"), ".sparky-iptv")
    }.also { it.mkdirs() }

    override val filesDir: File = File(dataDir, "files").also { it.mkdirs() }
    override val cacheDir: File = File(dataDir, "cache").also { it.mkdirs() }
    override val store: KeyValueStore = FileStore(File(dataDir, "settings.json"))
    override fun isOnline() = true

    override val versionName = DesktopRes.VERSION
    override val deviceLine: String = when {
        Os.isWindows -> "${System.getProperty("os.name")} · ${System.getProperty("os.arch")}"
        Os.isMac -> "macOS ${System.getProperty("os.version")} · ${System.getProperty("os.arch")}"
        else -> System.getProperty("os.name")
    }
    override val deviceName: String =
        (System.getenv("COMPUTERNAME") ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull() ?: "Your computer")
            .removeSuffix(".local")

    override fun localIp(): String? = Net.fromInterfaces()

    override fun readAsset(name: String): String? =
        javaClass.getResourceAsStream("/assets/$name")?.bufferedReader()?.use { it.readText() }

    override fun exit() = onExit()

    // The Microsoft Store (and later a notarized download) updates the desktop app.
    override val selfUpdates = false
    override fun installedBuild() = DesktopRes.BUILD
    override fun updateFile() = File(cacheDir, "update.bin")
    override fun canInstall() = false
    override fun openInstallPermission() {}
    override fun launchInstaller() {}

    companion object {
        /** Logs, the login key and the settings, before anything reads them. */
        fun init(p: DesktopPlatform) {
            SecretBox.store = if (Os.isWindows) DpapiSecrets else KeychainSecrets(File(p.dataDir, "key"))
            AppLog.sink = { println(it) }
            AppLog.init(p.filesDir, p.deviceLine, p.versionName)
        }
    }
}

/**
 * Settings in one JSON file (3.1), with the same keys and value types as Android's
 * SharedPreferences, so backups move between a TV and a computer.
 */
class FileStore(private val file: File) : KeyValueStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, Any>()

    init {
        runCatching {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                for (k in o.keys()) {
                    val e = o.getJSONObject(k)
                    val v: Any = when (e.getString("t")) {
                        "s" -> e.getString("v")
                        "i" -> e.getInt("v")
                        "l" -> e.getLong("v")
                        "b" -> e.getBoolean("v")
                        else -> e.getJSONArray("v").let { a -> List(a.length()) { a.getString(it) }.toSet() }
                    }
                    map[k] = v
                }
            }
        }.onFailure { AppLog.e("Settings file unreadable", it) }
    }

    @Synchronized
    private fun save() {
        val o = JSONObject()
        for ((k, v) in map) {
            o.put(k, when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            })
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    override fun getString(key: String, def: String?) = map[key] as? String ?: def
    override fun getInt(key: String, def: Int) = (map[key] as? Number)?.toInt() ?: def
    override fun getLong(key: String, def: Long) = (map[key] as? Number)?.toLong() ?: def
    override fun getBoolean(key: String, def: Boolean) = map[key] as? Boolean ?: def
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, def: Set<String>?) = (map[key] as? Set<String>)?.toSet() ?: def
    override fun contains(key: String) = map.containsKey(key)
    override fun all(): Map<String, *> = HashMap(map)

    override fun edit(): KeyValueStore.Editor = object : KeyValueStore.Editor {
        private val puts = LinkedHashMap<String, Any?>()
        private var clear = false
        private fun put(k: String, v: Any?): KeyValueStore.Editor { puts[k] = v; return this }
        override fun putString(key: String, value: String?) = put(key, value)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun putStringSet(key: String, value: Set<String>?) = put(key, value?.toSet())
        override fun remove(key: String) = put(key, null)
        override fun clear(): KeyValueStore.Editor { clear = true; return this }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            synchronized(this@FileStore) {
                if (clear) map.clear()
                puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
            return runCatching { save() }.onFailure { AppLog.e("Couldn't save settings", it) }.isSuccess
        }
    }
}

/** Windows: the login is protected with DPAPI, so only this Windows user on this PC can read it. */
object DpapiSecrets : SecretStore {
    private const val PREFIX = "v1:"
    override fun encrypt(plain: String): String =
        PREFIX + B64.encode(com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(plain.toByteArray(Charsets.UTF_8)))

    override fun decrypt(stored: String): String? = runCatching {
        if (!stored.startsWith(PREFIX)) return null
        val raw = B64.decode(stored.removePrefix(PREFIX)) ?: return null
        String(com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(raw), Charsets.UTF_8)
    }.getOrNull()
}

/**
 * Mac: AES-256-GCM with a key kept in the login Keychain (through the `security` tool). If the
 * Keychain can't be used, the key is kept in a file only this user can read.
 */
class KeychainSecrets(private val keyFile: File) : SecretStore {
    private val prefix = "v1:"
    @Volatile private var cached: ByteArray? = null

    private fun run(vararg cmd: String): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0) out else null
    }.getOrNull()

    private fun key(): ByteArray {
        cached?.let { return it }
        val found = run("/usr/bin/security", "find-generic-password", "-s", "Sparky IPTV", "-a", "login-key", "-w")
            ?: keyFile.takeIf { it.exists() }?.readText()?.trim()
        val k = found?.let { B64.decode(it) }?.takeIf { it.size == 32 } ?: ByteArray(32).also { SecureRandom().nextBytes(it) }.also { k ->
            val b64 = B64.encode(k)
            val ok = run("/usr/bin/security", "add-generic-password", "-U", "-s", "Sparky IPTV", "-a", "login-key", "-w", b64) != null
            if (!ok) {
                keyFile.writeText(b64)
                keyFile.setReadable(false, false); keyFile.setReadable(true, true)
            }
        }
        cached = k
        return k
    }

    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(), "AES"))
        return prefix + B64.encode(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    override fun decrypt(stored: String): String? = runCatching {
        if (!stored.startsWith(prefix)) return null
        val raw = B64.decode(stored.removePrefix(prefix)) ?: return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(), "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }.getOrNull()
}
