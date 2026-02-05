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

import android.annotation.SuppressLint
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEvent
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CompletedWideEventsProcessorTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventRepository: WideEventRepository = mock()
    private val wideEventSender: WideEventSender = mock()
    private val workManager: WorkManager = mock()
    private val lifecycleOwner = TestLifecycleOwner(initialState = INITIALIZED)

    private val hasCompletedEventsFlow = MutableSharedFlow<Boolean>()

    @SuppressLint("DenyListedApi")
    private val wideEventFeature: WideEventFeature =
        FakeFeatureToggleFactory
            .create(WideEventFeature::class.java)
            .apply { self().setRawStoredState(State(true)) }

    private lateinit var processor: CompletedWideEventsProcessor

    @Before
    fun setup() {
        whenever(wideEventRepository.hasCompletedWideEvents()).thenReturn(hasCompletedEventsFlow)

        processor = CompletedWideEventsProcessor(
            wideEventRepository = wideEventRepository,
            wideEventSender = wideEventSender,
            appCoroutineScope = coroutineRule.testScope,
            wideEventFeature = wideEventFeature,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            workManager = workManager,
        )

        lifecycleOwner.lifecycle.addObserver(processor)
    }

    @Test
    fun `when onCreate and hasCompletedWideEvents emits true then events are processed`() = runTest {
        val event = createTestEvent(id = 1L)
        whenever(wideEventRepository.getCompletedWideEventIds()).thenReturn(listOf(1L))
        whenever(wideEventRepository.getWideEvents(setOf(1L))).thenReturn(listOf(event))

        lifecycleOwner.currentState = CREATED
        hasCompletedEventsFlow.emit(true)
        advanceUntilIdle()

        verify(wideEventSender).sendWideEvent(event)
        verify(wideEventRepository).deleteWideEvent(1L)
    }

    @Test
    fun `when onCreate and hasCompletedWideEvents emits false then events are not processed`() = runTest {
        lifecycleOwner.currentState = CREATED
        hasCompletedEventsFlow.emit(false)
        advanceUntilIdle()

        verify(wideEventRepository, never()).getCompletedWideEventIds()
        verify(wideEventSender, never()).sendWideEvent(any())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `when feature is disabled then events are not processed on onCreate`() = runTest {
        wideEventFeature.self().setRawStoredState(State(false))

        lifecycleOwner.currentState = CREATED
        hasCompletedEventsFlow.emit(true)
        advanceUntilIdle()

        verify(wideEventRepository, never()).getCompletedWideEventIds()
        verify(wideEventSender, never()).sendWideEvent(any())
    }

    @Test
    fun `when processCompletedWideEvents called directly then events are sent and deleted`() = runTest {
        val event1 = createTestEvent(id = 1L)
        val event2 = createTestEvent(id = 2L)
        whenever(wideEventRepository.getCompletedWideEventIds()).thenReturn(listOf(1L, 2L))
        whenever(wideEventRepository.getWideEvents(setOf(1L, 2L))).thenReturn(listOf(event1, event2))

        processor.processCompletedWideEvents()

        inOrder(wideEventSender, wideEventRepository) {
            verify(wideEventSender).sendWideEvent(event1)
            verify(wideEventRepository).deleteWideEvent(1L)
            verify(wideEventSender).sendWideEvent(event2)
            verify(wideEventRepository).deleteWideEvent(2L)
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `when processCompletedWideEvents and feature is disabled then nothing is processed`() = runTest {
        wideEventFeature.self().setRawStoredState(State(false))

        processor.processCompletedWideEvents()

        verify(wideEventRepository, never()).getCompletedWideEventIds()
        verify(wideEventSender, never()).sendWideEvent(any())
    }

    @Test
    fun `when processing fails then retry worker is scheduled`() = runTest {
        whenever(wideEventRepository.getCompletedWideEventIds()).thenThrow(RuntimeException("Network error"))

        lifecycleOwner.currentState = CREATED
        hasCompletedEventsFlow.emit(true)
        advanceUntilIdle()

        verify(workManager).enqueueUniqueWork(
            eq(CompletedWideEventsProcessor.TAG_WORKER_COMPLETED_WIDE_EVENTS),
            any(),
            any<androidx.work.OneTimeWorkRequest>(),
        )
    }

    @Test
    fun `when no completed events then sender is not called`() = runTest {
        whenever(wideEventRepository.getCompletedWideEventIds()).thenReturn(emptyList())

        processor.processCompletedWideEvents()

        verify(wideEventSender, never()).sendWideEvent(any())
        verify(wideEventRepository, never()).deleteWideEvent(any())
    }

    @Test
    fun `when multiple emissions of hasCompletedWideEvents then each triggers processing`() = runTest {
        val event1 = createTestEvent(id = 1L)
        val event2 = createTestEvent(id = 2L)

        whenever(wideEventRepository.getCompletedWideEventIds())
            .thenReturn(listOf(1L))
            .thenReturn(listOf(2L))
        whenever(wideEventRepository.getWideEvents(setOf(1L))).thenReturn(listOf(event1))
        whenever(wideEventRepository.getWideEvents(setOf(2L))).thenReturn(listOf(event2))

        lifecycleOwner.currentState = CREATED

        hasCompletedEventsFlow.emit(true)
        advanceUntilIdle()

        hasCompletedEventsFlow.emit(true)
        advanceUntilIdle()

        verify(wideEventSender).sendWideEvent(event1)
        verify(wideEventSender).sendWideEvent(event2)
    }

    private fun createTestEvent(id: Long): WideEvent =
        WideEvent(
            id = id,
            name = "test_event",
            status = WideEventStatus.SUCCESS,
            steps = emptyList(),
            metadata = emptyMap(),
            flowEntryPoint = null,
            cleanupPolicy = WideEventRepository.CleanupPolicy.OnTimeout(
                duration = Duration.ofDays(7),
                status = WideEventStatus.UNKNOWN,
                metadata = emptyMap(),
            ),
            activeIntervals = emptyList(),
            createdAt = Instant.parse("2025-12-03T10:15:30.00Z"),
        )
}
