package com.sparky.iptv.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

/** Turns a player error into a clear message with the provider's actual code. */
object PlaybackErrors {

    /** The HTTP status the provider returned, if the error came from one. */
    @OptIn(UnstableApi::class)
    fun httpCode(e: Throwable?): Int? {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 8) {
            if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode
            t = t.cause
            depth++
        }
        return null
    }

    /** Errors where trying the other stream format can't help (login or connection limits). */
    fun isAccountProblem(e: PlaybackException): Boolean = httpCode(e) in setOf(401, 403, 429, 458, 509)

    /**
     * A message for the screen, e.g. "Provider says too many connections (509): stop playback on another device".
     * [thing] is "channel", "title" or "recording".
     */
    fun message(e: PlaybackException, thing: String): String {
        val code = httpCode(e)
        return when {
            code == 509 || code == 429 || code == 458 ->
                "Provider says too many connections ($code): stop playback on another device"
            code == 401 || code == 403 ->
                "Provider refused access ($code): check your subscription is active"
            code == 404 || code == 410 ->
                "Provider says this $thing isn't available ($code)"
            code != null && code >= 500 ->
                "Provider's server had a problem ($code): try again in a minute"
            code != null ->
                "Provider returned an error ($code)"
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Provider didn't respond in time: check your internet connection"
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "Couldn't connect to your provider: check your internet connection"
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "The stream from your provider is damaged (${e.errorCodeName})"
            else -> "This $thing isn't playing right now (${e.errorCodeName})"
        }
    }

    /** One log line with the code, e.g. "HTTP 509 · ERROR_CODE_IO_BAD_HTTP_STATUS". */
    fun logDetail(e: PlaybackException): String =
        (httpCode(e)?.let { "HTTP $it · " } ?: "") + e.errorCodeName
}
