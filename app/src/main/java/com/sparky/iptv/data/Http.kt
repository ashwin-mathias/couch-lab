package com.sparky.iptv.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One OkHttp base client for the whole app (2.8). The library downloads and the player derive
 * their own timeouts from it with newBuilder(), so they share one connection pool and one set of
 * threads instead of two.
 */
object Http {
    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
