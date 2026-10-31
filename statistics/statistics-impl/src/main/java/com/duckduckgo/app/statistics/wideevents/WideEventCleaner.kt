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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.StatisticsPixelName
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.time.Instant
import javax.inject.Inject

/**
 * Runs the cleanup sweep that initializes wide events for this process. Nothing recorded in this process can predate the sweep as long
 * as client operations wait for it, so any OnProcessStart event owned by this process that the sweep finds was left by a previous run.
 *
 * The main process starts its sweep as soon as it is created; other processes start theirs on their first wide-event operation.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class WideEventCleaner @Inject constructor(
    private val wideEventRepository: WideEventRepository,
    private val policyEvaluator: WideEventCleanupPolicyEvaluator,
    private val wideEventFeature: WideEventFeature,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {
    private val sweepMutex = Mutex()

    @Volatile
    private var initialized = false

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch {
            // The lifecycle has nowhere to report a failed sweep; the first client operation retries it and sees the failure.
            runCatching { if (isWideEventsEnabled()) initialize() }
        }
    }

    suspend fun ensureInitialized() {
        if (isProcessScopedCleanupEnabled()) initialize()
    }

    suspend fun initialize() {
        if (initialized) return

        appCoroutineScope
            .async {
                sweepMutex.withLock {
                    if (initialized) return@async Result.success(Unit)
                    sweepResult().also { if (it.isSuccess) initialized = true }
                }
            }.await()
            .getOrThrow()
    }

    // Wrapped in a Result so that a failed sweep cannot fail the application scope.
    private suspend fun sweepResult(): Result<Unit> =
        try {
            sweep(processScopedCleanup = isProcessScopedCleanupEnabled())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Until a sweep succeeds, client operations in this process fail, so a persistent failure needs to be visible.
            logcat(WARN) { "Wide event cleanup sweep failed: ${e.asLog()}" }
            pixel.enqueueFire(pixelName = StatisticsPixelName.WIDE_EVENTS_CLEANUP_FAILURE_COUNT.pixelName)
            pixel.enqueueFire(pixelName = StatisticsPixelName.WIDE_EVENTS_CLEANUP_FAILURE_DAILY.pixelName, type = Daily())
            Result.failure(e)
        }

    private suspend fun isWideEventsEnabled(): Boolean = withContext(dispatcherProvider.io()) { wideEventFeature.self().isEnabled() }

    private suspend fun isProcessScopedCleanupEnabled(): Boolean =
        withContext(dispatcherProvider.io()) { wideEventFeature.processScopedCleanup().isEnabled() }

    private suspend fun sweep(processScopedCleanup: Boolean) {
        val now = Instant.ofEpochMilli(currentTimeProvider.currentTimeMillis())

        wideEventRepository.getActiveWideEventIds().forEach { eventId ->
            try {
                wideEventRepository.cleanUpWideEvent(eventId) { event ->
                    policyEvaluator.evaluate(event, now, processScopedCleanup)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One unreadable row must not keep this process from recording any wide events.
                logcat(WARN) { "Skipping cleanup of wide event $eventId: ${e.asLog()}" }
            }
        }
    }
}
