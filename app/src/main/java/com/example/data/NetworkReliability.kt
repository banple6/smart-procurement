package com.smartprocurement.internal.data

import kotlinx.coroutines.delay
import java.util.UUID


object IdempotencyKeys {
    fun newKey(): String = UUID.randomUUID().toString()

    fun header(key: String): Map<String, String> = mapOf("Idempotency-Key" to key)
}


fun nextBackoffDelayMillis(attempt: Int, jitterFactor: Double = 0.5 + Math.random()): Long {
    val base = minOf(30_000L, 500L * (1L shl attempt.coerceAtMost(6)))
    return minOf(30_000L, (base * jitterFactor).toLong())
}


suspend fun <T> retryIdempotentWithBackoff(
    maxAttempts: Int = 5,
    jitterFactor: () -> Double = { 0.5 + Math.random() },
    delayFn: suspend (Long) -> Unit = { delay(it) },
    block: suspend () -> T,
): T {
    require(maxAttempts > 0) { "maxAttempts must be greater than 0" }
    var lastError: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (error: Throwable) {
            lastError = error
            if (attempt == maxAttempts - 1) throw error
            delayFn(nextBackoffDelayMillis(attempt, jitterFactor()))
        }
    }
    throw lastError ?: IllegalStateException("retry failed without an error")
}
