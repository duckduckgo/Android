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

package com.duckduckgo.pir.impl.scan

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.email.PirEmailConfirmationRemoteWorker
import com.duckduckgo.pir.impl.pixels.PirBackgroundScanStatsWorker
import com.duckduckgo.pir.impl.pixels.PirCustomStatsWorker
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.db.EventType
import com.duckduckgo.pir.impl.store.db.PirEventLog
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealPirScanSchedulerTest {
    private lateinit var testee: RealPirScanScheduler
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockWorkManager: WorkManager = mock()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPirPixelSender: PirPixelSender = mock()
    private val mockEventsRepository: PirEventsRepository = mock()
    private val mockContext: Context = mock()

    @Before
    fun setUp() {
        whenever(mockAppBuildConfig.applicationId).thenReturn("com.test.app")
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(12345678L)

        testee = RealPirScanScheduler(
            appBuildConfig = mockAppBuildConfig,
            workManager = mockWorkManager,
            coroutineScope = testScope,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
            eventsRepository = mockEventsRepository,
        )
    }

    @Test
    fun whenScheduleScansThenSchedulesAllThreeWorkers() {
        testee.scheduleScans()

        val tagCaptor = argumentCaptor<String>()
        verify(mockWorkManager, times(4)).enqueueUniquePeriodicWork(
            tagCaptor.capture(),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            any<PeriodicWorkRequest>(),
        )

        val tags = tagCaptor.allValues
        assertTrue(tags.contains(PirScheduledScanRemoteWorker.TAG_SCHEDULED_SCAN))
        assertTrue(tags.contains(PirEmailConfirmationRemoteWorker.TAG_EMAIL_CONFIRMATION))
        assertTrue(tags.contains(PirCustomStatsWorker.TAG_PIR_RECURRING_CUSTOM_STATS))
        assertTrue(tags.contains(PirBackgroundScanStatsWorker.TAG_PIR_BACKGROUND_STATS_DAILY))
    }

    @Test
    fun whenScheduleScansThenReportsScheduledScanPixel() {
        testee.scheduleScans()

        verify(mockPirPixelSender).reportScheduledScanScheduled()
    }

    @Test
    fun whenScheduleScansThenSavesScheduledScanEventLog() = runTest {
        testee.scheduleScans()

        testDispatcher.scheduler.advanceUntilIdle()

        val eventCaptor = argumentCaptor<PirEventLog>()
        verify(mockEventsRepository).saveEventLog(eventCaptor.capture())

        assertEquals(EventType.SCHEDULED_SCAN_SCHEDULED, eventCaptor.firstValue.eventType)
        assertEquals(12345678L, eventCaptor.firstValue.eventTimeInMillis)
    }

    @Test
    fun whenScheduleScansThenUsesUpdatePolicy() {
        testee.scheduleScans()

        val policyCaptor = argumentCaptor<ExistingPeriodicWorkPolicy>()
        verify(mockWorkManager, times(4)).enqueueUniquePeriodicWork(
            any(),
            policyCaptor.capture(),
            any<PeriodicWorkRequest>(),
        )

        policyCaptor.allValues.forEach { policy ->
            assertEquals(ExistingPeriodicWorkPolicy.UPDATE, policy)
        }
    }

    @Test
    fun whenScheduleScansThenSchedulesScheduledScanWorker() {
        testee.scheduleScans()

        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(PirScheduledScanRemoteWorker.TAG_SCHEDULED_SCAN),
            any(),
            any<PeriodicWorkRequest>(),
        )
    }

    @Test
    fun whenScheduleScansThenSchedulesEmailConfirmationWorker() {
        testee.scheduleScans()

        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(PirEmailConfirmationRemoteWorker.TAG_EMAIL_CONFIRMATION),
            any(),
            any<PeriodicWorkRequest>(),
        )
    }

    @Test
    fun whenScheduleScansThenSchedulesPixelStatsWorker() {
        testee.scheduleScans()

        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(PirCustomStatsWorker.TAG_PIR_RECURRING_CUSTOM_STATS),
            any(),
            any<PeriodicWorkRequest>(),
        )
    }

    @Test
    fun whenCancelScheduledScansThenCancelsScheduledScanWork() {
        testee.cancelScheduledScans(mockContext)

        verify(mockWorkManager).cancelUniqueWork(PirScheduledScanRemoteWorker.TAG_SCHEDULED_SCAN)
    }

    @Test
    fun whenCancelScheduledScansThenCancelsEmailConfirmationWork() {
        testee.cancelScheduledScans(mockContext)

        verify(mockWorkManager).cancelUniqueWork(PirEmailConfirmationRemoteWorker.TAG_EMAIL_CONFIRMATION)
    }

    @Test
    fun whenCancelScheduledScansThenCancelsPixelStatsWork() {
        testee.cancelScheduledScans(mockContext)

        verify(mockWorkManager).cancelUniqueWork(PirCustomStatsWorker.TAG_PIR_RECURRING_CUSTOM_STATS)
    }

    @Test
    fun whenCancelScheduledScansThenStopsRemoteWorkerService() {
        testee.cancelScheduledScans(mockContext)

        verify(mockContext).stopService(any())
    }

    @Test
    fun whenCancelScheduledScansThenCancelsAllThreeWorkers() {
        testee.cancelScheduledScans(mockContext)

        verify(mockWorkManager, times(4)).cancelUniqueWork(any())
    }

    @Test
    fun whenScheduleScansThenExecutesInOrder() {
        testee.scheduleScans()

        val inOrder = org.mockito.kotlin.inOrder(mockWorkManager, mockPirPixelSender)

        inOrder.verify(mockPirPixelSender).reportScheduledScanScheduled()
        inOrder.verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(PirScheduledScanRemoteWorker.TAG_SCHEDULED_SCAN),
            any(),
            any<PeriodicWorkRequest>(),
        )
    }

    @Test
    fun whenScheduleScansThenEventsRepositoryIsCalledAsync() = runTest {
        testee.scheduleScans()

        testDispatcher.scheduler.advanceUntilIdle()

        verify(mockEventsRepository).saveEventLog(any())
    }

    @Test
    fun whenScheduleScansThenUsesCorrectApplicationId() {
        whenever(mockAppBuildConfig.applicationId).thenReturn("com.custom.app")

        testee = RealPirScanScheduler(
            appBuildConfig = mockAppBuildConfig,
            workManager = mockWorkManager,
            coroutineScope = testScope,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
            eventsRepository = mockEventsRepository,
        )

        testee.scheduleScans()

        verify(mockWorkManager, times(4)).enqueueUniquePeriodicWork(
            any(),
            any(),
            any<PeriodicWorkRequest>(),
        )
    }

    @Test
    fun whenScheduleScansTwiceThenSchedulesWorkTwice() {
        testee.scheduleScans()
        testee.scheduleScans()

        verify(mockWorkManager, times(8)).enqueueUniquePeriodicWork(
            any(),
            any(),
            any<PeriodicWorkRequest>(),
        )
    }

    @Test
    fun whenScheduleScansTwiceThenReportsPixelTwice() {
        testee.scheduleScans()
        testee.scheduleScans()

        verify(mockPirPixelSender, times(2)).reportScheduledScanScheduled()
    }

    @Test
    fun whenCancelThenScheduleAgainThenWorkIsRescheduled() {
        testee.scheduleScans()
        testee.cancelScheduledScans(mockContext)
        testee.scheduleScans()

        verify(mockWorkManager, times(4)).cancelUniqueWork(any())
        verify(mockWorkManager, times(8)).enqueueUniquePeriodicWork(
            any(),
            any(),
            any<PeriodicWorkRequest>(),
        )
    }

    @Test
    fun whenScheduleScansThenUsesDifferentTagsForEachWorker() {
        testee.scheduleScans()

        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(PirScheduledScanRemoteWorker.TAG_SCHEDULED_SCAN),
            any(),
            any<PeriodicWorkRequest>(),
        )
        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(PirEmailConfirmationRemoteWorker.TAG_EMAIL_CONFIRMATION),
            any(),
            any<PeriodicWorkRequest>(),
        )
        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(PirCustomStatsWorker.TAG_PIR_RECURRING_CUSTOM_STATS),
            any(),
            any<PeriodicWorkRequest>(),
        )
        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(PirBackgroundScanStatsWorker.TAG_PIR_BACKGROUND_STATS_DAILY),
            any(),
            any<PeriodicWorkRequest>(),
        )
    }

    @Test
    fun whenCancelScheduledScansThenCancelsUsingCorrectTags() {
        testee.cancelScheduledScans(mockContext)

        verify(mockWorkManager).cancelUniqueWork(eq(PirScheduledScanRemoteWorker.TAG_SCHEDULED_SCAN))
        verify(mockWorkManager).cancelUniqueWork(eq(PirEmailConfirmationRemoteWorker.TAG_EMAIL_CONFIRMATION))
        verify(mockWorkManager).cancelUniqueWork(eq(PirCustomStatsWorker.TAG_PIR_RECURRING_CUSTOM_STATS))
        verify(mockWorkManager).cancelUniqueWork(eq(PirBackgroundScanStatsWorker.TAG_PIR_BACKGROUND_STATS_DAILY))
    }
}
