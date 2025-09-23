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

package com.duckduckgo.app.statistics.wideevents

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesMultibinding(AppScope::class)
class WideEventCleaner @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val wideEventRepository: WideEventRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val wideEventFeature: WideEventFeature,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch {
            runCatching {
                if (isFeatureEnabled()) {
                    performWideEventCleanup()
                }
            }
        }
    }

    private suspend fun performWideEventCleanup() {
        wideEventRepository.getActiveWideEventIds()
            .chunked(100)
            .forEach { idsChunk ->
                wideEventRepository.getWideEvents(idsChunk.toSet())
                    .forEach { event ->
                        processWideEvent(event)
                    }
            }
    }

    private suspend fun processWideEvent(event: WideEventRepository.WideEvent) {
        when (val policy = event.cleanupPolicy) {
            is WideEventRepository.CleanupPolicy.OnProcessStart -> {
                val hasIntervalTimeouts = event.activeIntervals.any { it.timeout != null }

                if (!hasIntervalTimeouts || !policy.ignoreIfIntervalTimeoutPresent) {
                    wideEventRepository.setWideEventStatus(
                        eventId = event.id,
                        status = policy.status,
                        metadata = policy.metadata,
                    )
                    return
                }
            }

            is WideEventRepository.CleanupPolicy.OnTimeout -> {
                if (isTimeoutReached(startAt = event.createdAt, timeout = policy.duration)) {
                    wideEventRepository.setWideEventStatus(
                        eventId = event.id,
                        status = policy.status,
                        metadata = policy.metadata,
                    )
                    return
                }
            }
        }

        event.activeIntervals
            .firstOrNull { interval ->
                interval.timeout != null && isTimeoutReached(event.createdAt, interval.timeout)
            }
            ?.let { interval ->
                wideEventRepository.setWideEventStatus(
                    eventId = event.id,
                    status = WideEventRepository.WideEventStatus.UNKNOWN,
                    metadata = emptyMap(),
                )
            }
    }

    private fun isTimeoutReached(startAt: Instant, timeout: Duration): Boolean {
        val currentTime = Instant.ofEpochMilli(currentTimeProvider.currentTimeMillis())
        return timeout <= Duration.between(startAt, currentTime)
    }

    private suspend fun isFeatureEnabled(): Boolean =
        withContext(dispatcherProvider.io()) {
            wideEventFeature.self().isEnabled()
        }
}
