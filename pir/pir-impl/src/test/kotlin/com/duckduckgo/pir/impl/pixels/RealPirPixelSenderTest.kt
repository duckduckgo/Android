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
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPirPixelSenderTest {
    private lateinit var testee: RealPirPixelSender
    private val mockPixelSender: Pixel = mock()
    private val mockNetworkProtectionState: NetworkProtectionState = mock()
    private val mockPirRemoteFeatures: PirRemoteFeatures = mock()
    private val mockToggle: Toggle = mock()

    @Before
    fun setUp() {
        whenever(mockPirRemoteFeatures.trackerBlocking()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(false)
        testee = RealPirPixelSender(mockPixelSender, mockNetworkProtectionState, mockPirRemoteFeatures)
    }

    @Test
    fun whenReportManualScanStartedThenFiresPixelWithPowerSavingParam() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)

        testee.reportManualScanStarted(
            isPowerSavingEnabled = true,
            profileQueryCount = 3,
            brokerCount = 10,
            executionType = PirExecutionType.MANUAL_INITIAL,
            notificationsPermissionGranted = true,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender, times(2)).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.containsKey("power_saving"))
        assert(paramsCaptor.firstValue["power_saving"] == "true")
        assert(paramsCaptor.firstValue.containsKey("profile_queries"))
        assert(paramsCaptor.firstValue["profile_queries"] == "3")
        assert(paramsCaptor.firstValue.containsKey("broker_count"))
        assert(paramsCaptor.firstValue["broker_count"] == "10")
        assert(paramsCaptor.firstValue["scan_trigger"] == "onboarding")
        assert(paramsCaptor.firstValue["vpn_connection_state"] == "disconnected")
        assert(paramsCaptor.firstValue["notifications_permission_granted"] == "true")
    }

    @Test
    fun whenReportManualScanStartedWithEditProfileThenScanTriggerIsProfileEdit() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)

        testee.reportManualScanStarted(
            isPowerSavingEnabled = false,
            profileQueryCount = 1,
            brokerCount = 5,
            executionType = PirExecutionType.MANUAL_EDIT_PROFILE,
            notificationsPermissionGranted = false,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender, times(2)).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["scan_trigger"] == "profile_edit")
        assert(paramsCaptor.firstValue["vpn_connection_state"] == "connected")
        assert(paramsCaptor.firstValue["notifications_permission_granted"] == "false")
    }

    @Test
    fun whenReportManualScanCompletedThenFiresPixelWithTotalTimeAndBatteryOptimizations() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)
        val totalTimeInMillis = 12345L

        testee.reportManualScanCompleted(
            totalTimeInMillis,
            batteryOptimizationsEnabled = false,
            totalScanJobs = 5,
            totalOptOutJobs = 3,
            profileQueryCount = 3,
            brokerCount = 10,
            isPowerSavingEnabled = true,
            executionType = PirExecutionType.MANUAL_INITIAL,
            notificationsPermissionGranted = true,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.containsKey("totalTimeInMillis"))
        assert(paramsCaptor.firstValue["totalTimeInMillis"] == "12345")
        assert(paramsCaptor.firstValue.containsKey("battery-optimizations"))
        assert(paramsCaptor.firstValue["battery-optimizations"] == "false")
        assert(paramsCaptor.firstValue.containsKey("total_scan"))
        assert(paramsCaptor.firstValue["total_scan"] == "5")
        assert(paramsCaptor.firstValue.containsKey("total_optout"))
        assert(paramsCaptor.firstValue["total_optout"] == "3")
        assert(paramsCaptor.firstValue.containsKey("profile_queries"))
        assert(paramsCaptor.firstValue["profile_queries"] == "3")
        assert(paramsCaptor.firstValue.containsKey("broker_count"))
        assert(paramsCaptor.firstValue["broker_count"] == "10")
        assert(paramsCaptor.firstValue.containsKey("power_saving"))
        assert(paramsCaptor.firstValue["power_saving"] == "true")
        assert(paramsCaptor.firstValue["scan_trigger"] == "onboarding")
        assert(paramsCaptor.firstValue["vpn_connection_state"] == "disconnected")
        assert(paramsCaptor.firstValue["notifications_permission_granted"] == "true")
    }

    @Test
    fun whenReportManualScanCompletedWithEditProfileThenScanTriggerIsProfileEdit() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)

        testee.reportManualScanCompleted(
            totalTimeInMillis = 1L,
            batteryOptimizationsEnabled = false,
            totalScanJobs = 0,
            totalOptOutJobs = 0,
            profileQueryCount = 0,
            brokerCount = 0,
            isPowerSavingEnabled = false,
            executionType = PirExecutionType.MANUAL_EDIT_PROFILE,
            notificationsPermissionGranted = false,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["scan_trigger"] == "profile_edit")
        assert(paramsCaptor.firstValue["vpn_connection_state"] == "connected")
        assert(paramsCaptor.firstValue["notifications_permission_granted"] == "false")
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
    fun whenReportManualScanLowMemoryThenEnqueuesCorrectPixel() = runTest {
        testee.reportManualScanLowMemory()

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
        testee.reportScheduledScanStarted(
            profileQueryCount = 3,
            brokerCount = 10,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.containsKey("profile_queries"))
        assert(paramsCaptor.firstValue["profile_queries"] == "3")
        assert(paramsCaptor.firstValue.containsKey("broker_count"))
        assert(paramsCaptor.firstValue["broker_count"] == "10")
    }

    @Test
    fun whenReportScheduledScanCompletedThenFiresPixelWithTotalTime() = runTest {
        val totalTimeInMillis = 54321L

        testee.reportScheduledScanCompleted(
            totalTimeInMillis = totalTimeInMillis,
            totalScanJobs = 7,
            totalOptOutJobs = 4,
            profileQueryCount = 3,
            brokerCount = 10,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue.containsKey("totalTimeInMillis"))
        assert(paramsCaptor.firstValue["totalTimeInMillis"] == "54321")
        assert(paramsCaptor.firstValue.containsKey("total_scan"))
        assert(paramsCaptor.firstValue["total_scan"] == "7")
        assert(paramsCaptor.firstValue.containsKey("total_optout"))
        assert(paramsCaptor.firstValue["total_optout"] == "4")
        assert(paramsCaptor.firstValue.containsKey("profile_queries"))
        assert(paramsCaptor.firstValue["profile_queries"] == "3")
        assert(paramsCaptor.firstValue.containsKey("broker_count"))
        assert(paramsCaptor.firstValue["broker_count"] == "10")
    }

    @Test
    fun whenReportOptOutSubmittedThenFiresPixelWithAllParameters() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)
        testee.reportOptOutSubmitted(
            brokerUrl = "https://broker.com",
            parent = "parent-broker",
            durationMs = 5000L,
            optOutAttemptCount = 2,
            emailPattern = "pattern-abc",
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
        assert(params["tracker_blocking_state"] == "disabled")
    }

    @Test
    fun whenReportOptOutSubmittedWithNullEmailPatternThenFiresPixelWithEmptyPattern() = runTest {
        testee.reportOptOutSubmitted(
            brokerUrl = "https://broker.com",
            parent = "parent-broker",
            durationMs = 5000L,
            optOutAttemptCount = 2,
            emailPattern = null,
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
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)
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
        assert(params["tracker_blocking_state"] == "disabled")
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
        assert(params["tracker_blocking_state"] == "disabled")
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
        assert(params["tracker_blocking_state"] == "disabled")
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
        assert(params["tracker_blocking_state"] == "disabled")
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
        assert(params["tracker_blocking_state"] == "disabled")
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
    fun whenReportBrokerCustomStateOptOutSubmitRateThenEnqueuesPixelWithParameters() = runTest {
        testee.reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = "https://broker.com",
            optOutSuccessRate = 0.75,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).enqueueFire(
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
    fun whenReportBrokerOptOutConfirmed7DaysThenEnqueuesPixelWithBrokerUrl() = runTest {
        testee.reportBrokerOptOutConfirmed7Days("https://broker.com")

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).enqueueFire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["data_broker"] == "https://broker.com")
    }

    @Test
    fun whenReportBrokerOptOutUnconfirmed7DaysThenEnqueuesPixelWithBrokerUrl() = runTest {
        testee.reportBrokerOptOutUnconfirmed7Days("https://broker.com")

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).enqueueFire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["data_broker"] == "https://broker.com")
    }

    @Test
    fun whenReportDAUThenEnqueuesCorrectPixel() = runTest {
        testee.reportDAU()

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
    fun whenReportWAUThenEnqueuesCorrectPixel() = runTest {
        testee.reportWAU()

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
    fun whenReportMAUThenEnqueuesCorrectPixel() = runTest {
        testee.reportMAU()

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
    fun whenReportWeeklyChildOrphanedOptOutsThenEnqueuesPixelWithAllParameters() = runTest {
        testee.reportWeeklyChildOrphanedOptOuts(
            brokerUrl = "https://child-broker.com",
            childParentRecordDifference = 5,
            orphanedRecordsCount = 3,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).enqueueFire(
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
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)
        testee.reportScanMatches(
            brokerUrl = "https://broker.com",
            totalMatches = 3,
            durationMs = 5000L,
            inManualStarted = true,
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
        assert(params["num_found"] == "3")
        assert(params["duration"] == "5000")
        assert(params["is_manual_scan"] == "true")
        assert(params["parent"] == "https://parent.com")
        assert(params["vpn_connection_state"] == "disconnected")
        assert(params["tracker_blocking_state"] == "disabled")
    }

    @Test
    fun whenReportScanNoMatchThenFiresPixelWithAllParameters() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)
        testee.reportScanNoMatch(
            brokerUrl = "https://broker.com",
            brokerVersion = "3.0",
            durationMs = 3000L,
            inManualStarted = false,
            parentUrl = "https://parent.com",
            actionId = "action-scan-2",
            actionType = "extract",
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
        assert(params["tracker_blocking_state"] == "disabled")
    }

    @Test
    fun whenReportScanErrorThenFiresPixelWithAllParameters() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)
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
        assert(params["tracker_blocking_state"] == "disabled")
    }

    @Test
    fun whenReportScanMatchesWithTrackerBlockingEnabledThenIncludesEnabledParam() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        testee.reportScanMatches(
            brokerUrl = "https://broker.com",
            totalMatches = 1,
            durationMs = 1000L,
            inManualStarted = false,
            parentUrl = "https://parent.com",
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["tracker_blocking_state"] == "enabled")
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
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)
        testee.reportDownloadMainConfigBEFailure("404")

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["error_code"] == "404")
        assert(params["vpn_connection_state"] == "disconnected")
    }

    @Test
    fun whenReportDownloadMainConfigFailureThenFiresCorrectPixel() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)
        testee.reportDownloadMainConfigFailure("test")

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender, times(2)).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.allValues.first()
        assert(params["error_details"] == "test")
        assert(params["vpn_connection_state"] == "connected")
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

    @Test
    fun whenReportInitialScanDurationThenFiresPixelWithAllParameters() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)

        testee.reportInitialScanDuration(
            durationMs = 45000L,
            profileQueryCount = 3,
            isPowerSavingEnabled = true,
            batteryOptimizationsEnabled = false,
            brokerCount = 10,
            executionType = PirExecutionType.MANUAL_INITIAL,
            notificationsPermissionGranted = true,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        val params = paramsCaptor.firstValue
        assert(params["duration_in_ms"] == "45000")
        assert(params["profile_queries"] == "3")
        assert(params["tracker_blocking_state"] == "disabled")
        assert(params["power_saving"] == "true")
        assert(params["battery-optimizations"] == "false")
        assert(params["broker_count"] == "10")
        assert(params["scan_trigger"] == "onboarding")
        assert(params["vpn_connection_state"] == "disconnected")
        assert(params["notifications_permission_granted"] == "true")
    }

    @Test
    fun whenReportInitialScanDurationWithEditProfileThenScanTriggerIsProfileEdit() = runTest {
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)

        testee.reportInitialScanDuration(
            durationMs = 1L,
            profileQueryCount = 0,
            isPowerSavingEnabled = false,
            batteryOptimizationsEnabled = true,
            brokerCount = 0,
            executionType = PirExecutionType.MANUAL_EDIT_PROFILE,
            notificationsPermissionGranted = false,
        )

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixelSender).fire(
            pixelName = any(),
            parameters = paramsCaptor.capture(),
            encodedParameters = any(),
            type = any(),
        )

        assert(paramsCaptor.firstValue["scan_trigger"] == "profile_edit")
        assert(paramsCaptor.firstValue["vpn_connection_state"] == "connected")
        assert(paramsCaptor.firstValue["notifications_permission_granted"] == "false")
    }
}
