package com.smartprocurement.internal

import com.smartprocurement.internal.data.IdempotencyKeys
import com.smartprocurement.internal.data.nextBackoffDelayMillis
import com.smartprocurement.internal.data.retryIdempotentWithBackoff
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkReliabilityTest {
    @Test
    fun idempotency_header_uses_the_same_key_as_order_body() {
        val key = IdempotencyKeys.newKey()
        val headers = IdempotencyKeys.header(key)

        assertTrue(key.length >= 32)
        assertEquals(key, headers["Idempotency-Key"])
    }

    @Test
    fun exponential_backoff_is_capped_and_accepts_jitter_factor() {
        assertEquals(250L, nextBackoffDelayMillis(attempt = 0, jitterFactor = 0.5))
        assertEquals(750L, nextBackoffDelayMillis(attempt = 0, jitterFactor = 1.5))
        assertEquals(30_000L, nextBackoffDelayMillis(attempt = 10, jitterFactor = 1.5))
    }

    @Test
    fun retry_idempotent_operations_until_success() = runTest {
        var attempts = 0
        val delays = mutableListOf<Long>()

        val result = retryIdempotentWithBackoff(
            maxAttempts = 3,
            jitterFactor = { 1.0 },
            delayFn = { delays += it },
        ) {
            attempts += 1
            if (attempts < 3) throw IOException("temporary")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
        assertEquals(listOf(500L, 1000L), delays)
    }
}
