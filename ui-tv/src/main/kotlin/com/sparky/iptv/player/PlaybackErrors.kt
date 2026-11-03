package com.sparky.iptv.player

/** Turns a player error into a clear message with the provider's actual code. */
object PlaybackErrors {

    /** The HTTP status the provider returned, if the error came from one (null on desktop: VLC doesn't say). */
    fun httpCode(e: PError?): Int? = e?.httpCode

    /** The provider's "too many connections" codes. */
    val CONNECTION_LIMIT = setOf(429, 458, 509)

    fun isConnectionLimit(e: PError?): Boolean = httpCode(e) in CONNECTION_LIMIT

    /** Shown after the last retry. The HTTP code stays in the log, never on screen (2.9). */
    const val CONNECTION_LIMIT_MESSAGE = "Your plan allows one stream. Another device may be watching. Press OK to try again."

    /** Waits before each retry after a connection limit: 3 s, 6 s, 10 s (2.9). */
    val CONNECTION_RETRY_MS = longArrayOf(3_000, 6_000, 10_000)

    /** Errors where trying the other stream format can't help (login or connection limits). */
    fun isAccountProblem(e: PError): Boolean = httpCode(e) in setOf(401, 403, 429, 458, 509)

    /**
     * A message for the screen, e.g. "Provider refused access (403): check your subscription is active".
     * [thing] is "channel", "title" or "recording".
     */
    fun message(e: PError, thing: String): String {
        val code = httpCode(e)
        return when {
            code == 509 || code == 429 || code == 458 ->
                "Your plan allows one stream. Another device may be watching."
            code == 401 || code == 403 ->
                "Provider refused access ($code): check your subscription is active"
            code == 404 || code == 410 ->
                "Provider says this $thing isn't available ($code)"
            code != null && code >= 500 ->
                "Provider's server had a problem ($code): try again in a minute"
            code != null ->
                "Provider returned an error ($code)"
            e.kind == PError.Kind.TIMEOUT ->
                "Provider didn't respond in time: check your internet connection"
            e.kind == PError.Kind.CONNECTION ->
                "Couldn't connect to your provider: check your internet connection"
            e.kind == PError.Kind.DAMAGED ->
                "The stream from your provider is damaged (${e.errorCodeName})"
            else -> "This $thing isn't playing right now (${e.errorCodeName})"
        }
    }

    /** One log line with the code, e.g. "HTTP 509 · ERROR_CODE_IO_BAD_HTTP_STATUS". */
    fun logDetail(e: PError): String =
        (httpCode(e)?.let { "HTTP $it · " } ?: "") + e.errorCodeName
}
