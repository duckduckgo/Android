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

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

interface PirScanWideEvent {
    suspend fun onRunStarted(
        executionType: PirExecutionType,
        profileQueriesCount: Int,
        brokerCount: Int,
        totalScanJobs: Int,
        webViewCount: Int,
        isPowerSavingEnabled: Boolean,
        isVpnConnected: Boolean,
        batteryOptimizationsEnabled: Boolean,
        notificationsPermissionGranted: Boolean,
        isTrackerBlockingEnabled: Boolean,
    )

    /**
     * Called once after [com.duckduckgo.pir.impl.scan.PirScan] has resolved which scan jobs will
     * actually run (i.e. after its internal `processJobRecords` filtering for missing broker steps
     * / unresolved profiles). The wide event uses [actualScanJobs] to drive decile math so progress
     * steps reflect real progress against jobs that will actually execute, not the pre-filter
     * estimate that `onRunStarted` was given.
     */
    suspend fun onScanJobsResolved(executionType: PirExecutionType, actualScanJobs: Int)

    suspend fun onScanJobCompleted(executionType: PirExecutionType)

    suspend fun onScanCompleted(executionType: PirExecutionType)

    suspend fun onOptOutStarted(executionType: PirExecutionType)

    suspend fun onOptOutCompleted(executionType: PirExecutionType, totalOptOutJobs: Int)

    suspend fun onOptOutSkipped(executionType: PirExecutionType)

    suspend fun onRunFailed(executionType: PirExecutionType, reason: String)

    suspend fun onRunCancelled(executionType: PirExecutionType)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PirScanWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val dispatchers: DispatcherProvider,
) : PirScanWideEvent {

    private val manualState = RunState(WIDE_EVENT_NAME_MANUAL)
    private val scheduledState = RunState(WIDE_EVENT_NAME_SCHEDULED)

    /**
     * Hook used to decide whether the current scheduled run is sampled in. Overridable from tests
     * so we can deterministically force scheduled runs to fire (or not) without depending on
     * `Random`. Production keeps the default coin flip against [SCHEDULED_SCAN_SAMPLE_RATE].
     */
    @VisibleForTesting
    internal var sampleScheduledIn: () -> Boolean = { Random.nextDouble() < SCHEDULED_SCAN_SAMPLE_RATE }

    override suspend fun onRunStarted(
        executionType: PirExecutionType,
        profileQueriesCount: Int,
        brokerCount: Int,
        totalScanJobs: Int,
        webViewCount: Int,
        isPowerSavingEnabled: Boolean,
        isVpnConnected: Boolean,
        batteryOptimizationsEnabled: Boolean,
        notificationsPermissionGranted: Boolean,
        isTrackerBlockingEnabled: Boolean,
    ) {
        if (!isFeatureEnabled()) return
        if (executionType == PirExecutionType.SCHEDULED && !sampleScheduledIn()) return

        stateFor(executionType).onRunStarted(
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

    override suspend fun onScanJobsResolved(executionType: PirExecutionType, actualScanJobs: Int) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onScanJobsResolved(actualScanJobs)
    }

    override suspend fun onScanJobCompleted(executionType: PirExecutionType) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onScanJobCompleted()
    }

    override suspend fun onScanCompleted(executionType: PirExecutionType) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onScanCompleted()
    }

    override suspend fun onOptOutStarted(executionType: PirExecutionType) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onOptOutStarted()
    }

    override suspend fun onOptOutCompleted(executionType: PirExecutionType, totalOptOutJobs: Int) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onOptOutCompleted(totalOptOutJobs)
    }

    override suspend fun onOptOutSkipped(executionType: PirExecutionType) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onOptOutSkipped()
    }

    override suspend fun onRunFailed(executionType: PirExecutionType, reason: String) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onRunFailed(reason)
    }

    override suspend fun onRunCancelled(executionType: PirExecutionType) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onRunCancelled()
    }

    private fun stateFor(executionType: PirExecutionType): RunState = when (executionType) {
        PirExecutionType.MANUAL_INITIAL, PirExecutionType.MANUAL_EDIT_PROFILE -> manualState
        PirExecutionType.SCHEDULED -> scheduledState
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        pirRemoteFeatures.sendScanWideEvent().isEnabled()
    }

    /**
     * Per-flow state. Two instances live in the singleton (manual and scheduled) so the two flows
     * never alias on `cachedFlowId`, decile counters, or the `getFlowIds(name)` lookup.
     */
    private inner class RunState(private val wideEventName: String) {
        private val mutex = Mutex()
        private var cachedFlowId: Long? = null
        private var totalScanJobs: Int = 0
        private var completedScanJobs: Int = 0
        private var lastReportedDecile: Int = 0
        private var currentDecileIntervalKey: String? = null
        private var lastStep: String = STEP_STARTED

        suspend fun onRunStarted(
            executionType: PirExecutionType,
            profileQueriesCount: Int,
            brokerCount: Int,
            totalScanJobs: Int,
            webViewCount: Int,
            isPowerSavingEnabled: Boolean,
            isVpnConnected: Boolean,
            batteryOptimizationsEnabled: Boolean,
            notificationsPermissionGranted: Boolean,
            isTrackerBlockingEnabled: Boolean,
        ) {
            mutex.withLock {
                // Finish any stale in-memory flow with Cancelled so we don't leave two open flows of
                // the same name in the wide-events DB.
                cachedFlowId?.let { staleId ->
                    wideEventClient.flowFinish(
                        wideEventId = staleId,
                        status = FlowStatus.Cancelled,
                        metadata = mapOf(KEY_LAST_STEP to lastStep),
                    )
                    clearStateLocked()
                }

                this.totalScanJobs = totalScanJobs
                this.completedScanJobs = 0
                this.lastReportedDecile = 0
                this.currentDecileIntervalKey = null
                this.lastStep = STEP_STARTED

                val newFlowId = wideEventClient.flowStart(
                    name = wideEventName,
                    flowEntryPoint = entryPointFor(executionType),
                    metadata = mapOf(
                        KEY_EXECUTION_TYPE to executionType.metadataValue(),
                        KEY_PROFILE_QUERIES_COUNT to profileQueriesCount.toString(),
                        KEY_BROKER_COUNT to brokerCount.toString(),
                        KEY_TOTAL_SCAN_JOBS to totalScanJobs.toString(),
                        KEY_WEB_VIEW_COUNT to webViewCount.toString(),
                        KEY_POWER_SAVING to isPowerSavingEnabled.toString(),
                        KEY_BATTERY_OPTIMIZATIONS to batteryOptimizationsEnabled.toString(),
                        KEY_VPN_CONNECTION_STATE to isVpnConnected.toVpnConnectionState(),
                        KEY_NOTIFICATIONS_PERMISSION_GRANTED to notificationsPermissionGranted.toString(),
                        KEY_TRACKER_BLOCKING_STATE to isTrackerBlockingEnabled.toTrackerBlockingState(),
                    ),
                    cleanupPolicy = CleanupPolicy.OnTimeout(
                        duration = 8.hours,
                        flowStatus = FlowStatus.Unknown,
                    ),
                ).getOrNull() ?: return@withLock

                cachedFlowId = newFlowId
                wideEventClient.flowStep(wideEventId = newFlowId, stepName = STEP_STARTED, success = true)

                if (totalScanJobs > 0) {
                    val key = decileIntervalKey(0, 10)
                    wideEventClient.intervalStart(wideEventId = newFlowId, key = key)
                    currentDecileIntervalKey = key
                }
            }
        }

        suspend fun onScanJobsResolved(actualScanJobs: Int) {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                if (actualScanJobs == totalScanJobs) return@withLock

                totalScanJobs = actualScanJobs

                // If the resolved count is 0, no scan jobs will run. Close any open decile
                // interval (we opened decile_0_10 in onRunStarted when the pre-filter estimate
                // was non-zero) so we don't carry it through to scan_completed.
                if (actualScanJobs == 0) {
                    currentDecileIntervalKey?.let {
                        wideEventClient.intervalEnd(wideEventId = flowId, key = it)
                        currentDecileIntervalKey = null
                    }
                }
            }
        }

        suspend fun onScanJobCompleted() {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                if (totalScanJobs == 0) return@withLock

                completedScanJobs = (completedScanJobs + 1).coerceAtMost(totalScanJobs)
                val currentDecile = ((completedScanJobs * 10) / totalScanJobs).coerceAtMost(9)

                if (currentDecile > lastReportedDecile) {
                    currentDecileIntervalKey?.let {
                        wideEventClient.intervalEnd(wideEventId = flowId, key = it)
                    }

                    val stepName = "$STEP_PROGRESS_PREFIX${currentDecile * 10}"
                    wideEventClient.flowStep(wideEventId = flowId, stepName = stepName, success = true)
                    lastStep = stepName

                    if (currentDecile < 9) {
                        val nextKey = decileIntervalKey(currentDecile * 10, (currentDecile + 1) * 10)
                        wideEventClient.intervalStart(wideEventId = flowId, key = nextKey)
                        currentDecileIntervalKey = nextKey
                    } else {
                        currentDecileIntervalKey = null
                    }

                    lastReportedDecile = currentDecile
                }
            }
        }

        suspend fun onScanCompleted() {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock

                currentDecileIntervalKey?.let {
                    wideEventClient.intervalEnd(wideEventId = flowId, key = it)
                    currentDecileIntervalKey = null
                }

                wideEventClient.flowStep(wideEventId = flowId, stepName = STEP_SCAN_COMPLETED, success = true)
                lastStep = STEP_SCAN_COMPLETED
            }
        }

        suspend fun onOptOutStarted() {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                wideEventClient.flowStep(wideEventId = flowId, stepName = STEP_OPT_OUT_STARTED, success = true)
                wideEventClient.intervalStart(wideEventId = flowId, key = INTERVAL_OPT_OUT_DURATION)
                lastStep = STEP_OPT_OUT_STARTED
            }
        }

        suspend fun onOptOutCompleted(totalOptOutJobs: Int) {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_OPT_OUT_DURATION)
                wideEventClient.flowStep(wideEventId = flowId, stepName = STEP_OPT_OUT_COMPLETED, success = true)
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Success,
                    metadata = mapOf(
                        KEY_LAST_STEP to STEP_OPT_OUT_COMPLETED,
                        KEY_TOTAL_OPT_OUT_JOBS to totalOptOutJobs.toString(),
                    ),
                )
                clearStateLocked()
            }
        }

        suspend fun onOptOutSkipped() {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                wideEventClient.flowStep(wideEventId = flowId, stepName = STEP_OPT_OUT_SKIPPED, success = true)
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Success,
                    metadata = mapOf(
                        KEY_LAST_STEP to STEP_OPT_OUT_SKIPPED,
                        KEY_TOTAL_OPT_OUT_JOBS to "0",
                    ),
                )
                clearStateLocked()
            }
        }

        suspend fun onRunFailed(reason: String) {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                val finalStep = lastStep
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Failure(reason = reason),
                    metadata = mapOf(KEY_LAST_STEP to finalStep),
                )
                clearStateLocked()
            }
        }

        suspend fun onRunCancelled() {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                val finalStep = lastStep
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Cancelled,
                    metadata = mapOf(KEY_LAST_STEP to finalStep),
                )
                clearStateLocked()
            }
        }

        private fun clearStateLocked() {
            cachedFlowId = null
            totalScanJobs = 0
            completedScanJobs = 0
            lastReportedDecile = 0
            currentDecileIntervalKey = null
            lastStep = STEP_STARTED
        }
    }

    private fun entryPointFor(executionType: PirExecutionType): String = when (executionType) {
        PirExecutionType.MANUAL_INITIAL -> ENTRY_POINT_MANUAL_INITIAL
        PirExecutionType.MANUAL_EDIT_PROFILE -> ENTRY_POINT_MANUAL_EDIT_PROFILE
        PirExecutionType.SCHEDULED -> ENTRY_POINT_SCHEDULED
    }

    private fun PirExecutionType.metadataValue(): String = when (this) {
        PirExecutionType.MANUAL_INITIAL -> EXECUTION_TYPE_MANUAL_INITIAL
        PirExecutionType.MANUAL_EDIT_PROFILE -> EXECUTION_TYPE_MANUAL_EDIT_PROFILE
        PirExecutionType.SCHEDULED -> EXECUTION_TYPE_SCHEDULED
    }

    private fun decileIntervalKey(from: Int, to: Int): String = "decile_${from}_${to}_duration_ms_bucketed"

    private fun Boolean.toVpnConnectionState(): String = if (this) "connected" else "disconnected"

    private fun Boolean.toTrackerBlockingState(): String = if (this) "enabled" else "disabled"

    companion object {
        const val WIDE_EVENT_NAME_MANUAL = "pir-initial-scan"
        const val WIDE_EVENT_NAME_SCHEDULED = "pir-scheduled-scan"

        const val ENTRY_POINT_MANUAL_INITIAL = "funnel_pir_initial_android"
        const val ENTRY_POINT_MANUAL_EDIT_PROFILE = "funnel_pir_edit_profile_android"
        const val ENTRY_POINT_SCHEDULED = "funnel_pir_scheduled_android"

        const val EXECUTION_TYPE_MANUAL_INITIAL = "manual_initial"
        const val EXECUTION_TYPE_MANUAL_EDIT_PROFILE = "manual_edit_profile"
        const val EXECUTION_TYPE_SCHEDULED = "scheduled"

        const val KEY_EXECUTION_TYPE = "execution_type"
        const val KEY_PROFILE_QUERIES_COUNT = "profile_queries_count"
        const val KEY_BROKER_COUNT = "broker_count"
        const val KEY_TOTAL_SCAN_JOBS = "total_scan_jobs"
        const val KEY_WEB_VIEW_COUNT = "web_view_count"
        const val KEY_TOTAL_OPT_OUT_JOBS = "total_opt_out_jobs"
        const val KEY_POWER_SAVING = "power_saving"
        const val KEY_BATTERY_OPTIMIZATIONS = "battery_optimizations"
        const val KEY_VPN_CONNECTION_STATE = "vpn_connection_state"
        const val KEY_NOTIFICATIONS_PERMISSION_GRANTED = "notifications_permission_granted"
        const val KEY_TRACKER_BLOCKING_STATE = "tracker_blocking_state"
        const val KEY_LAST_STEP = "last_step"

        const val STEP_STARTED = "started"
        const val STEP_PROGRESS_PREFIX = "progress_"
        const val STEP_SCAN_COMPLETED = "scan_completed"
        const val STEP_OPT_OUT_STARTED = "opt_out_started"
        const val STEP_OPT_OUT_COMPLETED = "opt_out_completed"
        const val STEP_OPT_OUT_SKIPPED = "opt_out_skipped"

        const val INTERVAL_OPT_OUT_DURATION = "opt_out_duration_ms_bucketed"

        /** Hardcoded sample rate for scheduled-scan wide events. Manual runs always send. */
        const val SCHEDULED_SCAN_SAMPLE_RATE: Double = 0.20
    }
}
