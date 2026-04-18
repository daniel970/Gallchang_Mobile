package com.dcinside.crawler.mobile

import kotlinx.coroutines.delay

class RateLimiter(private val baseDelayMillis: Long) {
    private var consecutiveFailures = 0

    suspend fun waitBeforeNext() {
        val shift = consecutiveFailures.coerceAtMost(4)
        val backoff = baseDelayMillis * (1L shl shift)
        delay(backoff)
    }

    fun onSuccess() {
        consecutiveFailures = 0
    }

    fun onFailure() {
        consecutiveFailures++
    }
}
