/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.integration.fakes

import com.duckduckgo.common.utils.CurrentTimeProvider
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A controllable time provider for integration tests.
 * Allows tests to advance time without waiting.
 *
 * @param initialTimeMs The initial time in milliseconds since epoch
 */
class FakeCurrentTimeProvider(
    initialTimeMs: Long = System.currentTimeMillis(),
) : CurrentTimeProvider {

    private var currentTimeMs: Long = initialTimeMs
    private var elapsedRealtimeMs: Long = 0L

    override fun elapsedRealtime(): Long = elapsedRealtimeMs

    override fun currentTimeMillis(): Long = currentTimeMs

    override fun localDateTimeNow(): LocalDateTime {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(currentTimeMs),
            ZoneId.systemDefault(),
        )
    }

    /**
     * Advances the current time by the specified number of hours.
     */
    fun advanceByHours(hours: Long) {
        val millis = hours * 60 * 60 * 1000
        currentTimeMs += millis
        elapsedRealtimeMs += millis
    }
}
