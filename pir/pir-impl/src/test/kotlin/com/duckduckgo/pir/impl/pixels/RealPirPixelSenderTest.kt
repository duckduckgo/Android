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

import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealPirPixelSenderTest {
    private lateinit var testee: RealPirPixelSender
    private val mockPixelSender: Pixel = mock()

    @Before
    fun setUp() {
        testee = RealPirPixelSender(mockPixelSender)
    }

    @Test
    fun whenReportManualScanStartedThenFiresCorrectPixel() = runTest {
        testee.reportManualScanStarted()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportManualScanCompletedThenFiresPixelWithTotalTime() = runTest {
        val totalTimeInMillis = 12345L

        testee.reportManualScanCompleted(totalTimeInMillis)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.containsKey("totalTimeInMillis"))
        assert(paramsCaptor.firstValue["totalTimeInMillis"] == "12345")
    }

    @Test
    fun whenReportManualScanStartFailedThenEnqueuesCorrectPixel() = runTest {
        testee.reportManualScanStartFailed()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).enqueueFire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportManualScanLowMemoryThenEnqueuesPixelWithMemoryLevel() = runTest {
        testee.reportManualScanLowMemory()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).enqueueFire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenReportScheduledScanScheduledThenFiresCorrectPixel() = runTest {
        testee.reportScheduledScanScheduled()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportScheduledScanStartedThenFiresCorrectPixel() = runTest {
        testee.reportScheduledScanStarted()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportScheduledScanCompletedThenFiresPixelWithTotalTime() = runTest {
        val totalTimeInMillis = 54321L

        testee.reportScheduledScanCompleted(totalTimeInMillis)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.containsKey("totalTimeInMillis"))
        assert(paramsCaptor.firstValue["totalTimeInMillis"] == "54321")
    }

    @Test
    fun whenReportOptOutSubmittedThenFiresPixelWithAllParameters() = runTest {
        testee.reportOptOutSubmitted(
            brokerUrl = "https://broker.com",
            parent = "parent-broker",
            durationMs = 5000L,
            optOutAttemptCount = 2,
            emailPattern = "pattern-abc",
            isVpnRunning = false,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["parent"] == "parent-broker")
        assert(params["duration"] == "5000")
        assert(params["tries"] == "2")
        assert(params["pattern"] == "pattern-abc")
        assert(params["vpn_connection_state"] == "disconnected")
    }

    @Test
    fun whenReportOptOutSubmittedWithNullEmailPatternThenFiresPixelWithEmptyPattern() = runTest {
        testee.reportOptOutSubmitted(
            brokerUrl = "https://broker.com",
            parent = "parent-broker",
            durationMs = 5000L,
            optOutAttemptCount = 2,
            emailPattern = null,
            isVpnRunning = false,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["pattern"] == "")
    }

    @Test
    fun whenReportOptOutFailedThenFiresPixelWithAllParameters() = runTest {
        testee.reportOptOutFailed(
            brokerUrl = "https://broker.com",
            parent = "parent-broker",
            brokerJsonVersion = "1.0",
            durationMs = 3000L,
            stage = PirStage.FILL_FORM,
            tries = 3,
            emailPattern = "pattern-xyz",
            actionId = "action-1",
            actionType = "fillform",
            isVpnRunning = true,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender, org.mockito.kotlin.atLeastOnce()).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        // Check the captured parameters (may be fired once or twice for Count/Daily)
        val params = paramsCaptor.allValues.first()
        assert(params["data_broker"] == "https://broker.com")
        assert(params["parent"] == "parent-broker")
        assert(params["broker_version"] == "1.0")
        assert(params["duration"] == "3000")
        assert(params["stage"] == "fill-form")
        assert(params["tries"] == "3")
        assert(params["pattern"] == "pattern-xyz")
        assert(params["action_id"] == "action-1")
        assert(params["action_type"] == "fillform")
        assert(params["vpn_connection_state"] == "connected")
    }

    @Test
    fun whenSendCPUUsageAlertThenFiresPixelWithCpuUsage() = runTest {
        testee.sendCPUUsageAlert(85)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["cpuUsage"] == "85")
    }

    @Test
    fun whenReportEmailConfirmationLinkFetchedThenFiresPixelWithAllParameters() = runTest {
        testee.reportEmailConfirmationLinkFetched(
            brokerUrl = "https://broker.com",
            brokerVersion = "2.0",
            linkAgeMs = 60000L,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "2.0")
        assert(params["link_age_ms"] == "60000")
    }

    @Test
    fun whenReportEmailConfirmationLinkFetchBEErrorThenFiresPixelWithAllParameters() = runTest {
        testee.reportEmailConfirmationLinkFetchBEError(
            brokerUrl = "https://broker.com",
            brokerVersion = "2.0",
            status = "error",
            errorCode = "server_error",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "2.0")
        assert(params["status"] == "error")
        assert(params["error_code"] == "server_error")
    }

    @Test
    fun whenReportStagePendingEmailConfirmationThenFiresPixelWithAllParameters() = runTest {
        testee.reportStagePendingEmailConfirmation(
            brokerUrl = "https://broker.com",
            brokerVersion = "2.0",
            actionId = "action-2",
            durationMs = 2000L,
            tries = 1,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "2.0")
        assert(params["action_id"] == "action-2")
        assert(params["duration"] == "2000")
        assert(params["tries"] == "1")
    }

    @Test
    fun whenReportEmailConfirmationAttemptStartThenFiresPixelWithAllParameters() = runTest {
        testee.reportEmailConfirmationAttemptStart(
            brokerUrl = "https://broker.com",
            brokerVersion = "2.0",
            attemptNumber = 1,
            actionId = "action-3",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "2.0")
        assert(params["attempt_number"] == "1")
        assert(params["action_id"] == "action-3")
    }

    @Test
    fun whenReportEmailConfirmationAttemptSuccessThenFiresPixelWithAllParameters() = runTest {
        testee.reportEmailConfirmationAttemptSuccess(
            brokerUrl = "https://broker.com",
            brokerVersion = "2.0",
            attemptNumber = 2,
            actionId = "action-4",
            durationMs = 1500L,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "2.0")
        assert(params["attempt_number"] == "2")
        assert(params["action_id"] == "action-4")
        assert(params["duration"] == "1500")
    }

    @Test
    fun whenReportEmailConfirmationAttemptFailedThenFiresPixelWithAllParameters() = runTest {
        testee.reportEmailConfirmationAttemptFailed(
            brokerUrl = "https://broker.com",
            brokerVersion = "2.0",
            attemptNumber = 3,
            actionId = "action-5",
            durationMs = 1000L,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "2.0")
        assert(params["attempt_number"] == "3")
        assert(params["action_id"] == "action-5")
        assert(params["duration"] == "1000")
    }

    @Test
    fun whenReportEmailConfirmationAttemptRetriesExceededThenFiresPixelWithAllParameters() = runTest {
        testee.reportEmailConfirmationAttemptRetriesExceeded(
            brokerUrl = "https://broker.com",
            brokerVersion = "2.0",
            actionId = "action-6",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "2.0")
        assert(params["action_id"] == "action-6")
    }

    @Test
    fun whenReportEmailConfirmationJobSuccessThenFiresPixelWithAllParameters() = runTest {
        testee.reportEmailConfirmationJobSuccess(
            brokerUrl = "https://broker.com",
            brokerVersion = "2.0",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "2.0")
    }

    @Test
    fun whenReportEmailConfirmationStartedThenFiresCorrectPixel() = runTest {
        testee.reportEmailConfirmationStarted()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportEmailConfirmationCompletedThenFiresPixelWithAllParameters() = runTest {
        testee.reportEmailConfirmationCompleted(
            totalTimeInMillis = 30000L,
            totalFetchAttempts = 5,
            totalEmailConfirmationJobs = 10,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["totalTimeInMillis"] == "30000")
        assert(params["totalFetchAttempts"] == "5")
        assert(params["totalEmailConfirmationJobs"] == "10")
    }

    @Test
    fun whenReportSecureStorageUnavailableThenFiresCorrectPixel() = runTest {
        testee.reportSecureStorageUnavailable()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender, org.mockito.kotlin.times(2)).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        // Check both captured values are empty
        paramsCaptor.allValues.forEach { params ->
            assert(params.isEmpty())
        }
    }

    @Test
    fun whenReportBrokerCustomStateOptOutSubmitRateThenFiresPixelWithParameters() = runTest {
        testee.reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = "https://broker.com",
            optOutSuccessRate = 0.75,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["optout_submit_success_rate"] == "0.75")
    }

    @Test
    fun whenReportBrokerOptOutConfirmed7DaysThenFiresPixelWithBrokerUrl() = runTest {
        testee.reportBrokerOptOutConfirmed7Days("https://broker.com")

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["data_broker"] == "https://broker.com")
    }

    @Test
    fun whenReportBrokerOptOutUnconfirmed7DaysThenFiresPixelWithBrokerUrl() = runTest {
        testee.reportBrokerOptOutUnconfirmed7Days("https://broker.com")

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["data_broker"] == "https://broker.com")
    }

    @Test
    fun whenReportDAUThenFiresCorrectPixel() = runTest {
        testee.reportDAU()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportWAUThenFiresCorrectPixel() = runTest {
        testee.reportWAU()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportMAUThenFiresCorrectPixel() = runTest {
        testee.reportMAU()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportWeeklyChildOrphanedOptOutsThenFiresPixelWithAllParameters() = runTest {
        testee.reportWeeklyChildOrphanedOptOuts(
            brokerUrl = "https://child-broker.com",
            childParentRecordDifference = 5,
            orphanedRecordsCount = 3,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://child-broker.com")
        assert(params["child-parent-record-difference"] == "5")
        assert(params["calculated-orphaned-records"] == "3")
    }

    @Test
    fun whenReportScanStartedThenFiresPixelWithBrokerUrl() = runTest {
        testee.reportScanStarted("https://broker.com")

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["data_broker"] == "https://broker.com")
    }

    @Test
    fun whenReportScanStageThenFiresPixelWithAllParameters() = runTest {
        testee.reportScanStage(
            brokerUrl = "https://broker.com",
            brokerVersion = "3.0",
            tries = 2,
            parentUrl = "https://parent.com",
            actionId = "action-scan-1",
            actionType = "navigate",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "3.0")
        assert(params["tries"] == "2")
        assert(params["parent"] == "https://parent.com")
        assert(params["action_id"] == "action-scan-1")
        assert(params["action_type"] == "navigate")
    }

    @Test
    fun whenReportScanMatchesThenFiresPixelWithAllParameters() = runTest {
        testee.reportScanMatches(
            brokerUrl = "https://broker.com",
            totalMatches = 3,
            durationMs = 5000L,
            inManualStarted = true,
            parentUrl = "https://parent.com",
            isVpnRunning = false,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["num_found"] == "3")
        assert(params["duration"] == "5000")
        assert(params["is_manual_scan"] == "true")
        assert(params["parent"] == "https://parent.com")
        assert(params["vpn_connection_state"] == "disconnected")
    }

    @Test
    fun whenReportScanNoMatchThenFiresPixelWithAllParameters() = runTest {
        testee.reportScanNoMatch(
            brokerUrl = "https://broker.com",
            brokerVersion = "3.0",
            durationMs = 3000L,
            inManualStarted = false,
            parentUrl = "https://parent.com",
            actionId = "action-scan-2",
            actionType = "extract",
            isVpnRunning = true,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "3.0")
        assert(params["duration"] == "3000")
        assert(params["is_manual_scan"] == "false")
        assert(params["parent"] == "https://parent.com")
        assert(params["action_id"] == "action-scan-2")
        assert(params["action_type"] == "extract")
        assert(params["vpn_connection_state"] == "connected")
    }

    @Test
    fun whenReportScanErrorThenFiresPixelWithAllParameters() = runTest {
        testee.reportScanError(
            brokerUrl = "https://broker.com",
            brokerVersion = "3.0",
            durationMs = 2000L,
            errorCategory = "network",
            errorDetails = "timeout",
            inManualStarted = true,
            parentUrl = "https://parent.com",
            actionId = "action-scan-3",
            actionType = "navigate",
            isVpnRunning = false,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["broker_version"] == "3.0")
        assert(params["duration"] == "2000")
        assert(params["error_category"] == "network")
        assert(params["error_details"] == "timeout")
        assert(params["is_manual_scan"] == "true")
        assert(params["parent"] == "https://parent.com")
        assert(params["action_id"] == "action-scan-3")
        assert(params["action_type"] == "navigate")
        assert(params["vpn_connection_state"] == "disconnected")
    }

    @Test
    fun whenReportOptOutStageStartThenFiresPixelWithAllParameters() = runTest {
        testee.reportOptOutStageStart(
            brokerUrl = "https://broker.com",
            parentUrl = "https://parent.com",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["parent"] == "https://parent.com")
    }

    @Test
    fun whenReportOptOutStageEmailGenerateThenFiresPixelWithAllParameters() = runTest {
        testee.reportOptOutStageEmailGenerate(
            brokerUrl = "https://broker.com",
            parentUrl = "https://parent.com",
            brokerVersion = "4.0",
            durationMs = 1000L,
            tries = 1,
            actionId = "action-email-1",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["parent"] == "https://parent.com")
        assert(params["broker_version"] == "4.0")
        assert(params["duration"] == "1000")
        assert(params["tries"] == "1")
        assert(params["action_id"] == "action-email-1")
    }

    @Test
    fun whenReportOptOutStageFinishThenFiresPixelWithAllParameters() = runTest {
        testee.reportOptOutStageFinish(
            brokerUrl = "https://broker.com",
            parentUrl = "https://parent.com",
            durationMs = 10000L,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker"] == "https://broker.com")
        assert(params["parent"] == "https://parent.com")
        assert(params["duration"] == "10000")
    }

    @Test
    fun whenReportUpdateBrokerJsonSuccessThenFiresPixelWithAllParameters() = runTest {
        testee.reportUpdateBrokerJsonSuccess(
            brokerJsonFileName = "broker.json",
            removedAtMs = 123456789L,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker_json_file"] == "broker.json")
        assert(params["removed_at"] == "123456789")
    }

    @Test
    fun whenReportUpdateBrokerJsonFailureThenFiresPixelWithAllParameters() = runTest {
        testee.reportUpdateBrokerJsonFailure(
            brokerJsonFileName = "broker.json",
            removedAtMs = 987654321L,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["data_broker_json_file"] == "broker.json")
        assert(params["removed_at"] == "987654321")
    }

    @Test
    fun whenReportDownloadMainConfigBEFailureThenFiresPixelWithErrorCode() = runTest {
        testee.reportDownloadMainConfigBEFailure("404")

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["error_code"] == "404")
    }

    @Test
    fun whenReportDownloadMainConfigFailureThenFiresCorrectPixel() = runTest {
        testee.reportDownloadMainConfigFailure()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.isEmpty())
    }

    @Test
    fun whenReportBrokerActionFailureThenFiresPixelWithAllParameters() = runTest {
        testee.reportBrokerActionFailure(
            brokerUrl = "https://broker.com",
            brokerVersion = "5.0",
            parentUrl = "https://parent.com",
            actionId = "action-fail-1",
            errorMessage = "Action failed due to error",
            stepType = "scan",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender, org.mockito.kotlin.atLeastOnce()).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.allValues.first()
        assert(params["data_broker"] == "https://broker.com")
        assert(params["parent"] == "https://parent.com")
        assert(params["broker_version"] == "5.0")
        assert(params["action_id"] == "action-fail-1")
        assert(params["message"] == "Action failed due to error")
        assert(params["stepType"] == "scan")
    }

    @Test
    fun whenReportDashboardOpenedThenFiresCorrectPixel() = runTest {
        testee.reportDashboardOpened()

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender, org.mockito.kotlin.times(2)).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        // Check both captured values are empty
        paramsCaptor.allValues.forEach { params ->
            assert(params.isEmpty())
        }
    }
}
