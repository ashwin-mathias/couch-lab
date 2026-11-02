package com.sparky.iptv.data

/**
 * What the shared code needs from the device it runs on (3.0). Each app (Android TV today;
 * Windows, Mac, iPhone and iPad later) provides its own implementations.
 */

/**
 * Small key-value settings storage. The method names match Android's SharedPreferences, so the
 * Android app wraps it directly and the keys stay the same everywhere (backups move between devices).
 */
interface KeyValueStore {
    fun getString(key: String, def: String?): String?
    fun getInt(key: String, def: Int): Int
    fun getLong(key: String, def: Long): Long
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getStringSet(key: String, def: Set<String>?): Set<String>?
    fun contains(key: String): Boolean
    /** Every key and its value (String, Int, Long, Boolean or Set<String>). */
    fun all(): Map<String, *>
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putStringSet(key: String, value: Set<String>?): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        /** Saves the changes (in the background where the platform allows). */
        fun apply()
        /** Saves the changes now; false if they couldn't be written. */
        fun commit(): Boolean
    }
}

/** Encrypts small secrets (the saved login) with a key kept by the device (Android Keystore, Keychain, DPAPI). */
interface SecretStore {
    /** Returns "v1:" + Base64(iv + ciphertext). Throws if the key store is unavailable. */
    fun encrypt(plain: String): String
    /** Returns the text, or null if it can't be decrypted (for example the key was removed). */
    fun decrypt(stored: String): String?
}

/** A settings store kept in memory: for tests, and for a device without storage. */
class MemoryStore : KeyValueStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, Any>()

    override fun getString(key: String, def: String?) = map[key] as? String ?: def
    override fun getInt(key: String, def: Int) = map[key] as? Int ?: def
    override fun getLong(key: String, def: Long) = map[key] as? Long ?: def
    override fun getBoolean(key: String, def: Boolean) = map[key] as? Boolean ?: def
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, def: Set<String>?) = (map[key] as? Set<String>)?.toSet() ?: def
    override fun contains(key: String) = map.containsKey(key)
    override fun all(): Map<String, *> = HashMap(map)

    override fun edit(): KeyValueStore.Editor = object : KeyValueStore.Editor {
        private val puts = LinkedHashMap<String, Any?>()
        private var clear = false
        private fun put(key: String, value: Any?): KeyValueStore.Editor { puts[key] = value; return this }
        override fun putString(key: String, value: String?) = put(key, value)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun putStringSet(key: String, value: Set<String>?) = put(key, value?.toSet())
        override fun remove(key: String) = put(key, null)
        override fun clear(): KeyValueStore.Editor { clear = true; return this }
        override fun apply() {
            synchronized(map) {
                if (clear) map.clear()
                puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
        }
        override fun commit(): Boolean { apply(); return true }
    }
}

/** Base64 without Android's android.util.Base64 (standard alphabet, no line breaks). */
object B64 {
    fun encode(bytes: ByteArray): String = okio.ByteString.of(*bytes).base64()
    /** Null if [s] isn't valid Base64. Line breaks and spaces are ignored. */
    fun decode(s: String): ByteArray? = with(okio.ByteString.Companion) { s.decodeBase64() }?.toByteArray()
}
