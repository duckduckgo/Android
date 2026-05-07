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

import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.duckduckgo.pir.impl.store.PirDataStore
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

/**
 * Long-lived wide event that measures the wall-clock duration from the very first time a user
 * starts the initial scan to the moment the dashboard would first show "all brokers scanned".
 *
 * Spans multiple foreground service runs and scheduled worker runs, since the foreground service
 * can be killed and the worker resumes the work later.
 *
 * One-shot per install: once started, never starts again unless the user fully resets PIR data.
 */
interface PirInitialScanCompletionWideEvent {

    /**
     * Called by `PirJobsRunner` when a scan run begins. Starts the long-lived flow on the very
     * first MANUAL_INITIAL run, and increments the foreground/scheduled run counter while a flow
     * is open.
     */
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
     * Called by `PirJobsRunner` after the scan phase of a run completes. If a flow is open and
     * every valid scan job now has a `lastScanDateInMillis > 0`, finishes the flow with Success.
     */
    suspend fun onScanCompleted()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PirInitialScanCompletionWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val pirDataStore: PirDataStore,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val dispatchers: DispatcherProvider,
) : PirInitialScanCompletionWideEvent {

    private val mutex = Mutex()

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

        withContext(dispatchers.io()) {
            mutex.withLock {
                val flowAlreadyOpen = pirDataStore.initialScanCompletionFlowId != 0L

                if (!flowAlreadyOpen) {
                    if (executionType != PirExecutionType.MANUAL_INITIAL) return@withLock
                    if (pirDataStore.hasInitialScanEverStarted) return@withLock

                    val newFlowId = wideEventClient.flowStart(
                        name = WIDE_EVENT_NAME,
                        flowEntryPoint = ENTRY_POINT,
                        metadata = mapOf(
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
                            duration = COMPLETION_FLOW_TIMEOUT,
                            flowStatus = FlowStatus.Unknown,
                        ),
                    ).getOrNull() ?: return@withLock

                    pirDataStore.initialScanCompletionFlowId = newFlowId
                    pirDataStore.hasInitialScanEverStarted = true
                    pirDataStore.initialScanCompletionForegroundRunCount = 0
                    pirDataStore.initialScanCompletionScheduledRunCount = 0
                    wideEventClient.intervalStart(wideEventId = newFlowId, key = INTERVAL_TOTAL_DURATION)
                }

                when (executionType) {
                    PirExecutionType.MANUAL_INITIAL,
                    PirExecutionType.MANUAL_EDIT_PROFILE,
                    -> {
                        pirDataStore.initialScanCompletionForegroundRunCount += 1
                    }
                    PirExecutionType.SCHEDULED -> {
                        pirDataStore.initialScanCompletionScheduledRunCount += 1
                    }
                }
            }
        }
    }

    override suspend fun onScanCompleted() {
        if (!isFeatureEnabled()) return

        withContext(dispatchers.io()) {
            mutex.withLock {
                val flowId = pirDataStore.initialScanCompletionFlowId
                if (flowId == 0L) return@withLock

                val scanJobs = pirSchedulingRepository.getAllValidScanJobRecords()
                if (scanJobs.isEmpty()) return@withLock
                if (scanJobs.any { it.lastScanDateInMillis == 0L }) return@withLock

                wideEventClient.intervalEnd(wideEventId = flowId, key = INTERVAL_TOTAL_DURATION)
                wideEventClient.flowFinish(
                    wideEventId = flowId,
                    status = FlowStatus.Success,
                    metadata = mapOf(
                        KEY_TOTAL_SCAN_JOBS_AT_FINISH to scanJobs.size.toString(),
                        KEY_FOREGROUND_RUN_COUNT to pirDataStore.initialScanCompletionForegroundRunCount.toString(),
                        KEY_SCHEDULED_RUN_COUNT to pirDataStore.initialScanCompletionScheduledRunCount.toString(),
                    ),
                )

                pirDataStore.initialScanCompletionFlowId = 0L
                pirDataStore.initialScanCompletionForegroundRunCount = 0
                pirDataStore.initialScanCompletionScheduledRunCount = 0
            }
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        pirRemoteFeatures.sendScanWideEvent().isEnabled()
    }

    private fun Boolean.toVpnConnectionState(): String = if (this) "connected" else "disconnected"

    private fun Boolean.toTrackerBlockingState(): String = if (this) "enabled" else "disabled"

    companion object {
        const val WIDE_EVENT_NAME = "pir-time-to-first-scan-complete"
        const val ENTRY_POINT = "funnel_pir_time_to_first_scan_complete_android"

        val COMPLETION_FLOW_TIMEOUT = 30.days

        const val INTERVAL_TOTAL_DURATION = "total_duration_ms_bucketed"

        const val KEY_PROFILE_QUERIES_COUNT = "profile_queries_count"
        const val KEY_BROKER_COUNT = "broker_count"
        const val KEY_TOTAL_SCAN_JOBS = "total_scan_jobs"
        const val KEY_WEB_VIEW_COUNT = "web_view_count"
        const val KEY_POWER_SAVING = "power_saving"
        const val KEY_BATTERY_OPTIMIZATIONS = "battery_optimizations"
        const val KEY_VPN_CONNECTION_STATE = "vpn_connection_state"
        const val KEY_NOTIFICATIONS_PERMISSION_GRANTED = "notifications_permission_granted"
        const val KEY_TRACKER_BLOCKING_STATE = "tracker_blocking_state"

        const val KEY_TOTAL_SCAN_JOBS_AT_FINISH = "total_scan_jobs_at_finish"
        const val KEY_FOREGROUND_RUN_COUNT = "foreground_run_count"
        const val KEY_SCHEDULED_RUN_COUNT = "scheduled_run_count"
    }
}
