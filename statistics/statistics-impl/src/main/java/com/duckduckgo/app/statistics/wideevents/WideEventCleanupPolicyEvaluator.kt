/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.statistics.wideevents

import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.CleanupDecision
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEvent
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class WideEventCleanupPolicyEvaluator @Inject constructor(
    @ProcessName private val processName: String,
) {
    /** Returns how [event] should be closed as of [now], or null if it should stay open. */
    fun evaluate(
        event: WideEvent,
        now: Instant,
    ): CleanupDecision? {
        when (val policy = event.cleanupPolicy) {
            is CleanupPolicy.OnProcessStart -> {
                val hasIntervalTimeouts = event.activeIntervals.any { it.timeout != null }
                if (policy.isOwnedByThisProcess() && (!hasIntervalTimeouts || !policy.ignoreIfIntervalTimeoutPresent)) {
                    return CleanupDecision(status = policy.status, metadata = policy.metadata)
                }
            }

            is CleanupPolicy.OnTimeout -> {
                if (hasElapsed(since = event.createdAt, timeout = policy.duration, now = now)) {
                    return CleanupDecision(status = policy.status, metadata = policy.metadata)
                }
            }
        }

        val hasExpiredInterval = event.activeIntervals.any { interval ->
            interval.timeout != null && hasElapsed(since = interval.startedAt, timeout = interval.timeout, now = now)
        }
        return if (hasExpiredInterval) CleanupDecision(status = WideEventStatus.UNKNOWN, metadata = emptyMap()) else null
    }

    // Events of other processes are cleaned up by those processes, which may still be running them.
    private fun CleanupPolicy.OnProcessStart.isOwnedByThisProcess(): Boolean = ownerProcessName == processName

    private fun hasElapsed(
        since: Instant,
        timeout: Duration,
        now: Instant,
    ): Boolean = timeout <= Duration.between(since, now)
}
