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
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.ENTRY_POINT_MANUAL_EDIT_PROFILE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.ENTRY_POINT_MANUAL_INITIAL
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.ENTRY_POINT_SCHEDULED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.EXECUTION_TYPE_MANUAL_EDIT_PROFILE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.EXECUTION_TYPE_MANUAL_INITIAL
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.EXECUTION_TYPE_SCHEDULED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.INTERVAL_OPT_OUT_DURATION
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_BATTERY_OPTIMIZATIONS
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_BROKER_COUNT
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_EXECUTION_TYPE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_LAST_STEP
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_NOTIFICATIONS_PERMISSION_GRANTED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_POWER_SAVING
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_PROFILE_QUERIES_COUNT
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_TOTAL_OPT_OUT_JOBS
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_TOTAL_SCAN_JOBS
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_TRACKER_BLOCKING_STATE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_VPN_CONNECTION_STATE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_WEB_VIEW_COUNT
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.STEP_OPT_OUT_COMPLETED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.STEP_OPT_OUT_SKIPPED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.STEP_OPT_OUT_STARTED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.STEP_SCAN_COMPLETED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.STEP_STARTED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.WIDE_EVENT_NAME_MANUAL
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.WIDE_EVENT_NAME_SCHEDULED
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.hours

class PirScanWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()

    @SuppressLint("DenyListedApi")
    private val pirRemoteFeatures: PirRemoteFeatures =
        FakeFeatureToggleFactory.create(PirRemoteFeatures::class.java).apply {
            sendScanWideEvent().setRawStoredState(Toggle.State(true))
        }

    private lateinit var testee: PirScanWideEventImpl

    @Before
    fun setUp() {
        testee = PirScanWideEventImpl(
            wideEventClient = wideEventClient,
            pirRemoteFeatures = pirRemoteFeatures,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
        // Force scheduled runs to be sampled in by default so scheduled tests fire deterministically.
        testee.sampleScheduledIn = { true }
    }

    private suspend fun runStarted(
        executionType: PirExecutionType,
        profileQueriesCount: Int,
        brokerCount: Int,
        totalScanJobs: Int,
        webViewCount: Int = totalScanJobs,
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
        // Given
        pirRemoteFeatures.sendScanWideEvent().setRawStoredState(Toggle.State(false))

        // When
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 5)
        testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL)
        testee.onScanCompleted(PirExecutionType.MANUAL_INITIAL)
        testee.onOptOutStarted(PirExecutionType.MANUAL_INITIAL)
        testee.onOptOutCompleted(PirExecutionType.MANUAL_INITIAL, totalOptOutJobs = 0)

        // Then
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun whenScheduledRunStartedAndSampledInThenFlowStartedWithScheduledName() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(50L))

        // When
        runStarted(PirExecutionType.SCHEDULED, 2, 5, 10)

        // Then
        verify(wideEventClient).flowStart(
            name = WIDE_EVENT_NAME_SCHEDULED,
            flowEntryPoint = ENTRY_POINT_SCHEDULED,
            metadata = mapOf(
                KEY_EXECUTION_TYPE to EXECUTION_TYPE_SCHEDULED,
                KEY_PROFILE_QUERIES_COUNT to "2",
                KEY_BROKER_COUNT to "5",
                KEY_TOTAL_SCAN_JOBS to "10",
                KEY_WEB_VIEW_COUNT to "10",
                KEY_POWER_SAVING to "false",
                KEY_BATTERY_OPTIMIZATIONS to "false",
                KEY_VPN_CONNECTION_STATE to "disconnected",
                KEY_NOTIFICATIONS_PERMISSION_GRANTED to "true",
                KEY_TRACKER_BLOCKING_STATE to "disabled",
            ),
            cleanupPolicy = CleanupPolicy.OnTimeout(duration = 8.hours, flowStatus = FlowStatus.Unknown),
        )
    }

    @Test
    fun whenScheduledRunStartedAndSampledOutThenNothingIsSent() = runTest {
        // Given
        testee.sampleScheduledIn = { false }

        // When
        runStarted(PirExecutionType.SCHEDULED, 2, 5, 10)
        testee.onScanCompleted(PirExecutionType.SCHEDULED)
        testee.onOptOutSkipped(PirExecutionType.SCHEDULED)

        // Then
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun whenManualRunStartedThenSampleScheduledHookIsNotInvoked() = runTest {
        // Given
        var hookInvocations = 0
        testee.sampleScheduledIn = {
            hookInvocations++
            true
        }
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(1L))

        // When
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)

        // Then - sampling should not gate manual runs
        org.junit.Assert.assertEquals(0, hookInvocations)
    }

    @Test
    fun whenManualInitialRunStartedThenFlowStartedWithExpectedParameters() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(123L))

        // When
        testee.onRunStarted(
            executionType = PirExecutionType.MANUAL_INITIAL,
            profileQueriesCount = 2,
            brokerCount = 5,
            totalScanJobs = 10,
            webViewCount = 10,
            isPowerSavingEnabled = true,
            isVpnConnected = true,
            batteryOptimizationsEnabled = false,
            notificationsPermissionGranted = true,
            isTrackerBlockingEnabled = true,
        )

        // Then
        verify(wideEventClient).flowStart(
            name = WIDE_EVENT_NAME_MANUAL,
            flowEntryPoint = ENTRY_POINT_MANUAL_INITIAL,
            metadata = mapOf(
                KEY_EXECUTION_TYPE to EXECUTION_TYPE_MANUAL_INITIAL,
                KEY_PROFILE_QUERIES_COUNT to "2",
                KEY_BROKER_COUNT to "5",
                KEY_TOTAL_SCAN_JOBS to "10",
                KEY_WEB_VIEW_COUNT to "10",
                KEY_POWER_SAVING to "true",
                KEY_BATTERY_OPTIMIZATIONS to "false",
                KEY_VPN_CONNECTION_STATE to "connected",
                KEY_NOTIFICATIONS_PERMISSION_GRANTED to "true",
                KEY_TRACKER_BLOCKING_STATE to "enabled",
            ),
            cleanupPolicy = CleanupPolicy.OnTimeout(duration = 8.hours, flowStatus = FlowStatus.Unknown),
        )
        verify(wideEventClient).flowStep(wideEventId = 123L, stepName = STEP_STARTED, success = true)
        verify(wideEventClient).intervalStart(
            wideEventId = 123L,
            key = "decile_0_10_duration_ms_bucketed",
        )
    }

    @Test
    fun whenManualEditProfileRunStartedThenEntryPointAndExecutionTypeReflectThat() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(456L))

        // When
        runStarted(PirExecutionType.MANUAL_EDIT_PROFILE, 1, 3, 6)

        // Then
        verify(wideEventClient).flowStart(
            name = WIDE_EVENT_NAME_MANUAL,
            flowEntryPoint = ENTRY_POINT_MANUAL_EDIT_PROFILE,
            metadata = mapOf(
                KEY_EXECUTION_TYPE to EXECUTION_TYPE_MANUAL_EDIT_PROFILE,
                KEY_PROFILE_QUERIES_COUNT to "1",
                KEY_BROKER_COUNT to "3",
                KEY_TOTAL_SCAN_JOBS to "6",
                KEY_WEB_VIEW_COUNT to "6",
                KEY_POWER_SAVING to "false",
                KEY_BATTERY_OPTIMIZATIONS to "false",
                KEY_VPN_CONNECTION_STATE to "disconnected",
                KEY_NOTIFICATIONS_PERMISSION_GRANTED to "true",
                KEY_TRACKER_BLOCKING_STATE to "disabled",
            ),
            cleanupPolicy = CleanupPolicy.OnTimeout(duration = 8.hours, flowStatus = FlowStatus.Unknown),
        )
    }

    @Test
    fun whenManualAndScheduledFlowsBothActiveThenTheyDoNotInterfere() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(11L)) // manual flow id
            .thenReturn(Result.success(22L)) // scheduled flow id

        // When
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)
        runStarted(PirExecutionType.SCHEDULED, 1, 1, 10)
        // Drive each flow's progress independently.
        repeat(5) { testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) } // crosses 50%
        repeat(3) { testee.onScanJobCompleted(PirExecutionType.SCHEDULED) } // crosses 30%
        testee.onScanCompleted(PirExecutionType.SCHEDULED)
        testee.onOptOutSkipped(PirExecutionType.SCHEDULED)

        // Then - manual flow remains open, scheduled flow finishes successfully without
        // touching the manual flow's id.
        verify(wideEventClient).flowStep(wideEventId = 11L, stepName = "progress_50", success = true)
        verify(wideEventClient).flowStep(wideEventId = 22L, stepName = "progress_30", success = true)
        verify(wideEventClient).flowStep(wideEventId = 22L, stepName = STEP_SCAN_COMPLETED, success = true)
        verify(wideEventClient).flowFinish(
            wideEventId = 22L,
            status = FlowStatus.Success,
            metadata = mapOf(
                KEY_LAST_STEP to STEP_OPT_OUT_SKIPPED,
                KEY_TOTAL_OPT_OUT_JOBS to "0",
            ),
        )
        verify(wideEventClient, never()).flowFinish(eq(11L), any(), any())
    }

    @Test
    fun whenScanJobsResolvedToLowerCountThenDecileMathUsesActualCount() = runTest {
        // Given - PirJobsRunner says 100 eligible jobs, but PirScan filters down to 5.
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(42L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 100)

        // When - PirScan reports the resolved count, then runs only 5 jobs.
        testee.onScanJobsResolved(PirExecutionType.MANUAL_INITIAL, actualScanJobs = 5)
        repeat(5) { testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) }

        // Then - decile math uses the actual count (5), so progress_90 fires on the last job
        // (5/5 = 100% → clamped to 90). Without the resolved-count update, completedScanJobs/100
        // would only reach 5%, and no progress step would fire — that's the bug being fixed.
        verify(wideEventClient).flowStep(wideEventId = 42L, stepName = "progress_90", success = true)
    }

    @Test
    fun whenScanJobsResolvedToZeroThenOpenDecileIntervalIsClosed() = runTest {
        // Given - PirJobsRunner says 10 eligible jobs (so onRunStarted opened decile_0_10),
        // but PirScan filters all of them out.
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(43L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)

        // When
        testee.onScanJobsResolved(PirExecutionType.MANUAL_INITIAL, actualScanJobs = 0)

        // Then - the decile_0_10 interval opened in onRunStarted is closed so it doesn't carry
        // through to scan_completed.
        verify(wideEventClient).intervalEnd(wideEventId = 43L, key = "decile_0_10_duration_ms_bucketed")
    }

    @Test
    fun whenScanJobsResolvedToSameCountThenNoStateChange() = runTest {
        // Given - resolved count matches the pre-filter estimate (the common case).
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(44L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)

        // When
        testee.onScanJobsResolved(PirExecutionType.MANUAL_INITIAL, actualScanJobs = 10)
        repeat(10) { testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) }

        // Then - decile math still works as expected with the unchanged total. No spurious
        // intervalEnd call (the interval is closed only when crossing a decile, not on resolution).
        for (decile in 1..9) {
            verify(wideEventClient).flowStep(
                wideEventId = 44L,
                stepName = "progress_${decile * 10}",
                success = true,
            )
        }
    }

    @Test
    fun whenRunStartedWithZeroTotalScanJobsThenNoIntervalStarted() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(789L))

        // When
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 0, 0)

        // Then
        verify(wideEventClient).flowStep(wideEventId = 789L, stepName = STEP_STARTED, success = true)
        verify(wideEventClient, never()).intervalStart(any(), any(), any(), any())
    }

    @Test
    fun whenSecondRunStartedWhileFlowOpenThenStaleFlowFinishedWithCancelled() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(100L))
            .thenReturn(Result.success(200L))

        // When
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)

        // Then
        verify(wideEventClient).flowFinish(
            wideEventId = 100L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_LAST_STEP to STEP_STARTED),
        )
    }

    @Test
    fun whenTenJobsCompleteOneAtATimeThenAllNineProgressStepsFire() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(1L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)

        // When
        repeat(10) { testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) }

        // Then - progress steps 10..90 fire exactly once each
        for (decile in 1..9) {
            verify(wideEventClient).flowStep(
                wideEventId = 1L,
                stepName = "progress_${decile * 10}",
                success = true,
            )
        }
    }

    @Test
    fun whenFiveJobsCompleteThenDecilesAreJumpedAndOnlyHighestCrossedFires() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(1L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 5)

        // When
        repeat(5) { testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) }

        // Then - progress_20/40/60/80/90 fire (the 5th completion clamps to 90), no progress_10/30/50/70/100
        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "progress_20", success = true)
        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "progress_40", success = true)
        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "progress_60", success = true)
        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "progress_80", success = true)
        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "progress_90", success = true)
        verify(wideEventClient, never()).flowStep(any(), eq("progress_10"), any(), any())
        verify(wideEventClient, never()).flowStep(any(), eq("progress_30"), any(), any())
        verify(wideEventClient, never()).flowStep(any(), eq("progress_50"), any(), any())
        verify(wideEventClient, never()).flowStep(any(), eq("progress_70"), any(), any())
        verify(wideEventClient, never()).flowStep(any(), eq("progress_100"), any(), any())
    }

    @Test
    fun whenSingleJobScanThenOnlyProgress90FiresAndIntervalsClose() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(7L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)

        // When
        testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL)
        testee.onScanCompleted(PirExecutionType.MANUAL_INITIAL)

        // Then
        verify(wideEventClient).flowStep(wideEventId = 7L, stepName = "progress_90", success = true)
        verify(wideEventClient).intervalEnd(wideEventId = 7L, key = "decile_0_10_duration_ms_bucketed")
        verify(wideEventClient).flowStep(wideEventId = 7L, stepName = STEP_SCAN_COMPLETED, success = true)
    }

    @Test
    fun whenTenJobsCompleteThenDecileIntervalsOpenAndCloseInOrder() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(11L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)

        // When
        repeat(10) { testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) }

        // Then - intervals 0_10..80_90 are closed; the 90_100 interval is never opened (no progress_100)
        for (start in 0..8) {
            val end = (start + 1) * 10
            verify(wideEventClient).intervalEnd(
                wideEventId = 11L,
                key = "decile_${start * 10}_${end}_duration_ms_bucketed",
            )
        }
        verify(wideEventClient, never()).intervalEnd(
            wideEventId = 11L,
            key = "decile_90_100_duration_ms_bucketed",
        )
    }

    @Test
    fun whenScanJobCompletedWithoutActiveFlowThenNoOp() = runTest {
        // Given - no onRunStarted call

        // When
        testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL)

        // Then
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun whenScanCompletedThenScanCompletedStepEmitted() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(2L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0) // zero scan jobs path

        // When
        testee.onScanCompleted(PirExecutionType.MANUAL_INITIAL)

        // Then
        verify(wideEventClient).flowStep(wideEventId = 2L, stepName = STEP_SCAN_COMPLETED, success = true)
    }

    @Test
    fun whenOptOutStartedThenStepAndIntervalEmitted() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(3L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0)

        // When
        testee.onOptOutStarted(PirExecutionType.MANUAL_INITIAL)

        // Then
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).flowStep(wideEventId = 3L, stepName = STEP_OPT_OUT_STARTED, success = true)
        order.verify(wideEventClient).intervalStart(
            wideEventId = 3L,
            key = INTERVAL_OPT_OUT_DURATION,
        )
    }

    @Test
    fun whenOptOutCompletedThenIntervalClosedAndFlowFinishedWithSuccess() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(4L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0)
        testee.onOptOutStarted(PirExecutionType.MANUAL_INITIAL)

        // When
        testee.onOptOutCompleted(PirExecutionType.MANUAL_INITIAL, totalOptOutJobs = 7)

        // Then
        verify(wideEventClient).intervalEnd(wideEventId = 4L, key = INTERVAL_OPT_OUT_DURATION)
        verify(wideEventClient).flowStep(wideEventId = 4L, stepName = STEP_OPT_OUT_COMPLETED, success = true)
        verify(wideEventClient).flowFinish(
            wideEventId = 4L,
            status = FlowStatus.Success,
            metadata = mapOf(
                KEY_LAST_STEP to STEP_OPT_OUT_COMPLETED,
                KEY_TOTAL_OPT_OUT_JOBS to "7",
            ),
        )
    }

    @Test
    fun whenOptOutSkippedThenStepEmittedAndFlowFinishedWithSuccess() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(5L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0)

        // When
        testee.onOptOutSkipped(PirExecutionType.MANUAL_INITIAL)

        // Then
        verify(wideEventClient).flowStep(wideEventId = 5L, stepName = STEP_OPT_OUT_SKIPPED, success = true)
        verify(wideEventClient).flowFinish(
            wideEventId = 5L,
            status = FlowStatus.Success,
            metadata = mapOf(
                KEY_LAST_STEP to STEP_OPT_OUT_SKIPPED,
                KEY_TOTAL_OPT_OUT_JOBS to "0",
            ),
        )
        verify(wideEventClient, never()).intervalEnd(
            wideEventId = 5L,
            key = INTERVAL_OPT_OUT_DURATION,
        )
    }

    @Test
    fun whenRunFailedThenFlowFinishedWithFailureAndLastStep() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(6L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 0, 0)

        // When
        testee.onRunFailed(PirExecutionType.MANUAL_INITIAL, "no_active_brokers")

        // Then
        verify(wideEventClient).flowFinish(
            wideEventId = 6L,
            status = FlowStatus.Failure(reason = "no_active_brokers"),
            metadata = mapOf(KEY_LAST_STEP to STEP_STARTED),
        )
    }

    @Test
    fun whenRunCancelledThenFlowFinishedWithCancelled() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(8L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)
        testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) // emits progress_90 -> last_step = progress_90

        // When
        testee.onRunCancelled(PirExecutionType.MANUAL_INITIAL)

        // Then
        verify(wideEventClient).flowFinish(
            wideEventId = 8L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_LAST_STEP to "progress_90"),
        )
    }

    @Test
    fun whenStateLostThenDownstreamCallsAreNoOpToAvoidPickingUpStaleFlows() = runTest {
        // Given - no onRunStarted ever (singleton recreated mid-flight, or sampled out).
        // Even if the DB has an orphan flow open, we MUST NOT pick it up here, otherwise
        // a sampled-out scheduled run could accidentally close a previously-sampled-in run's flow.

        // When
        testee.onScanCompleted(PirExecutionType.MANUAL_INITIAL)
        testee.onOptOutCompleted(PirExecutionType.MANUAL_INITIAL, totalOptOutJobs = 5)

        // Then
        verifyNoInteractions(wideEventClient)
    }
}
