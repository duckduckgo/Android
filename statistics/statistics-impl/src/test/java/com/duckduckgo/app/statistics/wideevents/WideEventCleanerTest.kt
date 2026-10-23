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
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.CleanupDecision
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEvent
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WideEventCleanerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventRepository: WideEventRepository = mock()

    private val timeProvider =
        object : CurrentTimeProvider {
            var currentTime: Instant = Instant.parse("2025-12-03T10:15:30.00Z")

            override fun elapsedRealtime(): Long = throw UnsupportedOperationException()

            override fun currentTimeMillis(): Long = currentTime.toEpochMilli()

            override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
        }

    @SuppressLint("DenyListedApi")
    private val wideEventFeature: WideEventFeature =
        FakeFeatureToggleFactory
            .create(WideEventFeature::class.java)
            .apply { self().setRawStoredState(State(true)) }

    private val cleaner =
        WideEventCleaner(
            appCoroutineScope = coroutineRule.testScope,
            wideEventRepository = wideEventRepository,
            policyEvaluator = WideEventCleanupPolicyEvaluator(PROCESS_NAME),
            currentTimeProvider = timeProvider,
            wideEventFeature = wideEventFeature,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    private val lifecycleOwner: LifecycleOwner = mock()

    @Test
    fun `every active event is cleaned up through the repository with the evaluator's decision`() =
        runTest {
            whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(listOf(1L, 2L))

            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()

            val decideCaptor = argumentCaptor<(WideEvent) -> CleanupDecision?>()
            verify(wideEventRepository).cleanUpWideEvent(eq(1L), decideCaptor.capture())
            verify(wideEventRepository).cleanUpWideEvent(eq(2L), decideCaptor.capture())

            val decide = decideCaptor.firstValue
            assertEquals(
                CleanupDecision(status = WideEventRepository.WideEventStatus.CANCELLED, metadata = mapOf("reason" to "test")),
                decide(processStartEvent(ownerProcessName = PROCESS_NAME)),
            )
            assertNull(decide(processStartEvent(ownerProcessName = ":vpn")))
        }

    @Test
    fun `evaluation uses the time of the sweep`() =
        runTest {
            whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(listOf(1L))
            val event = timeoutEvent(createdAt = timeProvider.currentTime.minus(Duration.ofHours(1)), timeout = Duration.ofHours(1))

            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()

            val decideCaptor = argumentCaptor<(WideEvent) -> CleanupDecision?>()
            verify(wideEventRepository).cleanUpWideEvent(eq(1L), decideCaptor.capture())
            assertEquals(
                CleanupDecision(status = WideEventRepository.WideEventStatus.UNKNOWN, metadata = emptyMap()),
                decideCaptor.firstValue(event),
            )
        }

    @Test
    fun `when there are no active events nothing is cleaned up`() =
        runTest {
            whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(emptyList())

            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()

            verify(wideEventRepository).getActiveWideEventIds()
            verify(wideEventRepository, never()).cleanUpWideEvent(any(), any())
        }

    @SuppressLint("DenyListedApi")
    @Test
    fun `when feature is disabled the repository is not touched`() =
        runTest {
            wideEventFeature.self().setRawStoredState(State(false))

            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()

            verifyNoInteractions(wideEventRepository)
        }

    @Test
    fun `a failing sweep does not propagate`() =
        runTest {
            whenever(wideEventRepository.getActiveWideEventIds()).thenThrow(IllegalStateException("db unavailable"))

            cleaner.onCreate(lifecycleOwner)
            advanceUntilIdle()
        }

    private fun processStartEvent(ownerProcessName: String) =
        event(
            policy = WideEventRepository.CleanupPolicy.OnProcessStart(
                ownerProcessName = ownerProcessName,
                ignoreIfIntervalTimeoutPresent = false,
                status = WideEventRepository.WideEventStatus.CANCELLED,
                metadata = mapOf("reason" to "test"),
            ),
            createdAt = timeProvider.currentTime,
        )

    private fun timeoutEvent(
        createdAt: Instant,
        timeout: Duration,
    ) = event(
        policy = WideEventRepository.CleanupPolicy.OnTimeout(duration = timeout, status = WideEventRepository.WideEventStatus.UNKNOWN),
        createdAt = createdAt,
    )

    private fun event(
        policy: WideEventRepository.CleanupPolicy,
        createdAt: Instant,
    ) = WideEvent(
        id = 1L,
        name = "test",
        status = null,
        steps = emptyList(),
        metadata = emptyMap(),
        flowEntryPoint = null,
        cleanupPolicy = policy,
        activeIntervals = emptyList(),
        createdAt = createdAt,
        metaType = "android-test",
        metaVersion = "1.0.0",
        appVersion = "5.123.0",
    )

    private companion object {
        const val PROCESS_NAME = "main"
    }
}
