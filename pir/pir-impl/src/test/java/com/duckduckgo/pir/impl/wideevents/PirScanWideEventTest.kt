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
import android.database.sqlite.SQLiteException
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.checker.DisabledReason
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent.CancellationReason
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent.FailureReason
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.ENTRY_POINT_MANUAL_EDIT_PROFILE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.ENTRY_POINT_MANUAL_INITIAL
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.ENTRY_POINT_MANUAL_INITIAL_RESUME
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.ENTRY_POINT_SCHEDULED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.EXECUTION_TYPE_MANUAL_EDIT_PROFILE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.EXECUTION_TYPE_MANUAL_INITIAL
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.EXECUTION_TYPE_MANUAL_INITIAL_RESUME
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.EXECUTION_TYPE_SCHEDULED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.INTERVAL_OPT_OUT_DURATION
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.INTERVAL_TOTAL_FLOW_DURATION
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.INTERVAL_TOTAL_SCAN_DURATION
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_BATTERY_OPTIMIZATIONS
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_BROKER_COUNT
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_CANCELLATION_REASON
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_EXECUTION_TYPE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_LAST_STEP
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_LAST_STEP_ELAPSED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_NOTIFICATIONS_PERMISSION_GRANTED
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_POWER_SAVING
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_PROFILE_QUERIES_COUNT
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_TOTAL_OPT_OUT_JOBS
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_TOTAL_SCAN_JOBS
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_TRACKER_BLOCKING_STATE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_VPN_CONNECTION_STATE
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.KEY_WEB_VIEW_COUNT
import com.duckduckgo.pir.impl.wideevents.PirScanWideEventImpl.Companion.PER_RUN_DURATION_BUCKETS
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.IOException
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.hours

class PirScanWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()

    // Controllable monotonic clock. Defaults to 0 so steps record elapsed "0" unless a test advances it.
    private val timeProvider = FakeTimeProvider()

    @SuppressLint("DenyListedApi")
    private val pirRemoteFeatures: PirRemoteFeatures =
        FakeFeatureToggleFactory.create(PirRemoteFeatures::class.java).apply {
            sendScanWideEvent().setRawStoredState(Toggle.State(true))
        }

    private lateinit var testee: PirScanWideEventImpl

    @Before
    fun setUp() = runTest {
        // Default: no open flow in the shared wide-events DB. Cross-process tests override this.
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(emptyList()))
        testee = PirScanWideEventImpl(
            wideEventClient = wideEventClient,
            pirRemoteFeatures = pirRemoteFeatures,
            dispatchers = coroutineRule.testDispatcherProvider,
            currentTimeProvider = timeProvider,
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
        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = STEP_STARTED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_STARTED),
        )
        // The two total-duration intervals open with the wider per-run buckets.
        verify(wideEventClient).intervalStart(
            wideEventId = 123L,
            key = INTERVAL_TOTAL_FLOW_DURATION,
            buckets = PER_RUN_DURATION_BUCKETS,
        )
        verify(wideEventClient).intervalStart(
            wideEventId = 123L,
            key = INTERVAL_TOTAL_SCAN_DURATION,
            buckets = PER_RUN_DURATION_BUCKETS,
        )
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
    fun whenManualInitialResumeRunStartedThenEntryPointAndExecutionTypeReflectThat() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(789L))

        // When
        runStarted(PirExecutionType.MANUAL_INITIAL_RESUME, 1, 3, 6)

        // Then - a resume reuses the pir-initial-scan flow but is tagged with its own entry point / type
        verify(wideEventClient).flowStart(
            name = WIDE_EVENT_NAME_MANUAL,
            flowEntryPoint = ENTRY_POINT_MANUAL_INITIAL_RESUME,
            metadata = mapOf(
                KEY_EXECUTION_TYPE to EXECUTION_TYPE_MANUAL_INITIAL_RESUME,
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
        verify(wideEventClient).flowStep(
            wideEventId = 11L,
            stepName = "progress_50",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_50", KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 22L,
            stepName = "progress_30",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_30", KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 22L,
            stepName = STEP_SCAN_COMPLETED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_SCAN_COMPLETED, KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 22L,
            status = FlowStatus.Success,
            metadata = mapOf(KEY_TOTAL_OPT_OUT_JOBS to "0"),
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
        verify(wideEventClient).flowStep(
            wideEventId = 42L,
            stepName = "progress_90",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_90", KEY_LAST_STEP_ELAPSED to "0"),
        )
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
                metadata = mapOf(KEY_LAST_STEP to "progress_${decile * 10}", KEY_LAST_STEP_ELAPSED to "0"),
            )
        }
    }

    @Test
    fun whenRunStartedWithZeroTotalScanJobsThenTotalIntervalsStartButNoDecileInterval() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(789L))

        // When
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 0, 0)

        // Then - the total-duration intervals open regardless of job count, but the decile interval
        // does not (there is no scan progress to measure).
        verify(wideEventClient).flowStep(
            wideEventId = 789L,
            stepName = STEP_STARTED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_STARTED),
        )
        verify(wideEventClient).intervalStart(
            wideEventId = 789L,
            key = INTERVAL_TOTAL_FLOW_DURATION,
            buckets = PER_RUN_DURATION_BUCKETS,
        )
        verify(wideEventClient).intervalStart(
            wideEventId = 789L,
            key = INTERVAL_TOTAL_SCAN_DURATION,
            buckets = PER_RUN_DURATION_BUCKETS,
        )
        verify(wideEventClient, never()).intervalStart(
            any(),
            eq("decile_0_10_duration_ms_bucketed"),
            any(),
            any(),
        )
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

        // Then - the stale flow had decile_0_10 and both total-duration intervals open from its
        // onRunStarted; they must all be ended before flowFinish so dangling intervals don't pollute
        // timing analytics.
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 100L, key = "decile_0_10_duration_ms_bucketed")
        order.verify(wideEventClient).intervalEnd(wideEventId = 100L, key = INTERVAL_TOTAL_SCAN_DURATION)
        order.verify(wideEventClient).intervalEnd(wideEventId = 100L, key = INTERVAL_TOTAL_FLOW_DURATION)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 100L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "superseded_by_new_run"),
        )
    }

    @Test
    fun whenManualFlowOpenAndProfileEditRunStartsThenStaleFlowFinishedWithSupersededByProfileEdit() = runTest {
        // Given an open manual flow (the initial scan)
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(101L))
            .thenReturn(Result.success(201L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)

        // When the user edits their profile, triggering a MANUAL_EDIT_PROFILE re-scan that supersedes it
        runStarted(PirExecutionType.MANUAL_EDIT_PROFILE, 1, 1, 1)

        // Then the stale flow is attributed specifically to the profile edit
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 101L, key = "decile_0_10_duration_ms_bucketed")
        order.verify(wideEventClient).intervalEnd(wideEventId = 101L, key = INTERVAL_TOTAL_SCAN_DURATION)
        order.verify(wideEventClient).intervalEnd(wideEventId = 101L, key = INTERVAL_TOTAL_FLOW_DURATION)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 101L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "superseded_by_profile_edit"),
        )
    }

    @Test
    fun whenManualFlowOpenAndInitialResumeRunStartsThenStaleFlowFinishedWithSupersededByNewRun() = runTest {
        // Given an open manual flow (the interrupted initial scan)
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(102L))
            .thenReturn(Result.success(202L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)

        // When the dashboard resumes it with a MANUAL_INITIAL_RESUME run that supersedes the stale flow
        runStarted(PirExecutionType.MANUAL_INITIAL_RESUME, 1, 1, 1)

        // Then the stale flow is cancelled as a generic superseded-by-new-run (a resume is not a profile edit)
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 102L, key = "decile_0_10_duration_ms_bucketed")
        order.verify(wideEventClient).intervalEnd(wideEventId = 102L, key = INTERVAL_TOTAL_SCAN_DURATION)
        order.verify(wideEventClient).intervalEnd(wideEventId = 102L, key = INTERVAL_TOTAL_FLOW_DURATION)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 102L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "superseded_by_new_run"),
        )
    }

    @Test
    fun whenStepsRecordedThenEachCarriesLastStepMetadataSoUnknownEventsRetainProgress() = runTest {
        // The :pir process can be killed mid-scan and the flow finalized as FlowStatus.Unknown by the
        // 8h cleanup-on-timeout policy, which never calls flowFinish. Attaching last_step as metadata on
        // every step persists it incrementally (flowStep metadata is merged + stored immediately), so an
        // Unknown event still reports how far the scan got.
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(70L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)

        // When - complete 5 of 10 jobs (crosses 50%)
        repeat(5) { testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) }

        // Then - the initial step and the latest progress step each persist last_step = their own name
        verify(wideEventClient).flowStep(
            wideEventId = 70L,
            stepName = STEP_STARTED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_STARTED),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 70L,
            stepName = "progress_50",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_50", KEY_LAST_STEP_ELAPSED to "0"),
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
                metadata = mapOf(KEY_LAST_STEP to "progress_${decile * 10}", KEY_LAST_STEP_ELAPSED to "0"),
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
        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "progress_20",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_20", KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "progress_40",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_40", KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "progress_60",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_60", KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "progress_80",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_80", KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "progress_90",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_90", KEY_LAST_STEP_ELAPSED to "0"),
        )
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
        verify(wideEventClient).flowStep(
            wideEventId = 7L,
            stepName = "progress_90",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_90", KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).intervalEnd(wideEventId = 7L, key = "decile_0_10_duration_ms_bucketed")
        verify(wideEventClient).flowStep(
            wideEventId = 7L,
            stepName = STEP_SCAN_COMPLETED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_SCAN_COMPLETED, KEY_LAST_STEP_ELAPSED to "0"),
        )
        // The scan-phase total ends at scan_completed; the end-to-end total stays open until finish.
        verify(wideEventClient).intervalEnd(wideEventId = 7L, key = INTERVAL_TOTAL_SCAN_DURATION)
        verify(wideEventClient, never()).intervalEnd(wideEventId = 7L, key = INTERVAL_TOTAL_FLOW_DURATION)
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
        verify(wideEventClient).flowStep(
            wideEventId = 2L,
            stepName = STEP_SCAN_COMPLETED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_SCAN_COMPLETED, KEY_LAST_STEP_ELAPSED to "0"),
        )
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
        order.verify(wideEventClient).flowStep(
            wideEventId = 3L,
            stepName = STEP_OPT_OUT_STARTED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_OPT_OUT_STARTED, KEY_LAST_STEP_ELAPSED to "0"),
        )
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
        verify(wideEventClient).flowStep(
            wideEventId = 4L,
            stepName = STEP_OPT_OUT_COMPLETED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_OPT_OUT_COMPLETED, KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 4L,
            status = FlowStatus.Success,
            metadata = mapOf(KEY_TOTAL_OPT_OUT_JOBS to "7"),
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
        verify(wideEventClient).flowStep(
            wideEventId = 5L,
            stepName = STEP_OPT_OUT_SKIPPED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_OPT_OUT_SKIPPED, KEY_LAST_STEP_ELAPSED to "0"),
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 5L,
            status = FlowStatus.Success,
            metadata = mapOf(KEY_TOTAL_OPT_OUT_JOBS to "0"),
        )
        verify(wideEventClient, never()).intervalEnd(
            wideEventId = 5L,
            key = INTERVAL_OPT_OUT_DURATION,
        )
    }

    @Test
    fun whenScanCompletedThenOptOutCompletedThenTotalScanEndsAtScanAndTotalFlowEndsAtFinish() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(70L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0) // zero scan jobs, no decile interval

        // When
        testee.onScanCompleted(PirExecutionType.MANUAL_INITIAL)
        testee.onOptOutStarted(PirExecutionType.MANUAL_INITIAL)
        testee.onOptOutCompleted(PirExecutionType.MANUAL_INITIAL, totalOptOutJobs = 3)

        // Then - the scan-phase total ends at scan_completed; the end-to-end total ends just before
        // the success flowFinish so it spans the whole run (including opt-out).
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 70L, key = INTERVAL_TOTAL_SCAN_DURATION)
        order.verify(wideEventClient).intervalEnd(wideEventId = 70L, key = INTERVAL_OPT_OUT_DURATION)
        order.verify(wideEventClient).intervalEnd(wideEventId = 70L, key = INTERVAL_TOTAL_FLOW_DURATION)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 70L,
            status = FlowStatus.Success,
            metadata = mapOf(KEY_TOTAL_OPT_OUT_JOBS to "3"),
        )
    }

    @Test
    fun whenOptOutSkippedThenTotalFlowIntervalEndedBeforeFlowFinish() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(71L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0)
        testee.onScanCompleted(PirExecutionType.MANUAL_INITIAL) // ends the scan-phase total

        // When
        testee.onOptOutSkipped(PirExecutionType.MANUAL_INITIAL)

        // Then - the end-to-end total is closed before the success flowFinish.
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 71L, key = INTERVAL_TOTAL_SCAN_DURATION)
        order.verify(wideEventClient).intervalEnd(wideEventId = 71L, key = INTERVAL_TOTAL_FLOW_DURATION)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 71L,
            status = FlowStatus.Success,
            metadata = mapOf(KEY_TOTAL_OPT_OUT_JOBS to "0"),
        )
    }

    @Test
    fun whenRunFailedMidScanThenBothTotalIntervalsEndedBeforeFlowFinish() = runTest {
        // Given - failure fires before scan_completed, so the scan-phase total is still open.
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(72L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10) // decile_0_10 + both totals open

        // When
        testee.onRunFailed(PirExecutionType.MANUAL_INITIAL, FailureReason.UNKNOWN_ERROR)

        // Then - closeOpenIntervalsLocked ends the decile and both total intervals before flowFinish,
        // so the non-success run records partial (filterable) durations rather than dangling intervals.
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 72L, key = "decile_0_10_duration_ms_bucketed")
        order.verify(wideEventClient).intervalEnd(wideEventId = 72L, key = INTERVAL_TOTAL_SCAN_DURATION)
        order.verify(wideEventClient).intervalEnd(wideEventId = 72L, key = INTERVAL_TOTAL_FLOW_DURATION)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 72L,
            status = FlowStatus.Failure(reason = "unknown_error"),
        )
    }

    @Test
    fun whenRunFailedThenFlowFinishedWithFailure() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(6L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 0, 0)

        // When
        testee.onRunFailed(PirExecutionType.MANUAL_INITIAL, FailureReason.NO_ACTIVE_BROKERS)

        // Then - last_step is not attached here; it is persisted per-step via recordStep.
        verify(wideEventClient).flowFinish(
            wideEventId = 6L,
            status = FlowStatus.Failure(reason = "no_active_brokers"),
        )
    }

    @Test
    fun whenRunCancelledThenFlowFinishedWithCancelled() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(8L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)
        testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) // emits progress_90 (which persists last_step = progress_90)

        // When
        testee.onRunCancelled(PirExecutionType.MANUAL_INITIAL, CancellationReason.WORK_STOPPED)

        // Then - only the cancellation reason is attached at finish; last_step was persisted per-step.
        verify(wideEventClient).flowFinish(
            wideEventId = 8L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "work_stopped"),
        )
    }

    @Test
    fun whenRunFailedDuringScanThenOpenDecileIntervalIsClosedBeforeFlowFinish() = runTest {
        // Given - 10 jobs total, only 2 completed -> still inside decile_20_30 when failure fires.
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(60L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)
        repeat(2) { testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL) } // progress_20 fires, decile_20_30 opens

        // When
        testee.onRunFailed(PirExecutionType.MANUAL_INITIAL, FailureReason.UNKNOWN_ERROR)

        // Then - the open decile interval must be ended before the flow is finished.
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 60L, key = "decile_20_30_duration_ms_bucketed")
        order.verify(wideEventClient).flowFinish(
            wideEventId = 60L,
            status = FlowStatus.Failure(reason = "unknown_error"),
        )
    }

    @Test
    fun whenRunFailedDuringOptOutThenOpenOptOutIntervalIsClosedBeforeFlowFinish() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(61L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0) // zero scan jobs, no decile interval
        testee.onScanCompleted(PirExecutionType.MANUAL_INITIAL)
        testee.onOptOutStarted(PirExecutionType.MANUAL_INITIAL) // opens INTERVAL_OPT_OUT_DURATION

        // When
        testee.onRunFailed(PirExecutionType.MANUAL_INITIAL, FailureReason.UNKNOWN_ERROR)

        // Then - the open opt-out interval must be ended before flowFinish.
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 61L, key = INTERVAL_OPT_OUT_DURATION)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 61L,
            status = FlowStatus.Failure(reason = "unknown_error"),
        )
    }

    @Test
    fun whenRunCancelledDuringScanThenOpenDecileIntervalIsClosedBeforeFlowFinish() = runTest {
        // Given - cancellation fires while scan is in progress (decile_0_10 still open).
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(62L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)

        // When
        testee.onRunCancelled(PirExecutionType.MANUAL_INITIAL, CancellationReason.WORK_STOPPED)

        // Then
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 62L, key = "decile_0_10_duration_ms_bucketed")
        order.verify(wideEventClient).flowFinish(
            wideEventId = 62L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "work_stopped"),
        )
    }

    @Test
    fun whenRunCancelledDuringOptOutThenOpenOptOutIntervalIsClosedBeforeFlowFinish() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(63L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0)
        testee.onScanCompleted(PirExecutionType.MANUAL_INITIAL)
        testee.onOptOutStarted(PirExecutionType.MANUAL_INITIAL)

        // When
        testee.onRunCancelled(PirExecutionType.MANUAL_INITIAL, CancellationReason.WORK_STOPPED)

        // Then
        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 63L, key = INTERVAL_OPT_OUT_DURATION)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 63L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "work_stopped"),
        )
    }

    @Test
    fun whenOnWorkCancelledThenOpenFlowFinishedWithCancelledAndReason() = runTest {
        // Given an open manual flow with no scan jobs (so no decile interval is opened)
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(80L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0)

        // When work is cancelled externally (e.g. via PirWorkHandler.cancelWork)
        testee.onWorkCancelled(CancellationReason.PROFILE_DELETED)

        // Then
        verify(wideEventClient).flowFinish(
            wideEventId = 80L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "profile_deleted"),
        )
    }

    @Test
    fun whenOnWorkCancelledAndNoOpenFlowThenNothingIsFinished() = runTest {
        // No flow open in this process or the shared DB (getFlowIds stubbed empty in setUp).
        testee.onWorkCancelled(CancellationReason.PROFILE_DELETED)

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun whenOnWorkCancelledAndFlowStartedInAnotherProcessThenResolvesFromDbAndFinishesCancelled() = runTest {
        // Reproduces the cross-process bug: the scan ran in the :pir process (so cachedFlowId is null
        // in this process), but the shared wide-events DB has the open manual flow. cancelWork runs in
        // the main process (profile delete / eligibility loss), so we must resolve the flow from the DB.
        whenever(wideEventClient.getFlowIds(WIDE_EVENT_NAME_MANUAL)).thenReturn(Result.success(listOf(555L)))

        testee.onWorkCancelled(CancellationReason.PROFILE_DELETED)

        // last_step is not attached at finish: it was persisted per-step (via recordStep) by the
        // process that ran the scan, so it is already stored on the flow we resolved from the DB.
        verify(wideEventClient).flowFinish(
            wideEventId = 555L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "profile_deleted"),
        )
    }

    @Test
    fun whenRunCancelledBeforeStartThenOneShotFlowEmittedWithZeroedCountsAndReason() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(90L))

        testee.onRunCancelledBeforeStart(PirExecutionType.MANUAL_INITIAL, CancellationReason.FOREGROUND_START_FAILED)

        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).flowStart(
            name = WIDE_EVENT_NAME_MANUAL,
            flowEntryPoint = ENTRY_POINT_MANUAL_INITIAL,
            metadata = mapOf(
                KEY_EXECUTION_TYPE to EXECUTION_TYPE_MANUAL_INITIAL,
                KEY_PROFILE_QUERIES_COUNT to "0",
                KEY_BROKER_COUNT to "0",
                KEY_TOTAL_SCAN_JOBS to "0",
                KEY_WEB_VIEW_COUNT to "0",
            ),
            cleanupPolicy = CleanupPolicy.OnTimeout(duration = 8.hours, flowStatus = FlowStatus.Unknown),
        )
        order.verify(wideEventClient).flowStep(
            wideEventId = 90L,
            stepName = STEP_STARTED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_STARTED),
        )
        order.verify(wideEventClient).flowFinish(
            wideEventId = 90L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "foreground_start_failed"),
        )
    }

    @Test
    fun whenRunCancelledBeforeStartAndManualFlowAlreadyOpenThenReusesItWithoutDoubleEmitting() = runTest {
        // Reproduces the cause-4 sequence in PirForegroundScanService: a manual flow is still open
        // when the service is blocked at start. onRunCancelledBeforeStart is followed by cancelWork
        // (-> onWorkCancelled). The before-start hook must reuse the already-open flow instead of
        // synthesizing a second one, otherwise the follow-on onWorkCancelled produces a duplicate
        // cancelled wide event for a single user action.
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(300L)) // onRunStarted
            .thenReturn(Result.success(301L)) // any (unwanted) synthetic one-shot would get this id
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 0) // opens manual flow 300L

        testee.onRunCancelledBeforeStart(PirExecutionType.MANUAL_INITIAL, CancellationReason.SUBSCRIPTION_EXPIRED)
        testee.onWorkCancelled(CancellationReason.SUBSCRIPTION_EXPIRED)

        // No synthetic one-shot was started (only the original onRunStarted flowStart)...
        verify(wideEventClient, times(1)).flowStart(any(), any(), any(), any())
        // ...and the run is finished as Cancelled exactly once, reusing the open flow.
        verify(wideEventClient, times(1)).flowFinish(any(), eq(FlowStatus.Cancelled), any())
        verify(wideEventClient).flowFinish(
            wideEventId = 300L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to "subscription_expired"),
        )
    }

    @Test
    fun fromExceptionMapsKnownTypesToBoundedReasonsAndOtherwiseUnknown() {
        org.junit.Assert.assertEquals(
            FailureReason.SQLITE_EXCEPTION,
            FailureReason.fromException(SQLiteException("boom")),
        )
        org.junit.Assert.assertEquals(
            FailureReason.IO_EXCEPTION,
            FailureReason.fromException(IOException("boom")),
        )
        // FileNotFoundException is a subclass of IOException — must map to IO_EXCEPTION.
        org.junit.Assert.assertEquals(
            FailureReason.IO_EXCEPTION,
            FailureReason.fromException(java.io.FileNotFoundException("boom")),
        )
        org.junit.Assert.assertEquals(
            FailureReason.ILLEGAL_STATE_EXCEPTION,
            FailureReason.fromException(IllegalStateException("boom")),
        )
        org.junit.Assert.assertEquals(
            FailureReason.ILLEGAL_ARGUMENT_EXCEPTION,
            FailureReason.fromException(IllegalArgumentException("boom")),
        )
        org.junit.Assert.assertEquals(
            FailureReason.NULL_POINTER_EXCEPTION,
            FailureReason.fromException(NullPointerException("boom")),
        )
        org.junit.Assert.assertEquals(
            FailureReason.UNKNOWN_ERROR,
            FailureReason.fromException(RuntimeException("boom")),
        )
    }

    @Test
    fun fromDisabledReasonMapsEachEligibilityReasonToBoundedCancellationReason() {
        org.junit.Assert.assertEquals(
            CancellationReason.FEATURE_DISABLED,
            CancellationReason.fromDisabledReason(DisabledReason.FEATURE_DISABLED),
        )
        org.junit.Assert.assertEquals(
            CancellationReason.SUBSCRIPTION_EXPIRED,
            CancellationReason.fromDisabledReason(DisabledReason.SUBSCRIPTION_EXPIRED),
        )
        org.junit.Assert.assertEquals(
            CancellationReason.ENTITLEMENT_LOST,
            CancellationReason.fromDisabledReason(DisabledReason.ENTITLEMENT_LOST),
        )
        org.junit.Assert.assertEquals(
            CancellationReason.REPOSITORY_UNAVAILABLE,
            CancellationReason.fromDisabledReason(DisabledReason.REPOSITORY_UNAVAILABLE),
        )
    }

    @Test
    fun whenRunFailedWithMappedExceptionThenFlowFinishedWithMappedReasonValue() = runTest {
        // Given
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(99L))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)

        // When - simulating PirJobsRunner mapping an IOException via fromException.
        testee.onRunFailed(PirExecutionType.MANUAL_INITIAL, FailureReason.fromException(IOException("net")))

        // Then
        verify(wideEventClient).flowFinish(
            wideEventId = 99L,
            status = FlowStatus.Failure(reason = "io_exception"),
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

    @Test
    fun whenOnUserResetCalledAndNoFlowsOpenThenWideEventClientNotCalled() = runTest {
        testee.onUserReset()

        verify(wideEventClient, never()).flowAbort(any())
    }

    @Test
    fun whenOnUserResetCalledAndManualFlowOpenThenManualFlowAborted() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(11L))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)

        testee.onUserReset()

        verify(wideEventClient).flowAbort(11L)
    }

    @Test
    fun whenOnUserResetCalledAndScheduledFlowOpenThenScheduledFlowAborted() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(22L))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))
        runStarted(PirExecutionType.SCHEDULED, 1, 1, 1)

        testee.onUserReset()

        verify(wideEventClient).flowAbort(22L)
    }

    @Test
    fun whenOnUserResetCalledAndBothFlowsOpenThenBothAreAborted() = runTest {
        whenever(wideEventClient.flowStart(any(), eq(ENTRY_POINT_MANUAL_INITIAL), any(), any()))
            .thenReturn(Result.success(11L))
        whenever(wideEventClient.flowStart(any(), eq(ENTRY_POINT_SCHEDULED), any(), any()))
            .thenReturn(Result.success(22L))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)
        runStarted(PirExecutionType.SCHEDULED, 1, 1, 1)

        testee.onUserReset()

        verify(wideEventClient).flowAbort(11L)
        verify(wideEventClient).flowAbort(22L)
    }

    @Test
    fun whenOnUserResetCalledAndManualFlowOpenThenStateClearedSoNextRunStartsFresh() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(11L), Result.success(33L))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)
        testee.onUserReset()

        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)

        // The new run must NOT close the prior cached flow as a stale-flow cleanup —
        // onUserReset() should already have aborted and cleared it.
        verify(wideEventClient, never()).flowFinish(eq(11L), any(), any())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenOnUserResetCalledAndFeatureFlagDisabledMidFlightThenInFlightFlowStillAborted() = runTest {
        // Flow opens while flag is ON, then flag flips OFF before user reset. We must still abort
        // the in-flight flow so it doesn't get auto-finished as Unknown by the cleanup timeout.
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(11L))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 1)
        pirRemoteFeatures.sendScanWideEvent().setRawStoredState(Toggle.State(false))

        testee.onUserReset()

        verify(wideEventClient).flowAbort(11L)
    }

    @Test
    fun whenStepReachedAfterTimeElapsedThenLastStepElapsedIsBucketedSinceRunStart() = runTest {
        // Given - the run starts at t=1s; a 10-job scan.
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(73L))
        timeProvider.elapsed = 1_000
        runStarted(PirExecutionType.MANUAL_INITIAL, 1, 1, 10)

        // When - 65s of wall-clock pass, then the first job completes (crosses into progress_10).
        timeProvider.elapsed = 66_000
        testee.onScanJobCompleted(PirExecutionType.MANUAL_INITIAL)

        // Then - the step carries elapsed-since-run-start, floored to the 60s bucket. This is the
        // value that survives a process kill and lets us tell how long an UNKNOWN run had been going.
        verify(wideEventClient).flowStep(
            wideEventId = 73L,
            stepName = "progress_10",
            success = true,
            metadata = mapOf(KEY_LAST_STEP to "progress_10", KEY_LAST_STEP_ELAPSED to "60000"),
        )
        // The initial 'started' step omits elapsed (it is ~0 and carries no signal).
        verify(wideEventClient).flowStep(
            wideEventId = 73L,
            stepName = STEP_STARTED,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to STEP_STARTED),
        )
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var elapsed: Long = 0L
        override fun elapsedRealtime(): Long = elapsed
        override fun currentTimeMillis(): Long = elapsed
        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
    }
}
