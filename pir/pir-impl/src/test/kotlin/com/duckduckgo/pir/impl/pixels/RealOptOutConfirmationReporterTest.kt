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

package com.duckduckgo.pir.impl.pixels

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class RealOptOutConfirmationReporterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealOptOutConfirmationReporter

    private val mockPirSchedulingRepository: PirSchedulingRepository = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPixelSender: PirPixelSender = mock()

    // Test data
    // January 15, 2024 10:00:00 UTC
    private val baseTime = 1705309200000L
    private val oneDay = TimeUnit.DAYS.toMillis(1)
    private val sevenDays = TimeUnit.DAYS.toMillis(7)
    private val fourteenDays = TimeUnit.DAYS.toMillis(14)
    private val twentyOneDays = TimeUnit.DAYS.toMillis(21)
    private val fortyTwoDays = TimeUnit.DAYS.toMillis(42)

    private val testBroker = Broker(
        name = "test-broker",
        fileName = "test-broker.json",
        url = "https://test-broker.com",
        version = "1.0",
        parent = null,
        addedDatetime = baseTime,
        removedAt = 0L,
    )

    @Before
    fun setUp() {
        testee = RealOptOutConfirmationReporter(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirSchedulingRepository = mockPirSchedulingRepository,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pixelSender = mockPixelSender,
        )
    }

    @Test
    fun whenNoActiveBrokersThenDoesNotFirePixels() = runTest {
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
        verify(mockPirSchedulingRepository, never()).markOptOutDay7ConfirmationPixelSent(any(), any())
    }

    @Test
    fun whenNoOptOutJobsThenDoesNotFirePixels() = runTest {
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
        verify(mockPirSchedulingRepository, never()).markOptOutDay7ConfirmationPixelSent(any(), any())
    }

    @Test
    fun whenOptOutJobNotRequestedOrRemovedThenDoesNotFirePixels() = runTest {
        val now = baseTime + sevenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.NOT_EXECUTED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
        verify(mockPirSchedulingRepository, never()).markOptOutDay7ConfirmationPixelSent(any(), any())
    }

    @Test
    fun when7DaysPassedAndStatusIsRemovedThenFiresConfirmed7dayPixel() = runTest {
        val now = baseTime + sevenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed7Days(testBroker.url)
        verify(mockPirSchedulingRepository).markOptOutDay7ConfirmationPixelSent(1L, now)
    }

    @Test
    fun when7DaysPassedAndStatusIsRequestedThenFiresUnconfirmed7dayPixel() = runTest {
        val now = baseTime + sevenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutUnconfirmed7Days(testBroker.url)
        verify(mockPirSchedulingRepository).markOptOutDay7ConfirmationPixelSent(1L, now)
    }

    @Test
    fun when7DaysPixelAlreadySentThenDoesNotFireAgain() = runTest {
        val now = baseTime + sevenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
            confirmation7dayReportSentDateMs = baseTime + oneDay, // Already sent
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed7Days(any())
        verify(mockPirSchedulingRepository, never()).markOptOutDay7ConfirmationPixelSent(any(), any())
    }

    @Test
    fun whenLessThan7DaysPassedThenDoesNotFire7DayPixel() = runTest {
        val now = baseTime + sevenDays - TimeUnit.HOURS.toMillis(1) // 1 hour before 7 days
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed7Days(any())
        verify(mockPirSchedulingRepository, never()).markOptOutDay7ConfirmationPixelSent(any(), any())
    }

    @Test
    fun whenExactly7DaysPassedThenFires7DayPixel() = runTest {
        val now = baseTime + sevenDays // Exactly 7 days
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed7Days(testBroker.url)
        verify(mockPirSchedulingRepository).markOptOutDay7ConfirmationPixelSent(1L, now)
    }

    @Test
    fun when14DaysPassedThenFires14DayPixel() = runTest {
        val now = baseTime + fourteenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed14Days(testBroker.url)
        verify(mockPirSchedulingRepository).markOptOutDay14ConfirmationPixelSent(1L, now)
    }

    @Test
    fun when21DaysPassedThenFires21DayPixel() = runTest {
        val now = baseTime + twentyOneDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed21Days(testBroker.url)
        verify(mockPirSchedulingRepository).markOptOutDay21ConfirmationPixelSent(1L, now)
    }

    @Test
    fun when42DaysPassedThenFires42DayPixel() = runTest {
        val now = baseTime + fortyTwoDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed42Days(testBroker.url)
        verify(mockPirSchedulingRepository).markOptOutDay42ConfirmationPixelSent(1L, now)
    }

    @Test
    fun whenMultipleIntervalsPassedThenFiresAllApplicablePixels() = runTest {
        val now = baseTime + fortyTwoDays // 42 days passed
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed7Days(testBroker.url)
        verify(mockPixelSender).reportBrokerOptOutConfirmed14Days(testBroker.url)
        verify(mockPixelSender).reportBrokerOptOutConfirmed21Days(testBroker.url)
        verify(mockPixelSender).reportBrokerOptOutConfirmed42Days(testBroker.url)
    }

    @Test
    fun whenBrokerNotFoundThenSkipsJobRecord() = runTest {
        val now = baseTime + sevenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = "unknown-broker",
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed7Days(any())
        verify(mockPirSchedulingRepository, never()).markOptOutDay7ConfirmationPixelSent(any(), any())
    }

    @Test
    fun whenMultipleJobRecordsThenFiresPixelsForEach() = runTest {
        val now = baseTime + sevenDays
        val jobRecord1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )
        val jobRecord2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord1, jobRecord2))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed7Days(testBroker.url)
        verify(mockPixelSender).reportBrokerOptOutUnconfirmed7Days(testBroker.url)
        verify(mockPirSchedulingRepository).markOptOutDay7ConfirmationPixelSent(1L, now)
        verify(mockPirSchedulingRepository).markOptOutDay7ConfirmationPixelSent(2L, now)
    }

    @Test
    fun when14DayPixelAlreadySentThenDoesNotFireAgain() = runTest {
        val now = baseTime + fourteenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
            confirmation14dayReportSentDateMs = baseTime + oneDay, // Already sent
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed14Days(any())
        verify(mockPirSchedulingRepository, never()).markOptOutDay14ConfirmationPixelSent(any(), any())
    }

    @Test
    fun when21DayPixelAlreadySentThenDoesNotFireAgain() = runTest {
        val now = baseTime + twentyOneDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
            confirmation21dayReportSentDateMs = baseTime + oneDay, // Already sent
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed21Days(any())
        verify(mockPirSchedulingRepository, never()).markOptOutDay21ConfirmationPixelSent(any(), any())
    }

    @Test
    fun when42DayPixelAlreadySentThenDoesNotFireAgain() = runTest {
        val now = baseTime + fortyTwoDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
            confirmation42dayReportSentDateMs = baseTime + oneDay, // Already sent
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed42Days(any())
        verify(mockPirSchedulingRepository, never()).markOptOutDay42ConfirmationPixelSent(any(), any())
    }

    @Test
    fun when7DaysPassedBut14DaysNotPassedThenOnlyFires7DayPixel() = runTest {
        val now = baseTime + sevenDays + oneDay // 8 days passed
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed7Days(testBroker.url)
        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed14Days(any())
    }

    @Test
    fun when14DaysPassedBut21DaysNotPassedThenFires7And14DayPixels() = runTest {
        val now = baseTime + fourteenDays + oneDay // 15 days passed
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed7Days(testBroker.url)
        verify(mockPixelSender).reportBrokerOptOutConfirmed14Days(testBroker.url)
        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed21Days(any())
    }

    @Test
    fun whenRequestedStatusThenFiresUnconfirmedPixels() = runTest {
        val now = baseTime + sevenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutUnconfirmed7Days(testBroker.url)
        verify(mockPixelSender, never()).reportBrokerOptOutConfirmed7Days(any())
    }

    @Test
    fun whenRemovedStatusThenFiresConfirmedPixels() = runTest {
        val now = baseTime + sevenDays
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker.name,
            status = OptOutJobStatus.REMOVED,
            optOutRequestedDateInMillis = baseTime,
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        testee.attemptFirePixel()

        verify(mockPixelSender).reportBrokerOptOutConfirmed7Days(testBroker.url)
        verify(mockPixelSender, never()).reportBrokerOptOutUnconfirmed7Days(any())
    }

    private fun createOptOutJobRecord(
        extractedProfileId: Long,
        brokerName: String = testBroker.name,
        userProfileId: Long = 1L,
        status: OptOutJobStatus = OptOutJobStatus.REQUESTED,
        optOutRequestedDateInMillis: Long = baseTime,
        optOutRemovedDateInMillis: Long = 0L,
        confirmation7dayReportSentDateMs: Long = 0L,
        confirmation14dayReportSentDateMs: Long = 0L,
        confirmation21dayReportSentDateMs: Long = 0L,
        confirmation42dayReportSentDateMs: Long = 0L,
    ): OptOutJobRecord {
        return OptOutJobRecord(
            brokerName = brokerName,
            userProfileId = userProfileId,
            extractedProfileId = extractedProfileId,
            status = status,
            attemptCount = 0,
            lastOptOutAttemptDateInMillis = 0L,
            optOutRequestedDateInMillis = optOutRequestedDateInMillis,
            optOutRemovedDateInMillis = optOutRemovedDateInMillis,
            deprecated = false,
            dateCreatedInMillis = baseTime,
            confirmation7dayReportSentDateMs = confirmation7dayReportSentDateMs,
            confirmation14dayReportSentDateMs = confirmation14dayReportSentDateMs,
            confirmation21dayReportSentDateMs = confirmation21dayReportSentDateMs,
            confirmation42dayReportSentDateMs = confirmation42dayReportSentDateMs,
        )
    }
}
