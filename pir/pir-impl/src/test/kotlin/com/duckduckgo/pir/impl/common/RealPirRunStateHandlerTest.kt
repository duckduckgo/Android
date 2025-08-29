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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerManualScanCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScheduledScanCompleted
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse.ScriptAddressCityState
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse.ScriptExtractedProfile
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_ERROR
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_SUCCESS
import com.duckduckgo.pir.impl.store.db.PirBrokerScanLog
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class RealPirRunStateHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirRunStateHandler

    private val mockRepository: PirRepository = mock()
    private val mockEventsRepository: PirEventsRepository = mock()
    private val mockPixelSender: PirPixelSender = mock()
    private val mockJobRecordUpdater: JobRecordUpdater = mock()

    @Before
    fun setUp() {
        testee = RealPirRunStateHandler(
            repository = mockRepository,
            eventsRepository = mockEventsRepository,
            pixelSender = mockPixelSender,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            jobRecordUpdater = mockJobRecordUpdater,
        )
    }

    // Test data
    private val testBrokerName = "test-broker"
    private val testProfileQueryId = 123L
    private val testStartTimeInMillis = 1000L
    private val testEventTimeInMillis = 2000L
    private val testTotalTimeMillis = 1000L
    private val testExtractedProfileId = 456L

    private val testExtractedProfile = ExtractedProfile(
        dbId = testExtractedProfileId,
        profileQueryId = testProfileQueryId,
        brokerName = testBrokerName,
        name = "John Doe",
        alternativeNames = listOf("Johnny", "J. Doe"),
        age = "30",
        addresses = listOf(
            AddressCityState(
                city = "New York",
                state = "NY",
                fullAddress = "123 Main St",
            ),
        ),
        phoneNumbers = listOf("555-1234"),
        relatives = listOf("Jane Doe"),
        reportId = "report123",
        email = "john@example.com",
        fullName = "John Michael Doe",
        profileUrl = "https://example.com/profile/123",
        identifier = "id123",
    )

    private val testScriptExtractedProfile = ScriptExtractedProfile(
        name = "John Doe",
        alternativeNames = listOf("Johnny", "J. Doe"),
        age = "30",
        addresses = listOf(
            ScriptAddressCityState(
                city = "New York",
                state = "NY",
                fullAddress = "123 Main St",
            ),
        ),
        phoneNumbers = listOf("555-1234"),
        relatives = listOf("Jane Doe"),
        profileUrl = "https://example.com/profile/123",
        identifier = "id123",
        reportId = "report123",
        email = "john@example.com",
        fullName = "John Michael Doe",
    )

    @Test
    fun whenHandleBrokerManualScanCompletedWithSuccessThenSavesLogsAndReportsPixel() = runTest {
        val state = BrokerManualScanCompleted(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            startTimeInMillis = testStartTimeInMillis,
            eventTimeInMillis = testEventTimeInMillis,
            totalTimeMillis = testTotalTimeMillis,
            isSuccess = true,
        )

        testee.handleState(state)

        verify(mockEventsRepository).saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = testEventTimeInMillis,
                brokerName = testBrokerName,
                eventType = BROKER_SUCCESS,
            ),
        )
        verify(mockEventsRepository).saveScanCompletedBroker(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            startTimeInMillis = testStartTimeInMillis,
            endTimeInMillis = testEventTimeInMillis,
            isSuccess = true,
        )
        verify(mockPixelSender).reportBrokerScanCompleted(
            brokerName = testBrokerName,
            totalTimeInMillis = testTotalTimeMillis,
            isSuccess = true,
        )
        verifyNoInteractions(mockJobRecordUpdater)
    }

    @Test
    fun whenHandleBrokerManualScanCompletedWithFailureThenSavesErrorLogsAndReportsPixel() = runTest {
        val state = BrokerManualScanCompleted(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            startTimeInMillis = testStartTimeInMillis,
            eventTimeInMillis = testEventTimeInMillis,
            totalTimeMillis = testTotalTimeMillis,
            isSuccess = false,
        )

        testee.handleState(state)

        verify(mockEventsRepository).saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = testEventTimeInMillis,
                brokerName = testBrokerName,
                eventType = BROKER_ERROR,
            ),
        )
        verify(mockEventsRepository).saveScanCompletedBroker(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            startTimeInMillis = testStartTimeInMillis,
            endTimeInMillis = testEventTimeInMillis,
            isSuccess = false,
        )
        verify(mockPixelSender).reportBrokerScanCompleted(
            brokerName = testBrokerName,
            totalTimeInMillis = testTotalTimeMillis,
            isSuccess = false,
        )
        verify(mockJobRecordUpdater).updateScanError(testBrokerName, testProfileQueryId)
    }

    @Test
    fun whenHandleBrokerScheduledScanCompletedWithSuccessThenSavesLogsAndReportsPixel() = runTest {
        val state = BrokerScheduledScanCompleted(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            startTimeInMillis = testStartTimeInMillis,
            eventTimeInMillis = testEventTimeInMillis,
            totalTimeMillis = testTotalTimeMillis,
            isSuccess = true,
        )

        testee.handleState(state)

        verify(mockPixelSender).reportBrokerScanCompleted(
            brokerName = testBrokerName,
            totalTimeInMillis = testTotalTimeMillis,
            isSuccess = true,
        )
        verify(mockEventsRepository).saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = testEventTimeInMillis,
                brokerName = testBrokerName,
                eventType = BROKER_SUCCESS,
            ),
        )
        verify(mockEventsRepository).saveScanCompletedBroker(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            startTimeInMillis = testStartTimeInMillis,
            endTimeInMillis = testEventTimeInMillis,
            isSuccess = true,
        )
        verifyNoInteractions(mockJobRecordUpdater)
    }

    @Test
    fun whenHandleBrokerScheduledScanCompletedWithFailureThenSavesErrorLogsAndReportsPixel() = runTest {
        val state = BrokerScheduledScanCompleted(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            startTimeInMillis = testStartTimeInMillis,
            eventTimeInMillis = testEventTimeInMillis,
            totalTimeMillis = testTotalTimeMillis,
            isSuccess = false,
        )

        testee.handleState(state)

        verify(mockPixelSender).reportBrokerScanCompleted(
            brokerName = testBrokerName,
            totalTimeInMillis = testTotalTimeMillis,
            isSuccess = false,
        )
        verify(mockEventsRepository).saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = testEventTimeInMillis,
                brokerName = testBrokerName,
                eventType = BROKER_ERROR,
            ),
        )
        verify(mockEventsRepository).saveScanCompletedBroker(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            startTimeInMillis = testStartTimeInMillis,
            endTimeInMillis = testEventTimeInMillis,
            isSuccess = false,
        )
        verify(mockJobRecordUpdater).updateScanError(testBrokerName, testProfileQueryId)
    }

    @Test
    fun whenHandleBrokerScanActionSucceededWithExtractedResponseAndProfilesFoundThenSavesProfilesAndUpdatesJobs() = runTest {
        val extractedResponse = ExtractedResponse(
            actionID = "extract123",
            actionType = "extract",
            response = listOf(testScriptExtractedProfile),
        )
        val state = BrokerScanActionSucceeded(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            pirSuccessResponse = extractedResponse,
        )
        val expectedExtractedProfile = ExtractedProfile(
            profileUrl = "https://example.com/profile/123",
            profileQueryId = testProfileQueryId,
            brokerName = testBrokerName,
            name = "John Doe",
            alternativeNames = listOf("Johnny", "J. Doe"),
            age = "30",
            addresses = listOf(
                AddressCityState(
                    city = "New York",
                    state = "NY",
                    fullAddress = "123 Main St",
                ),
            ),
            phoneNumbers = listOf("555-1234"),
            relatives = listOf("Jane Doe"),
            identifier = "id123",
            reportId = "report123",
            email = "john@example.com",
            fullName = "John Michael Doe",
        )

        testee.handleState(state)

        // Verify the order of method calls is correct:
        // 1. Mark removed profiles first - compares new profiles with existing ones to mark any no longer present as removed
        // 2. Save extracted profiles second - persists the new profiles to database
        // 3. Update scan status last - updates job record to indicate matches were found
        // This order ensures data consistency and proper state management
        val inOrder = inOrder(mockJobRecordUpdater, mockRepository)
        inOrder.verify(mockJobRecordUpdater).markRemovedOptOutJobRecords(
            newExtractedProfiles = listOf(expectedExtractedProfile),
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
        )
        inOrder.verify(mockRepository).saveNewExtractedProfiles(listOf(expectedExtractedProfile))
        inOrder.verify(mockJobRecordUpdater).updateScanMatchesFound(testBrokerName, testProfileQueryId)
    }

    @Test
    fun whenHandleBrokerScanActionSucceededWithExtractedResponseAndNoProfilesFoundThenUpdatesJobAsNoMatch() = runTest {
        val extractedResponse = ExtractedResponse(
            actionID = "extract123",
            actionType = "extract",
            response = emptyList(),
        )
        val state = BrokerScanActionSucceeded(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            pirSuccessResponse = extractedResponse,
        )

        testee.handleState(state)

        verify(mockJobRecordUpdater).updateScanNoMatchFound(testBrokerName, testProfileQueryId)
        verifyNoInteractions(mockRepository)
    }

    @Test
    fun whenHandleBrokerScanActionSucceededWithNonExtractedResponseThenDoesNothing() = runTest {
        val navigateResponse = NavigateResponse(
            actionID = "navigate123",
            actionType = "navigate",
            response = NavigateResponse.ResponseData(url = "https://example.com"),
        )
        val state = BrokerScanActionSucceeded(
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
            pirSuccessResponse = navigateResponse,
        )

        testee.handleState(state)

        verifyNoInteractions(mockRepository)
        verifyNoInteractions(mockJobRecordUpdater)
    }

    @Test
    fun whenHandleBrokerRecordOptOutStartedThenMarksOptOutAsAttemptedAndReportsPixel() = runTest {
        val state = BrokerRecordOptOutStarted(
            brokerName = testBrokerName,
            extractedProfile = testExtractedProfile,
        )

        testee.handleState(state)

        verify(mockJobRecordUpdater).markOptOutAsAttempted(testExtractedProfileId)
        verify(mockPixelSender).reportOptOutStarted(brokerName = testBrokerName)
    }

    @Test
    fun whenHandleBrokerRecordOptOutCompletedWithSuccessThenUpdatesRecordAndReportsPixel() = runTest {
        val state = BrokerRecordOptOutCompleted(
            brokerName = testBrokerName,
            extractedProfile = testExtractedProfile,
            startTimeInMillis = testStartTimeInMillis,
            endTimeInMillis = testEventTimeInMillis,
            isSubmitSuccess = true,
        )

        testee.handleState(state)

        verify(mockJobRecordUpdater).updateOptOutRequested(testExtractedProfileId)
        verify(mockEventsRepository).saveOptOutCompleted(
            brokerName = testBrokerName,
            extractedProfile = testExtractedProfile,
            startTimeInMillis = testStartTimeInMillis,
            endTimeInMillis = testEventTimeInMillis,
            isSubmitSuccess = true,
        )
        verify(mockPixelSender).reportOptOutCompleted(
            brokerName = testBrokerName,
            totalTimeInMillis = testEventTimeInMillis - testStartTimeInMillis,
            isSuccess = true,
        )
    }

    @Test
    fun whenHandleBrokerRecordOptOutCompletedWithFailureThenUpdatesRecordAndReportsPixel() = runTest {
        val state = BrokerRecordOptOutCompleted(
            brokerName = testBrokerName,
            extractedProfile = testExtractedProfile,
            startTimeInMillis = testStartTimeInMillis,
            endTimeInMillis = testEventTimeInMillis,
            isSubmitSuccess = false,
        )

        testee.handleState(state)

        verify(mockJobRecordUpdater).updateOptOutError(testExtractedProfileId)
        verify(mockEventsRepository).saveOptOutCompleted(
            brokerName = testBrokerName,
            extractedProfile = testExtractedProfile,
            startTimeInMillis = testStartTimeInMillis,
            endTimeInMillis = testEventTimeInMillis,
            isSubmitSuccess = false,
        )
        verify(mockPixelSender).reportOptOutCompleted(
            brokerName = testBrokerName,
            totalTimeInMillis = testEventTimeInMillis - testStartTimeInMillis,
            isSuccess = false,
        )
    }
}
