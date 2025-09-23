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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WideEventCleanerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventRepository: WideEventRepository = mock()

    private val timeProvider = object : CurrentTimeProvider {
        var currentTime: Instant = Instant.parse("2025-12-03T10:15:30.00Z")

        override fun elapsedRealtime(): Long = throw UnsupportedOperationException()
        override fun currentTimeMillis(): Long = currentTime.toEpochMilli()
        override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
    }

    private val cleaner = WideEventCleaner(
        appCoroutineScope = coroutineRule.testScope,
        wideEventRepository = wideEventRepository,
        currentTimeProvider = timeProvider,
    )
    private val lifecycleOwner: LifecycleOwner = mock()

    @Test
    fun `OnProcessStart without active interval timeouts sets status`() = runTest {
        val event = WideEventRepository.WideEvent(
            id = 1L,
            name = "test",
            status = null,
            steps = emptyList(),
            metadata = emptyMap(),
            flowEntryPoint = null,
            cleanupPolicy = WideEventRepository.CleanupPolicy.OnProcessStart(
                ignoreIfIntervalTimeoutPresent = false,
                status = WideEventRepository.WideEventStatus.CANCELLED,
                metadata = mapOf("reason" to "test"),
            ),
            activeIntervals = emptyList(),
            createdAt = timeProvider.currentTime,
        )
        whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(listOf(1L))
        whenever(wideEventRepository.getWideEvents(setOf(1L))).thenReturn(listOf(event))

        cleaner.onCreate(lifecycleOwner)
        advanceUntilIdle()

        verify(wideEventRepository).setWideEventStatus(
            eventId = 1L,
            status = WideEventRepository.WideEventStatus.CANCELLED,
            metadata = mapOf("reason" to "test"),
        )
    }

    @Test
    fun `OnProcessStart with ignoreIfIntervalTimeoutPresent true and active interval timeouts does not set status`() = runTest {
        val start = timeProvider.currentTime.minusSeconds(60)
        val event = WideEventRepository.WideEvent(
            id = 6L,
            name = "test",
            status = null,
            steps = emptyList(),
            metadata = emptyMap(),
            flowEntryPoint = null,
            cleanupPolicy = WideEventRepository.CleanupPolicy.OnProcessStart(
                ignoreIfIntervalTimeoutPresent = true,
                status = WideEventRepository.WideEventStatus.CANCELLED,
                metadata = mapOf("reason" to "test"),
            ),
            activeIntervals = listOf(
                WideEventRepository.WideEventInterval(
                    name = "interval",
                    startedAt = start,
                    timeout = Duration.ofMinutes(5),
                ),
            ),
            createdAt = start,
        )
        whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(listOf(6L))
        whenever(wideEventRepository.getWideEvents(setOf(6L))).thenReturn(listOf(event))

        cleaner.onCreate(lifecycleOwner)
        advanceUntilIdle()

        // Should skip because ignoreIfIntervalTimeoutPresent = true
        verify(wideEventRepository, never()).setWideEventStatus(any(), any(), any())
    }

    @Test
    fun `OnProcessStart with ignoreIfIntervalTimeoutPresent false and active interval timeouts sets status`() = runTest {
        val start = timeProvider.currentTime.minusSeconds(60)
        val event = WideEventRepository.WideEvent(
            id = 7L,
            name = "test",
            status = null,
            steps = emptyList(),
            metadata = emptyMap(),
            flowEntryPoint = null,
            cleanupPolicy = WideEventRepository.CleanupPolicy.OnProcessStart(
                ignoreIfIntervalTimeoutPresent = false,
                status = WideEventRepository.WideEventStatus.CANCELLED,
                metadata = mapOf("reason" to "test"),
            ),
            activeIntervals = listOf(
                WideEventRepository.WideEventInterval(
                    name = "interval",
                    startedAt = start,
                    timeout = Duration.ofMinutes(5),
                ),
            ),
            createdAt = start,
        )
        whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(listOf(7L))
        whenever(wideEventRepository.getWideEvents(setOf(7L))).thenReturn(listOf(event))

        cleaner.onCreate(lifecycleOwner)
        advanceUntilIdle()

        verify(wideEventRepository).setWideEventStatus(
            eventId = 7L,
            status = WideEventRepository.WideEventStatus.CANCELLED,
            metadata = mapOf("reason" to "test"),
        )
    }

    @Test
    fun `OnTimeout after timeout elapsed sets status`() = runTest {
        val timeout = Duration.ofMinutes(10)
        val createdAt = timeProvider.currentTime - Duration.ofMinutes(11)

        val event = WideEventRepository.WideEvent(
            id = 3L,
            name = "test",
            status = null,
            steps = emptyList(),
            metadata = emptyMap(),
            flowEntryPoint = null,
            cleanupPolicy = WideEventRepository.CleanupPolicy.OnTimeout(
                duration = timeout,
                status = WideEventRepository.WideEventStatus.FAILURE,
                metadata = mapOf("cleanup" to "timeout"),
            ),
            activeIntervals = emptyList(),
            createdAt = createdAt,
        )
        whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(listOf(3L))
        whenever(wideEventRepository.getWideEvents(setOf(3L))).thenReturn(listOf(event))

        cleaner.onCreate(lifecycleOwner)
        advanceUntilIdle()

        verify(wideEventRepository).setWideEventStatus(
            eventId = 3L,
            status = WideEventRepository.WideEventStatus.FAILURE,
            metadata = mapOf("cleanup" to "timeout"),
        )
    }

    @Test
    fun `Active interval timeout sets UNKNOWN status`() = runTest {
        val start = timeProvider.currentTime.minusSeconds(600)
        val event = WideEventRepository.WideEvent(
            id = 4L,
            name = "test",
            status = null,
            steps = emptyList(),
            metadata = emptyMap(),
            flowEntryPoint = null,
            cleanupPolicy = WideEventRepository.CleanupPolicy.OnTimeout(
                duration = Duration.ofHours(1),
                status = WideEventRepository.WideEventStatus.UNKNOWN,
                metadata = emptyMap(),
            ),
            activeIntervals = listOf(
                WideEventRepository.WideEventInterval(
                    name = "interval",
                    startedAt = start,
                    timeout = Duration.ofMinutes(5),
                ),
            ),
            createdAt = start,
        )
        whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(listOf(4L))
        whenever(wideEventRepository.getWideEvents(setOf(4L))).thenReturn(listOf(event))

        cleaner.onCreate(lifecycleOwner)
        advanceUntilIdle()

        verify(wideEventRepository).setWideEventStatus(
            eventId = 4L,
            status = WideEventRepository.WideEventStatus.UNKNOWN,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `Cleanup policy conditions are not met and no interval timeout does nothing`() = runTest {
        val event = WideEventRepository.WideEvent(
            id = 5L,
            name = "test",
            status = null,
            steps = emptyList(),
            metadata = emptyMap(),
            flowEntryPoint = null,
            cleanupPolicy = WideEventRepository.CleanupPolicy.OnTimeout(
                duration = Duration.ofHours(1),
                status = WideEventRepository.WideEventStatus.UNKNOWN,
                metadata = emptyMap(),
            ),
            activeIntervals = emptyList(),
            createdAt = timeProvider.currentTime,
        )
        whenever(wideEventRepository.getActiveWideEventIds()).thenReturn(listOf(5L))
        whenever(wideEventRepository.getWideEvents(setOf(5L))).thenReturn(listOf(event))

        cleaner.onCreate(lifecycleOwner)
        advanceUntilIdle()

        verify(wideEventRepository, never()).setWideEventStatus(any(), any(), any())
    }
}
