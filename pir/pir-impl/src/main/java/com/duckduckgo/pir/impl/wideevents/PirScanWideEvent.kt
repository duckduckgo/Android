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

import android.database.sqlite.SQLiteException
import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.checker.DisabledReason
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    suspend fun onRunFailed(executionType: PirExecutionType, reason: FailureReason)

    suspend fun onRunCancelled(executionType: PirExecutionType, reason: CancellationReason)

    suspend fun onWorkCancelled(reason: CancellationReason)

    suspend fun onRunCancelledBeforeStart(executionType: PirExecutionType, reason: CancellationReason)

    /**
     * Aborts any open manual or scheduled flow so they are discarded rather than eventually
     * emitting a stale `FlowStatus.Unknown` record via the cleanup-on-timeout policy.
     */
    suspend fun onUserReset()

    enum class CancellationReason(val value: String) {
        SUPERSEDED_BY_NEW_RUN("superseded_by_new_run"),
        SUPERSEDED_BY_PROFILE_EDIT("superseded_by_profile_edit"),
        PROFILE_DELETED("profile_deleted"),
        FEATURE_DISABLED("feature_disabled"),
        SUBSCRIPTION_EXPIRED("subscription_expired"),
        ENTITLEMENT_LOST("entitlement_lost"),
        REPOSITORY_UNAVAILABLE("repository_unavailable"),
        FOREGROUND_START_FAILED("foreground_start_failed"),
        WORK_STOPPED("work_stopped"),
        ;

        companion object {
            fun fromDisabledReason(reason: DisabledReason): CancellationReason = when (reason) {
                DisabledReason.FEATURE_DISABLED -> FEATURE_DISABLED
                DisabledReason.SUBSCRIPTION_EXPIRED -> SUBSCRIPTION_EXPIRED
                DisabledReason.ENTITLEMENT_LOST -> ENTITLEMENT_LOST
                DisabledReason.REPOSITORY_UNAVAILABLE -> REPOSITORY_UNAVAILABLE
            }
        }
    }

    enum class FailureReason(val value: String) {
        NO_ACTIVE_BROKERS("no_active_brokers"),
        ILLEGAL_STATE_EXCEPTION("illegal_state_exception"),
        ILLEGAL_ARGUMENT_EXCEPTION("illegal_argument_exception"),
        NULL_POINTER_EXCEPTION("null_pointer_exception"),
        IO_EXCEPTION("io_exception"),
        SQLITE_EXCEPTION("sqlite_exception"),
        TIMEOUT_CANCELLATION_EXCEPTION("timeout_cancellation_exception"),
        UNKNOWN_ERROR("unknown_error"),
        ;

        companion object {
            // Order is intentional: more specific subclasses must come before their supertypes.
            // TIMEOUT_CANCELLATION_EXCEPTION is intentionally not handled here — TimeoutCancellationException
            // extends CancellationException, which the runner catches separately and routes via an
            // explicit catch block ahead of the generic Exception catch that calls this helper.
            fun fromException(e: Exception): FailureReason = when (e) {
                is SQLiteException -> SQLITE_EXCEPTION
                is IOException -> IO_EXCEPTION
                is IllegalStateException -> ILLEGAL_STATE_EXCEPTION
                is IllegalArgumentException -> ILLEGAL_ARGUMENT_EXCEPTION
                is NullPointerException -> NULL_POINTER_EXCEPTION
                else -> UNKNOWN_ERROR
            }
        }
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PirScanWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val dispatchers: DispatcherProvider,
    private val currentTimeProvider: CurrentTimeProvider,
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

    override suspend fun onRunFailed(executionType: PirExecutionType, reason: PirScanWideEvent.FailureReason) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onRunFailed(reason)
    }

    override suspend fun onRunCancelled(executionType: PirExecutionType, reason: PirScanWideEvent.CancellationReason) {
        if (!isFeatureEnabled()) return
        stateFor(executionType).onRunCancelled(reason)
    }

    override suspend fun onWorkCancelled(reason: PirScanWideEvent.CancellationReason) {
        manualState.onRunCancelled(reason)
        scheduledState.onRunCancelled(reason)
    }

    override suspend fun onRunCancelledBeforeStart(
        executionType: PirExecutionType,
        reason: PirScanWideEvent.CancellationReason,
    ) {
        if (!isFeatureEnabled()) return

        // If a flow for this run is already open, finalize that one
        if (stateFor(executionType).onRunCancelled(reason)) return

        // No open flow: synthesize a one-shot Cancelled flow to record the never-started run.
        val flowId = wideEventClient.flowStart(
            name = wideEventNameFor(executionType),
            flowEntryPoint = entryPointFor(executionType),
            metadata = mapOf(
                KEY_EXECUTION_TYPE to executionType.metadataValue(),
                KEY_PROFILE_QUERIES_COUNT to "0",
                KEY_BROKER_COUNT to "0",
                KEY_TOTAL_SCAN_JOBS to "0",
                KEY_WEB_VIEW_COUNT to "0",
            ),
            cleanupPolicy = CleanupPolicy.OnTimeout(
                duration = TIMEOUT_DURATION,
                flowStatus = FlowStatus.Unknown,
            ),
        ).getOrNull() ?: return

        recordStep(flowId, STEP_STARTED)
        wideEventClient.flowFinish(
            wideEventId = flowId,
            status = FlowStatus.Cancelled,
            metadata = mapOf(KEY_CANCELLATION_REASON to reason.value),
        )
    }

    override suspend fun onUserReset() {
        manualState.onUserReset()
        scheduledState.onUserReset()
    }

    private fun stateFor(executionType: PirExecutionType): RunState = when (executionType) {
        PirExecutionType.MANUAL_INITIAL, PirExecutionType.MANUAL_EDIT_PROFILE, PirExecutionType.MANUAL_INITIAL_RESUME -> manualState
        PirExecutionType.SCHEDULED -> scheduledState
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        pirRemoteFeatures.sendScanWideEvent().isEnabled()
    }

    /**
     * Records a flow step and persists [stepName] as `last_step` metadata in the same write. When
     * [elapsedMs] is provided, also persists the bucketed time-since-run-start as
     * `last_step_elapsed_ms_bucketed`. Because flowStep metadata is merged into the event and
     * persisted immediately, the furthest-reached step and its elapsed time survive an abnormal
     * termination — e.g. the `:pir` process being killed mid-scan and the flow finalized as
     * [FlowStatus.Unknown] by the cleanup-on-timeout policy, which never calls flowFinish. That is the
     * only way to recover how long a killed run had been going, since the `total_*_duration` intervals
     * are never closed (and thus never recorded) on such a run.
     *
     * [elapsedMs] is omitted for the initial `started` step (elapsed is ~0 there and carries no
     * signal); the field therefore being absent on a flow means it was killed before the first
     * progress decile.
     */
    private suspend fun recordStep(flowId: Long, stepName: String, elapsedMs: Long? = null) {
        val metadata = buildMap {
            put(KEY_LAST_STEP, stepName)
            elapsedMs?.let { put(KEY_LAST_STEP_ELAPSED, bucketedElapsedMs(it)) }
        }
        wideEventClient.flowStep(
            wideEventId = flowId,
            stepName = stepName,
            success = true,
            metadata = metadata,
        )
    }

    /**
     * Floors [elapsedMs] to the nearest [PER_RUN_DURATION_BUCKETS] boundary (values below the smallest
     * bucket become 0), mirroring how the wide-events framework buckets interval durations.
     */
    private fun bucketedElapsedMs(elapsedMs: Long): String {
        val matched = PER_RUN_DURATION_BUCKETS
            .filter { it.inWholeMilliseconds <= elapsedMs }
            .maxByOrNull { it.inWholeMilliseconds }
        return (matched?.inWholeMilliseconds ?: 0L).toString()
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
        private var optOutIntervalOpen: Boolean = false
        private var totalScanIntervalOpen: Boolean = false
        private var totalFlowIntervalOpen: Boolean = false
        private var runStartElapsedMs: Long = 0

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
                // the same name in the wide-events DB. A still-open flow here means a new run started
                // before the previous one finished, so it was superseded.
                cachedFlowId?.let { staleId ->
                    closeOpenIntervalsLocked(staleId)
                    val supersededReason = if (executionType == PirExecutionType.MANUAL_EDIT_PROFILE) {
                        PirScanWideEvent.CancellationReason.SUPERSEDED_BY_PROFILE_EDIT
                    } else {
                        PirScanWideEvent.CancellationReason.SUPERSEDED_BY_NEW_RUN
                    }
                    wideEventClient.flowFinish(
                        wideEventId = staleId,
                        status = FlowStatus.Cancelled,
                        metadata = mapOf(KEY_CANCELLATION_REASON to supersededReason.value),
                    )
                    clearStateLocked()
                }

                this.totalScanJobs = totalScanJobs
                this.completedScanJobs = 0
                this.lastReportedDecile = 0
                this.currentDecileIntervalKey = null

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
                        duration = TIMEOUT_DURATION,
                        flowStatus = FlowStatus.Unknown,
                    ),
                ).getOrNull() ?: return@withLock

                cachedFlowId = newFlowId
                runStartElapsedMs = currentTimeProvider.elapsedRealtime()
                recordStep(newFlowId, STEP_STARTED)

                wideEventClient.intervalStart(
                    wideEventId = newFlowId,
                    key = INTERVAL_TOTAL_FLOW_DURATION,
                    buckets = PER_RUN_DURATION_BUCKETS,
                )
                totalFlowIntervalOpen = true

                wideEventClient.intervalStart(
                    wideEventId = newFlowId,
                    key = INTERVAL_TOTAL_SCAN_DURATION,
                    buckets = PER_RUN_DURATION_BUCKETS,
                )
                totalScanIntervalOpen = true

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
                    recordStep(flowId, stepName, elapsedSinceStart())

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

                if (totalScanIntervalOpen) {
                    wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_TOTAL_SCAN_DURATION)
                    totalScanIntervalOpen = false
                }

                recordStep(flowId, STEP_SCAN_COMPLETED, elapsedSinceStart())
            }
        }

        suspend fun onOptOutStarted() {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                recordStep(flowId, STEP_OPT_OUT_STARTED, elapsedSinceStart())
                wideEventClient.intervalStart(wideEventId = flowId, key = INTERVAL_OPT_OUT_DURATION)
                optOutIntervalOpen = true
            }
        }

        suspend fun onOptOutCompleted(totalOptOutJobs: Int) {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_OPT_OUT_DURATION)
                optOutIntervalOpen = false
                recordStep(flowId, STEP_OPT_OUT_COMPLETED, elapsedSinceStart())
                if (totalFlowIntervalOpen) {
                    wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_TOTAL_FLOW_DURATION)
                    totalFlowIntervalOpen = false
                }
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Success,
                    metadata = mapOf(KEY_TOTAL_OPT_OUT_JOBS to totalOptOutJobs.toString()),
                )
                clearStateLocked()
            }
        }

        suspend fun onOptOutSkipped() {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                recordStep(flowId, STEP_OPT_OUT_SKIPPED, elapsedSinceStart())
                if (totalFlowIntervalOpen) {
                    wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_TOTAL_FLOW_DURATION)
                    totalFlowIntervalOpen = false
                }
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Success,
                    metadata = mapOf(KEY_TOTAL_OPT_OUT_JOBS to "0"),
                )
                clearStateLocked()
            }
        }

        suspend fun onRunFailed(reason: PirScanWideEvent.FailureReason) {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                closeOpenIntervalsLocked(flowId)
                // last_step is already persisted per-step via recordStep, so it stays on the flow
                // even when this finish is never reached (Unknown timeout cleanup).
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Failure(reason = reason.value),
                )
                clearStateLocked()
            }
        }

        /**
         * Finishes the open flow (if any) as Cancelled with [reason]. Returns true if a flow was
         * finalized, false if there was nothing open — letting callers avoid synthesizing a
         * duplicate flow.
         */
        suspend fun onRunCancelled(reason: PirScanWideEvent.CancellationReason): Boolean =
            mutex.withLock {
                // The flow may have been started in a different process (the :pir scan service) than
                // the one handling the cancellation (the main process, for profile deletion or
                // eligibility loss). cachedFlowId is per-process in-memory state, so fall back to the
                // shared wide-events DB to resolve the still-open flow by name.
                val flowId = cachedFlowId
                    ?: wideEventClient.getFlowIds(wideEventName).getOrNull()?.lastOrNull()
                    ?: return@withLock false
                closeOpenIntervalsLocked(flowId)
                // last_step is persisted incrementally via recordStep, so it is already stored
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Cancelled,
                    metadata = mapOf(KEY_CANCELLATION_REASON to reason.value),
                )
                clearStateLocked()
                true
            }

        suspend fun onUserReset() {
            mutex.withLock {
                val flowId = cachedFlowId ?: return@withLock
                wideEventClient.flowAbort(flowId)
                clearStateLocked()
            }
        }

        /**
         * Closes any intervals left open by the in-progress flow before it terminates abnormally
         * (failure, cancellation, or stale-flow cleanup on a re-run). Without this, dangling open
         * intervals would corrupt timing analytics for non-success runs.
         */
        private suspend fun closeOpenIntervalsLocked(flowId: Long) {
            currentDecileIntervalKey?.let {
                wideEventClient.intervalEnd(wideEventId = flowId, key = it)
                currentDecileIntervalKey = null
            }
            if (optOutIntervalOpen) {
                wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_OPT_OUT_DURATION)
                optOutIntervalOpen = false
            }
            if (totalScanIntervalOpen) {
                wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_TOTAL_SCAN_DURATION)
                totalScanIntervalOpen = false
            }
            if (totalFlowIntervalOpen) {
                wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_TOTAL_FLOW_DURATION)
                totalFlowIntervalOpen = false
            }
        }

        /**
         * Wall-clock time since this run's flow started.
         */
        private fun elapsedSinceStart(): Long =
            (currentTimeProvider.elapsedRealtime() - runStartElapsedMs).coerceAtLeast(0)

        private fun clearStateLocked() {
            cachedFlowId = null
            totalScanJobs = 0
            completedScanJobs = 0
            lastReportedDecile = 0
            currentDecileIntervalKey = null
            optOutIntervalOpen = false
            totalScanIntervalOpen = false
            totalFlowIntervalOpen = false
            runStartElapsedMs = 0
        }
    }

    private fun entryPointFor(executionType: PirExecutionType): String = when (executionType) {
        PirExecutionType.MANUAL_INITIAL -> ENTRY_POINT_MANUAL_INITIAL
        PirExecutionType.MANUAL_EDIT_PROFILE -> ENTRY_POINT_MANUAL_EDIT_PROFILE
        PirExecutionType.MANUAL_INITIAL_RESUME -> ENTRY_POINT_MANUAL_INITIAL_RESUME
        PirExecutionType.SCHEDULED -> ENTRY_POINT_SCHEDULED
    }

    private fun wideEventNameFor(executionType: PirExecutionType): String = when (executionType) {
        PirExecutionType.MANUAL_INITIAL, PirExecutionType.MANUAL_EDIT_PROFILE, PirExecutionType.MANUAL_INITIAL_RESUME -> WIDE_EVENT_NAME_MANUAL
        PirExecutionType.SCHEDULED -> WIDE_EVENT_NAME_SCHEDULED
    }

    private fun PirExecutionType.metadataValue(): String = when (this) {
        PirExecutionType.MANUAL_INITIAL -> EXECUTION_TYPE_MANUAL_INITIAL
        PirExecutionType.MANUAL_EDIT_PROFILE -> EXECUTION_TYPE_MANUAL_EDIT_PROFILE
        PirExecutionType.MANUAL_INITIAL_RESUME -> EXECUTION_TYPE_MANUAL_INITIAL_RESUME
        PirExecutionType.SCHEDULED -> EXECUTION_TYPE_SCHEDULED
    }

    private fun decileIntervalKey(from: Int, to: Int): String = "decile_${from}_${to}_duration_ms_bucketed"

    private fun Boolean.toVpnConnectionState(): String = if (this) "connected" else "disconnected"

    private fun Boolean.toTrackerBlockingState(): String = if (this) "enabled" else "disabled"

    companion object {
        private val TIMEOUT_DURATION = 8.hours

        const val WIDE_EVENT_NAME_MANUAL = "pir-initial-scan"
        const val WIDE_EVENT_NAME_SCHEDULED = "pir-scheduled-scan"

        const val ENTRY_POINT_MANUAL_INITIAL = "funnel_pir_initial_android"
        const val ENTRY_POINT_MANUAL_EDIT_PROFILE = "funnel_pir_edit_profile_android"
        const val ENTRY_POINT_MANUAL_INITIAL_RESUME = "funnel_pir_initial_resume_android"
        const val ENTRY_POINT_SCHEDULED = "funnel_pir_scheduled_android"

        const val EXECUTION_TYPE_MANUAL_INITIAL = "manual_initial"
        const val EXECUTION_TYPE_MANUAL_EDIT_PROFILE = "manual_edit_profile"
        const val EXECUTION_TYPE_MANUAL_INITIAL_RESUME = "manual_initial_resume"
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
        const val KEY_LAST_STEP_ELAPSED = "last_step_elapsed_ms_bucketed"
        const val KEY_CANCELLATION_REASON = "cancellation_reason"

        const val STEP_STARTED = "started"
        const val STEP_PROGRESS_PREFIX = "progress_"
        const val STEP_SCAN_COMPLETED = "scan_completed"
        const val STEP_OPT_OUT_STARTED = "opt_out_started"
        const val STEP_OPT_OUT_COMPLETED = "opt_out_completed"
        const val STEP_OPT_OUT_SKIPPED = "opt_out_skipped"

        const val INTERVAL_OPT_OUT_DURATION = "opt_out_duration_ms_bucketed"
        const val INTERVAL_TOTAL_SCAN_DURATION = "total_scan_duration_ms_bucketed"
        const val INTERVAL_TOTAL_FLOW_DURATION = "total_flow_duration_ms_bucketed"

        val PER_RUN_DURATION_BUCKETS = setOf(
            10.seconds,
            30.seconds,
            1.minutes,
            2.minutes,
            5.minutes,
            10.minutes,
            15.minutes,
            30.minutes,
            1.hours,
            2.hours,
            4.hours,
            8.hours,
        )

        /** Hardcoded sample rate for scheduled-scan wide events. Manual runs always send. */
        const val SCHEDULED_SCAN_SAMPLE_RATE: Double = 0.20
    }
}
