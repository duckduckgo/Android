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

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.CleanupDecision
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEvent
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Instant
import java.time.LocalDateTime
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WideEventCleanerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val repository = FakeWideEventRepository()

    private val timeProvider =
        object : CurrentTimeProvider {
            override fun elapsedRealtime(): Long = throw UnsupportedOperationException()

            override fun currentTimeMillis(): Long = NOW.toEpochMilli()

            override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
        }

    private val wideEventFeature: WideEventFeature = FakeFeatureToggleFactory.create(WideEventFeature::class.java)

    private val pixel: Pixel = mock()

    private val cleaner =
        WideEventCleaner(
            wideEventRepository = repository,
            policyEvaluator = WideEventCleanupPolicyEvaluator(PROCESS_NAME),
            wideEventFeature = wideEventFeature,
            currentTimeProvider = timeProvider,
            pixel = pixel,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

    private val lifecycleOwner: LifecycleOwner = mock()

    @Test
    fun `main process start runs the initial cleanup sweep`() =
        runTest {
            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()

            assertEquals(1, repository.sweeps)
        }

    @SuppressLint("DenyListedApi")
    @Test
    fun `main process start runs no sweep when wide events are disabled`() =
        runTest {
            wideEventFeature.self().setRawStoredState(State(false))

            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()

            assertEquals(0, repository.sweeps)
        }

    @Test
    fun `a failing startup sweep does not propagate and a client can retry it`() =
        runTest {
            var failing = true
            repository.activeWideEventIds = { if (failing) throw IllegalStateException("db unavailable") else emptyList() }

            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()

            failing = false
            cleaner.ensureInitialized()
            assertEquals(2, repository.sweeps)
        }

    @Test
    fun `main process start and client operations share the same initialization`() =
        runTest {
            val client =
                WideEventClientImpl(
                    wideEventRepository = repository,
                    wideEventFeature = lazy { wideEventFeature },
                    dispatcherProvider = coroutineRule.testDispatcherProvider,
                    random = Random.Default,
                    processName = PROCESS_NAME,
                    wideEventCleaner = cleaner,
                )

            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()
            client.flowStart(name = "flow")
            client.getFlowIds(name = "flow")

            assertEquals(1, repository.sweeps)
        }

    @Test
    fun `concurrent callers share one initial sweep`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.activeWideEventIds = {
                gate.await()
                emptyList()
            }

            val first = launch { cleaner.ensureInitialized() }
            val second = launch { cleaner.ensureInitialized() }
            advanceUntilIdle()
            assertEquals(1, repository.sweeps)

            gate.complete(Unit)
            first.join()
            second.join()

            assertEquals(1, repository.sweeps)
        }

    @SuppressLint("DenyListedApi")
    @Test
    fun `ensureInitialized does nothing when process-scoped cleanup is disabled`() =
        runTest {
            wideEventFeature.processScopedCleanup().setRawStoredState(State(false))

            cleaner.ensureInitialized()

            assertEquals(0, repository.sweeps)
        }

    @SuppressLint("DenyListedApi")
    @Test
    fun `initialize sweeps even when process-scoped cleanup is disabled`() =
        runTest {
            wideEventFeature.processScopedCleanup().setRawStoredState(State(false))

            cleaner.initialize()

            assertEquals(1, repository.sweeps)
        }

    @Test
    fun `initialization is skipped once a sweep succeeded`() =
        runTest {
            cleaner.ensureInitialized()
            cleaner.ensureInitialized()

            assertEquals(1, repository.sweeps)
        }

    @Test
    fun `failed initialization is reported to callers and retried by the next call`() =
        runTest {
            var failing = true
            repository.activeWideEventIds = { if (failing) throw IllegalStateException("db unavailable") else emptyList() }

            val failure = runCatching { cleaner.ensureInitialized() }
            assertTrue(failure.isFailure)

            failing = false
            cleaner.ensureInitialized()
            cleaner.ensureInitialized()

            assertEquals(2, repository.sweeps)
        }

    @Test
    fun `callers waiting on a failed sweep each retry it`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.activeWideEventIds = {
                gate.await()
                throw IllegalStateException("db unavailable")
            }

            val callers = List(3) { launch { runCatching { cleaner.ensureInitialized() } } }
            advanceUntilIdle()
            assertEquals(1, repository.sweeps)

            gate.complete(Unit)
            callers.forEach { it.join() }

            assertEquals(3, repository.sweeps)
        }

    @Test
    fun `failed sweep enqueues count and daily failure pixels`() =
        runTest {
            repository.activeWideEventIds = { throw IllegalStateException("db unavailable") }

            runCatching { cleaner.ensureInitialized() }

            verify(pixel).enqueueFire(pixelName = "wide_events_cleanup_failure_c")
            verify(pixel).enqueueFire(pixelName = "wide_events_cleanup_failure_d", type = Daily())
            verifyNoMoreInteractions(pixel)
        }

    @Test
    fun `successful sweep fires no pixel even when an event is skipped`() =
        runTest {
            repository.activeWideEventIds = { listOf(1L) }
            repository.onCleanUp = { _, _ -> throw IllegalStateException("unreadable row") }

            cleaner.ensureInitialized()

            verifyNoInteractions(pixel)
        }

    @Test
    fun `cancelling a caller does not cancel the shared sweep`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.activeWideEventIds = {
                gate.await()
                emptyList()
            }

            val caller = launch { cleaner.ensureInitialized() }
            advanceUntilIdle()
            assertEquals(1, repository.sweeps)
            caller.cancelAndJoin()
            gate.complete(Unit)
            advanceUntilIdle()

            cleaner.ensureInitialized()

            assertEquals(1, repository.sweeps)
        }

    @Test
    fun `a failing event is skipped and the remaining events are still cleaned up`() =
        runTest {
            val cleanedUp = mutableListOf<Long>()
            repository.activeWideEventIds = { listOf(1L, 2L, 3L) }
            repository.onCleanUp = { eventId, _ ->
                if (eventId == 2L) throw IllegalStateException("unreadable row")
                cleanedUp += eventId
            }

            cleaner.ensureInitialized()

            assertEquals(listOf(1L, 3L), cleanedUp)
            cleaner.ensureInitialized()
            assertEquals(1, repository.sweeps)
        }

    @Test
    fun `initial sweep closes process-start events owned by this process only`() =
        runTest {
            val decide = captureDecision { cleaner.ensureInitialized() }

            assertEquals(PROCESS_START_DECISION, decide(processStartEvent(ownerProcessName = PROCESS_NAME)))
            assertNull(decide(processStartEvent(ownerProcessName = ":vpn")))
        }

    @SuppressLint("DenyListedApi")
    @Test
    fun `initial sweep closes process-start events of every process when process-scoped cleanup is disabled`() =
        runTest {
            wideEventFeature.processScopedCleanup().setRawStoredState(State(false))

            val decide = captureDecision { cleaner.initialize() }

            assertEquals(PROCESS_START_DECISION, decide(processStartEvent(ownerProcessName = PROCESS_NAME)))
            assertEquals(PROCESS_START_DECISION, decide(processStartEvent(ownerProcessName = ":vpn")))
        }

    private suspend fun captureDecision(sweep: suspend () -> Unit): (WideEvent) -> CleanupDecision? {
        var decide: ((WideEvent) -> CleanupDecision?)? = null
        repository.activeWideEventIds = { listOf(1L) }
        repository.onCleanUp = { _, decision -> decide = decision }

        sweep()

        return checkNotNull(decide)
    }

    private fun processStartEvent(ownerProcessName: String) =
        WideEvent(
            id = 1L,
            name = "test",
            status = null,
            steps = emptyList(),
            metadata = emptyMap(),
            flowEntryPoint = null,
            cleanupPolicy = WideEventRepository.CleanupPolicy.OnProcessStart(
                ownerProcessName = ownerProcessName,
                ignoreIfIntervalTimeoutPresent = false,
                status = PROCESS_START_DECISION.status,
                metadata = PROCESS_START_DECISION.metadata,
            ),
            activeIntervals = emptyList(),
            createdAt = NOW,
            metaType = "android-test",
            metaVersion = "1.0.0",
            appVersion = "5.123.0",
        )

    private companion object {
        val NOW: Instant = Instant.parse("2025-12-03T10:15:30.00Z")
        const val PROCESS_NAME = "main"
        val PROCESS_START_DECISION = CleanupDecision(status = WideEventRepository.WideEventStatus.CANCELLED, metadata = mapOf("reason" to "test"))
    }
}
