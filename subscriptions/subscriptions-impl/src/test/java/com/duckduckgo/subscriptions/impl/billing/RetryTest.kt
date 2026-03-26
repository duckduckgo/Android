package com.duckduckgo.subscriptions.impl.billing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTest {

    @Test
    fun `when block returns false and config is null then does not retry`() = runTest {
        var attemptCount = 0

        retry(retryPolicy = null) {
            attemptCount++
            true
        }

        assertEquals(1, attemptCount)
    }

    @Test
    fun `when block returns true then does not retry`() = runTest {
        val retryPolicy = RetryPolicy(
            retryCount = 10,
            initialDelay = 1.seconds,
            maxDelay = 1.seconds,
            delayIncrementFactor = 2.0,
        )

        var attemptCount = 0

        retry(retryPolicy) {
            attemptCount++
            true
        }

        assertEquals(1, attemptCount)
    }

    @Test
    fun `when block returns false then does retry`() = runTest {
        val retryPolicy = RetryPolicy(
            retryCount = 10,
            initialDelay = 1.seconds,
            maxDelay = 1.seconds,
            delayIncrementFactor = 2.0,
        )

        var attemptCount = 0

        retry(retryPolicy) {
            attemptCount++
            false
        }

        assertEquals(1 + retryPolicy.retryCount, attemptCount)
    }

    @Test
    fun `when block returns true on retry then stops retrying`() = runTest {
        val retryPolicy = RetryPolicy(
            retryCount = 10,
            initialDelay = 1.seconds,
            maxDelay = 1.seconds,
            delayIncrementFactor = 2.0,
        )

        var attemptCount = 0

        retry(retryPolicy) {
            attemptCount++
            attemptCount >= 3 // return true on 3rd attempt
        }

        assertEquals(3, attemptCount)
    }

    @Test
    fun `when about to retry then delays incrementally`() = runTest {
        val retryPolicy = RetryPolicy(
            retryCount = 8,
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            delayIncrementFactor = 2.0,
        )

        var attemptCount = 0
        val attemptTime = mutableListOf<Duration>()

        retry(retryPolicy) {
            attemptCount++
            attemptTime += currentTime.toDuration(MILLISECONDS)
            false
        }

        assertEquals(1 + retryPolicy.retryCount, attemptCount)

        // First attempt should happen immediately
        assertEquals(0.seconds, attemptTime.first())

        // There should be delays between subsequent attempts.
        assertEquals(
            listOf(1.seconds, 2.seconds, 4.seconds, 8.seconds, 16.seconds, 30.seconds, 30.seconds, 30.seconds),
            attemptTime.zipWithNext { previousAttemptAt, attemptAt -> attemptAt - previousAttemptAt },
        )
    }
}
