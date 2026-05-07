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

package com.duckduckgo.pir.impl.wideevents

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.duckduckgo.pir.impl.store.PirDataStore
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.COMPLETION_FLOW_TIMEOUT
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.ENTRY_POINT
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.INTERVAL_TOTAL_DURATION
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_BATTERY_OPTIMIZATIONS
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_BROKER_COUNT
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_FOREGROUND_RUN_COUNT
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_NOTIFICATIONS_PERMISSION_GRANTED
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_POWER_SAVING
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_PROFILE_QUERIES_COUNT
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_SCHEDULED_RUN_COUNT
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_TOTAL_SCAN_JOBS
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_TOTAL_SCAN_JOBS_AT_FINISH
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_TRACKER_BLOCKING_STATE
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_VPN_CONNECTION_STATE
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.KEY_WEB_VIEW_COUNT
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEventImpl.Companion.WIDE_EVENT_NAME
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PirInitialScanCompletionWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()
    private val pirSchedulingRepository: PirSchedulingRepository = mock()
    private val dataStore = FakePirDataStore()

    @SuppressLint("DenyListedApi")
    private val pirRemoteFeatures: PirRemoteFeatures =
        FakeFeatureToggleFactory.create(PirRemoteFeatures::class.java).apply {
            sendScanWideEvent().setRawStoredState(Toggle.State(true))
        }

    private lateinit var testee: PirInitialScanCompletionWideEventImpl

    @Before
    fun setUp() {
        testee = PirInitialScanCompletionWideEventImpl(
            wideEventClient = wideEventClient,
            pirRemoteFeatures = pirRemoteFeatures,
            pirDataStore = dataStore,
            pirSchedulingRepository = pirSchedulingRepository,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    private suspend fun runStarted(
        executionType: PirExecutionType = PirExecutionType.MANUAL_INITIAL,
        profileQueriesCount: Int = 1,
        brokerCount: Int = 5,
        totalScanJobs: Int = 10,
        webViewCount: Int = 10,
        isPowerSavingEnabled: Boolean = false,
        isVpnConnected: Boolean = false,
        batteryOptimizationsEnabled: Boolean = false,
        notificationsPermissionGranted: Boolean = true,
        isTrackerBlockingEnabled: Boolean = false,
    ) {
        testee.onRunStarted(
            executionType = executionType,
            profileQueriesCount = profileQueriesCount,
            brokerCount = brokerCount,
            totalScanJobs = totalScanJobs,
            webViewCount = webViewCount,
            isPowerSavingEnabled = isPowerSavingEnabled,
            isVpnConnected = isVpnConnected,
            batteryOptimizationsEnabled = batteryOptimizationsEnabled,
            notificationsPermissionGranted = notificationsPermissionGranted,
            isTrackerBlockingEnabled = isTrackerBlockingEnabled,
        )
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenFeatureFlagDisabledThenNothingIsSent() = runTest {
        pirRemoteFeatures.sendScanWideEvent().setRawStoredState(Toggle.State(false))

        runStarted()
        testee.onScanCompleted()

        verifyNoInteractions(wideEventClient)
        assertFalse(dataStore.hasInitialScanEverStarted)
        assertEquals(0L, dataStore.initialScanCompletionFlowId)
    }

    @Test
    fun whenManualInitialRunStartedAndFlagFalseThenFlowStartedWithExpectedMetadata() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(42L))

        testee.onRunStarted(
            executionType = PirExecutionType.MANUAL_INITIAL,
            profileQueriesCount = 2,
            brokerCount = 5,
            totalScanJobs = 10,
            webViewCount = 7,
            isPowerSavingEnabled = true,
            isVpnConnected = true,
            batteryOptimizationsEnabled = false,
            notificationsPermissionGranted = true,
            isTrackerBlockingEnabled = true,
        )

        verify(wideEventClient).flowStart(
            name = WIDE_EVENT_NAME,
            flowEntryPoint = ENTRY_POINT,
            metadata = mapOf(
                KEY_PROFILE_QUERIES_COUNT to "2",
                KEY_BROKER_COUNT to "5",
                KEY_TOTAL_SCAN_JOBS to "10",
                KEY_WEB_VIEW_COUNT to "7",
                KEY_POWER_SAVING to "true",
                KEY_BATTERY_OPTIMIZATIONS to "false",
                KEY_VPN_CONNECTION_STATE to "connected",
                KEY_NOTIFICATIONS_PERMISSION_GRANTED to "true",
                KEY_TRACKER_BLOCKING_STATE to "enabled",
            ),
            cleanupPolicy = CleanupPolicy.OnTimeout(duration = COMPLETION_FLOW_TIMEOUT, flowStatus = FlowStatus.Unknown),
        )
        verify(wideEventClient).intervalStart(wideEventId = 42L, key = INTERVAL_TOTAL_DURATION)
        assertTrue(dataStore.hasInitialScanEverStarted)
        assertEquals(42L, dataStore.initialScanCompletionFlowId)
        assertEquals(1, dataStore.initialScanCompletionForegroundRunCount)
        assertEquals(0, dataStore.initialScanCompletionScheduledRunCount)
    }

    @Test
    fun whenManualInitialRunStartedAndFlagAlreadyTrueThenNothingIsStarted() = runTest {
        dataStore.hasInitialScanEverStarted = true

        runStarted(executionType = PirExecutionType.MANUAL_INITIAL)

        verifyNoInteractions(wideEventClient)
        assertEquals(0L, dataStore.initialScanCompletionFlowId)
        assertEquals(0, dataStore.initialScanCompletionForegroundRunCount)
    }

    @Test
    fun whenScheduledRunStartedAndNoFlowOpenThenNothingIsStarted() = runTest {
        runStarted(executionType = PirExecutionType.SCHEDULED)

        verifyNoInteractions(wideEventClient)
        assertFalse(dataStore.hasInitialScanEverStarted)
        assertEquals(0, dataStore.initialScanCompletionScheduledRunCount)
    }

    @Test
    fun whenManualEditProfileRunStartedAndNoFlowOpenThenNothingIsStarted() = runTest {
        runStarted(executionType = PirExecutionType.MANUAL_EDIT_PROFILE)

        verifyNoInteractions(wideEventClient)
        assertFalse(dataStore.hasInitialScanEverStarted)
    }

    @Test
    fun whenScheduledRunStartedWhileFlowOpenThenScheduledCountIncrementedAndNoNewFlow() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(1L))
        runStarted(executionType = PirExecutionType.MANUAL_INITIAL)
        // foreground=1 after the manual initial run

        runStarted(executionType = PirExecutionType.SCHEDULED)
        runStarted(executionType = PirExecutionType.SCHEDULED)

        verify(wideEventClient).flowStart(any(), any(), any(), any())
        assertEquals(1L, dataStore.initialScanCompletionFlowId)
        assertEquals(1, dataStore.initialScanCompletionForegroundRunCount)
        assertEquals(2, dataStore.initialScanCompletionScheduledRunCount)
    }

    @Test
    fun whenManualEditProfileRunStartedWhileFlowOpenThenForegroundCountIncremented() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(1L))
        runStarted(executionType = PirExecutionType.MANUAL_INITIAL)

        runStarted(executionType = PirExecutionType.MANUAL_EDIT_PROFILE)

        verify(wideEventClient).flowStart(any(), any(), any(), any())
        assertEquals(2, dataStore.initialScanCompletionForegroundRunCount)
        assertEquals(0, dataStore.initialScanCompletionScheduledRunCount)
    }

    @Test
    fun whenScanCompletedAndAllJobsDoneThenFlowFinishedWithSuccess() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(99L))
        runStarted(executionType = PirExecutionType.MANUAL_INITIAL)
        runStarted(executionType = PirExecutionType.SCHEDULED)
        whenever(pirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(
                ScanJobRecord(brokerName = "b1", userProfileId = 1, lastScanDateInMillis = 1000L),
                ScanJobRecord(brokerName = "b2", userProfileId = 1, lastScanDateInMillis = 2000L),
            ),
        )

        testee.onScanCompleted()

        verify(wideEventClient).intervalEnd(wideEventId = 99L, key = INTERVAL_TOTAL_DURATION)
        verify(wideEventClient).flowFinish(
            wideEventId = 99L,
            status = FlowStatus.Success,
            metadata = mapOf(
                KEY_TOTAL_SCAN_JOBS_AT_FINISH to "2",
                KEY_FOREGROUND_RUN_COUNT to "1",
                KEY_SCHEDULED_RUN_COUNT to "1",
            ),
        )
        assertEquals(0L, dataStore.initialScanCompletionFlowId)
        assertEquals(0, dataStore.initialScanCompletionForegroundRunCount)
        assertEquals(0, dataStore.initialScanCompletionScheduledRunCount)
        // Flag remains set so we never start a second flow.
        assertTrue(dataStore.hasInitialScanEverStarted)
    }

    @Test
    fun whenScanCompletedAndSomeJobsNotDoneThenFlowRemainsOpen() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(99L))
        runStarted(executionType = PirExecutionType.MANUAL_INITIAL)
        whenever(pirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(
                ScanJobRecord(brokerName = "b1", userProfileId = 1, lastScanDateInMillis = 1000L),
                ScanJobRecord(brokerName = "b2", userProfileId = 1, lastScanDateInMillis = 0L),
            ),
        )

        testee.onScanCompleted()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        verify(wideEventClient, never()).intervalEnd(any(), any())
        assertEquals(99L, dataStore.initialScanCompletionFlowId)
    }

    @Test
    fun whenScanCompletedAndNoFlowOpenThenNoOp() = runTest {
        whenever(pirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(
                ScanJobRecord(brokerName = "b1", userProfileId = 1, lastScanDateInMillis = 1000L),
            ),
        )

        testee.onScanCompleted()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun whenJourneySpansMultipleRunsThenSuccessReportsTotalCounts() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(7L))

        // Initial foreground run starts the flow.
        runStarted(executionType = PirExecutionType.MANUAL_INITIAL)
        // Service gets killed; scheduled worker picks up.
        runStarted(executionType = PirExecutionType.SCHEDULED)
        // Worker doesn't finish; another scheduled run resumes.
        runStarted(executionType = PirExecutionType.SCHEDULED)
        // User edits profile, foreground service runs again.
        runStarted(executionType = PirExecutionType.MANUAL_EDIT_PROFILE)
        // Final scheduled run completes the work.
        runStarted(executionType = PirExecutionType.SCHEDULED)

        whenever(pirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(
                ScanJobRecord(brokerName = "b1", userProfileId = 1, lastScanDateInMillis = 1000L),
                ScanJobRecord(brokerName = "b2", userProfileId = 1, lastScanDateInMillis = 2000L),
                ScanJobRecord(brokerName = "b3", userProfileId = 1, lastScanDateInMillis = 3000L),
            ),
        )

        testee.onScanCompleted()

        verify(wideEventClient).flowFinish(
            wideEventId = 7L,
            status = FlowStatus.Success,
            metadata = mapOf(
                KEY_TOTAL_SCAN_JOBS_AT_FINISH to "3",
                KEY_FOREGROUND_RUN_COUNT to "2",
                KEY_SCHEDULED_RUN_COUNT to "3",
            ),
        )
    }
}

private class FakePirDataStore : PirDataStore {
    override var mainConfigEtag: String? = null
    override var customStatsPixelsLastSentMs: Long = 0L
    override var dauLastSentMs: Long = 0L
    override var wauLastSentMs: Long = 0L
    override var mauLastSentMs: Long = 0L
    override var weeklyStatLastSentMs: Long = 0L
    override var hasBrokerConfigBeenManuallyUpdated: Boolean = false
    override var latestBackgroundScanRunInMs: Long = 0L
    override var featureReceivedMs: Long = 0L
    override var hasInitialScanEverStarted: Boolean = false
    override var initialScanCompletionFlowId: Long = 0L
    override var initialScanCompletionForegroundRunCount: Int = 0
    override var initialScanCompletionScheduledRunCount: Int = 0

    override fun reset() {
        mainConfigEtag = null
        hasBrokerConfigBeenManuallyUpdated = false
        featureReceivedMs = 0L
        resetUserData()
    }

    override fun resetUserData() {
        customStatsPixelsLastSentMs = 0L
        dauLastSentMs = 0L
        wauLastSentMs = 0L
        mauLastSentMs = 0L
        weeklyStatLastSentMs = 0L
        latestBackgroundScanRunInMs = 0L
        hasInitialScanEverStarted = false
        initialScanCompletionFlowId = 0L
        initialScanCompletionForegroundRunCount = 0
        initialScanCompletionScheduledRunCount = 0
    }
}
