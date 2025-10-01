/*
 * Copyright (c) 2024 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.subscriptions.impl.billing

import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Retries a given block of code according to the retry policy defined in [retryPolicy].
 * The block is executed immediately and then, based on its result and the provided [retryPolicy],
 * may be retried several times with delays between attempts. The method will stop retrying and
 * return as soon as the block returns `true` or the retry limits are exhausted.
 *
 * @param retryPolicy An optional [RetryPolicy] object specifying the retry policy, including
 * the number of retries, the delay before the first retry, the maximum delay, and the delay increment factor.
 * If null, the block will be executed once without retries.
 * @param block The suspending block of code to be executed and possibly retried. This block should
 * return `true` to indicate success and avoid further retries, or `false` to indicate failure and
 * potentially trigger another retry attempt according to the retry policy.
 *
 * @throws Throwable whatever exceptions [block] may throw if it fails. Note that exceptions do not trigger retries,
 * and are instead propagated immediately to the caller of `retry()`.
 *
 * Usage example:
 * ```
 * suspend fun mightFailOperation() {
 *     // Implementation here
 * }
 *
 * val retryPolicy = RetryPolicy(
 *     retryCount = 3,
 *     initialDelay = 500.milliseconds,
 *     maxDelay = 10.seconds,
 *     delayIncrementFactor = 2.0
 * )
 *
 * retry(retryPolicy) {
 *     return try {
 *          mightFailOperation()
 *          true
 *     } catch (e: IOException) {
 *          false
 *     } catch (e: YouCantRecoverFromThisException) {
 *          true
 *     }
 * }
 * ```
 */
suspend fun retry(
    retryPolicy: RetryPolicy?,
    block: suspend () -> Boolean,
) {
    if (block() || retryPolicy == null) return

    val delayDurations = with(retryPolicy) {
        generateSequence(initialDelay) { (it * delayIncrementFactor).coerceAtMost(maxDelay) }
            .iterator()
    }

    repeat(times = retryPolicy.retryCount) {
        delay(duration = delayDurations.next())
        if (block()) return
    }
}

data class RetryPolicy(
    val retryCount: Int,
    val initialDelay: Duration,
    val maxDelay: Duration,
    val delayIncrementFactor: Double,
)
