package com.sparky.iptv.data

import java.security.SecureRandom

/**
 * Encrypts small secrets (the saved login) with a key kept by the device, so the stored text is
 * useless if the app's data is copied off it. The app sets [store] at start (3.0: Android Keystore).
 */
object SecretBox {
    @Volatile var store: SecretStore? = null

    /** Returns "v1:" + Base64(iv + ciphertext). Throws if no key store is available. */
    fun encrypt(plain: String): String = (store ?: error("No secret store on this device")).encrypt(plain)

    /** Returns the text, or null if it can't be decrypted (for example the key was removed). */
    fun decrypt(stored: String): String? = runCatching { store?.decrypt(stored) }.getOrNull()

    /** A random one-time token for the phone web pages: 128 bits as hex. */
    fun newToken(): String {
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Compares two strings in constant time. */
    fun same(a: String?, b: String): Boolean =
        a != null && java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
