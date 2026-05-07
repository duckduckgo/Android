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

package com.duckduckgo.pir.impl.scheduling

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isIgnoringBatteryOptimizations
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdater
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirJobConstants.MAX_DETACHED_WEBVIEW_COUNT
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.optout.PirOptOut
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scan.PirScan
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

interface PirJobsRunner {
    /**
     * This method is responsible for executing scans and opt-out jobs for any [ScanJobRecord] and/or [OptOutJobRecord] that is
     * eligible to be run at the current time.
     *
     * Note that any new [ScanJobRecord] and [OptOutJobRecord] are also created as part of the execution path.
     */
    suspend fun runEligibleJobs(
        context: Context,
        executionType: PirExecutionType,
    ): Result<Unit>

    /**
     * Stop any job that is in progress if any.
     */
    fun stop()
}

@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealPirJobsRunner @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirRepository: PirRepository,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val eligibleOptOutJobProvider: EligibleOptOutJobProvider,
    private val eligibleScanJobProvider: EligibleScanJobProvider,
    private val pirScan: PirScan,
    private val pirOptOut: PirOptOut,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pixelSender: PirPixelSender,
    private val brokerJsonUpdater: BrokerJsonUpdater,
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val pirScanWideEvent: PirScanWideEvent,
    private val networkProtectionState: NetworkProtectionState,
) : PirJobsRunner {
    override suspend fun runEligibleJobs(
        context: Context,
        executionType: PirExecutionType,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        val startTimeInMillis = currentTimeProvider.currentTimeMillis()

        // Multiple profile support (includes deprecated profiles as we need to process opt-out for them if there are extracted profiles)
        val profileQueries = obtainProfiles()
        var activeBrokers = pirRepository.getAllActiveBrokers().toHashSet()

        // Clean up any already running scan jobs before starting new ones as this function can be called
        // while previous instance is still running in case of profile edits.
        //
        // The PirScan is a singleton and already stops any ongoing work if execute is called on it while a previous work is still ongoing.
        //
        // This handles case when user removes a profile while a scan or opt-out is ongoing for that profile.
        // Without this, the scan would continue to run on the removed profile, potentially finding new extracted profiles
        // and creating opt-out jobs for them, which we don't want.
        // We only want to continue running opt-outs and confirmation scans for extracted profiles that were found up until the point of profile edit.
        pirScan.stop()

        if (profileQueries.isEmpty()) {
            emitStartPixel(context, executionType, 0, activeBrokers.size)
            logcat { "PIR-JOB-RUNNER: No profile queries available. Completing run." }
            emitCompletedPixel(
                context = context,
                executionType = executionType,
                startTimeInMillis = startTimeInMillis,
                totalScanJobs = 0,
                totalOptOutJobs = 0,
                profileQueryCount = 0,
                brokerCount = activeBrokers.size,
            )
            return@withContext Result.success(Unit)
        }

        // If no active brokers found, attempt to load broker data before giving up.
        // This handles a race condition where the scan starts before broker data has been downloaded
        // (e.g., on first scan after feature flag enablement, or with slow network/VPN).
        if (activeBrokers.isEmpty() && pirRemoteFeatures.ensureBrokerDataBeforeScan().isEnabled()) {
            logcat { "PIR-JOB-RUNNER: No active brokers, attempting to ensure broker data is loaded..." }
            try {
                brokerJsonUpdater.update()
                activeBrokers = pirRepository.getAllActiveBrokers().toHashSet()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                logcat { "PIR-JOB-RUNNER: Failed to update broker data." }
            }
        }

        emitStartPixel(context, executionType, profileQueries.size, activeBrokers.size)

        // Pre-create scan jobs and resolve eligible jobs up front so the wide event has the accurate
        // total_scan_jobs count at flowStart.
        val eligibleJobs = if (activeBrokers.isNotEmpty()) {
            attemptCreateScanJobs(activeBrokers, profileQueries)
            eligibleScanJobProvider.getAllEligibleScanJobs(currentTimeProvider.currentTimeMillis())
                .filter { it.brokerName in activeBrokers }
        } else {
            emptyList()
        }

        pirScanWideEvent.onRunStarted(
            executionType = executionType,
            profileQueriesCount = profileQueries.size,
            brokerCount = activeBrokers.size,
            totalScanJobs = eligibleJobs.size,
            webViewCount = minOf(eligibleJobs.size, MAX_DETACHED_WEBVIEW_COUNT),
            isPowerSavingEnabled = context.isPowerSavingModeEnabled(),
            isVpnConnected = networkProtectionState.safeIsVpnRunning(),
            batteryOptimizationsEnabled = !context.isIgnoringBatteryOptimizations(),
            notificationsPermissionGranted = context.areNotificationsPermissionGranted(),
            isTrackerBlockingEnabled = pirRemoteFeatures.trackerBlocking().isEnabled(),
        )

        if (activeBrokers.isEmpty()) {
            logcat { "PIR-JOB-RUNNER: No active brokers available. Completing run." }
            pirScanWideEvent.onRunFailed(executionType = executionType, reason = REASON_NO_ACTIVE_BROKERS)
            emitCompletedPixel(
                context = context,
                executionType = executionType,
                startTimeInMillis = startTimeInMillis,
                totalScanJobs = 0,
                totalOptOutJobs = 0,
                profileQueryCount = profileQueries.size,
                brokerCount = 0,
            )
            return@withContext Result.success(Unit)
        }

        try {
            storeScanStats(startTimeInMillis, executionType)

            val onJobCompletedCallback: suspend () -> Unit = {
                pirScanWideEvent.onScanJobCompleted(executionType)
            }
            val onScanJobsResolvedCallback: suspend (Int) -> Unit = { actual ->
                pirScanWideEvent.onScanJobsResolved(executionType, actual)
            }
            val totalScanJobs = executeScanJobs(
                context = context,
                executionType = executionType,
                eligibleJobs = eligibleJobs,
                onJobCompleted = onJobCompletedCallback,
                onScanJobsResolved = onScanJobsResolvedCallback,
            )

            pirScanWideEvent.onScanCompleted(executionType)

            if (executionType.isManual) {
                val batteryOptimizationsEnabled = !context.isIgnoringBatteryOptimizations()
                pixelSender.reportInitialScanDuration(
                    durationMs = currentTimeProvider.currentTimeMillis() - startTimeInMillis,
                    profileQueryCount = profileQueries.size,
                    isPowerSavingEnabled = context.isPowerSavingModeEnabled(),
                    batteryOptimizationsEnabled = batteryOptimizationsEnabled,
                    brokerCount = activeBrokers.size,
                    executionType = executionType,
                    notificationsPermissionGranted = context.areNotificationsPermissionGranted(),
                )
            }

            val formOptOutBrokers = pirRepository.getBrokersForOptOut(true).toSet()
            val activeFormOptOutBrokers = formOptOutBrokers.intersect(activeBrokers)

            if (activeFormOptOutBrokers.isEmpty()) {
                logcat { "PIR-JOB-RUNNER: No active parent brokers available for optout. Completing run." }
                pirScanWideEvent.onOptOutSkipped(executionType)
                emitCompletedPixel(
                    context = context,
                    executionType = executionType,
                    startTimeInMillis = startTimeInMillis,
                    totalScanJobs = totalScanJobs,
                    totalOptOutJobs = 0,
                    profileQueryCount = profileQueries.size,
                    brokerCount = activeBrokers.size,
                )
                return@withContext Result.success(Unit)
            }

            pirScanWideEvent.onOptOutStarted(executionType)

            attemptCreateOptOutJobs(activeFormOptOutBrokers)
            val totalOptOutJobs = executeOptOutJobs(context, activeFormOptOutBrokers)

            pirScanWideEvent.onOptOutCompleted(executionType = executionType, totalOptOutJobs = totalOptOutJobs)

            logcat { "PIR-JOB-RUNNER: Completed." }
            emitCompletedPixel(
                context = context,
                executionType = executionType,
                startTimeInMillis = startTimeInMillis,
                totalScanJobs = totalScanJobs,
                totalOptOutJobs = totalOptOutJobs,
                profileQueryCount = profileQueries.size,
                brokerCount = activeBrokers.size,
            )
            return@withContext Result.success(Unit)
        } catch (e: CancellationException) {
            pirScanWideEvent.onRunCancelled(executionType)
            throw e
        } catch (e: Exception) {
            pirScanWideEvent.onRunFailed(executionType = executionType, reason = e.javaClass.simpleName)
            throw e
        }
    }

    private suspend fun storeScanStats(
        startTimeInMillis: Long,
        executionType: PirExecutionType,
    ) {
        val previousRun = pirRepository.latestBackgroundScanRunInMs()

        // The first run will be the starting point of counting the stats (regardless if manual or scheduled)
        if (previousRun == 0L) {
            pirRepository.setLatestBackgroundScanRunInMs(startTimeInMillis)
        } else {
            // We will only update the values on the succeeding background scans only
            if (executionType == PirExecutionType.SCHEDULED) {
                pirRepository.setLatestBackgroundScanRunInMs(startTimeInMillis)
            }
        }
    }

    private suspend fun emitStartPixel(
        context: Context,
        executionType: PirExecutionType,
        profileQueryCount: Int,
        brokerCount: Int,
    ) {
        if (executionType.isManual) {
            val isPowerSavingEnabled = context.isPowerSavingModeEnabled()
            pixelSender.reportManualScanStarted(
                isPowerSavingEnabled = isPowerSavingEnabled,
                profileQueryCount = profileQueryCount,
                brokerCount = brokerCount,
                executionType = executionType,
                notificationsPermissionGranted = context.areNotificationsPermissionGranted(),
            )
        } else {
            pixelSender.reportScheduledScanStarted(profileQueryCount, brokerCount)
        }
    }

    private suspend fun emitCompletedPixel(
        context: Context,
        executionType: PirExecutionType,
        startTimeInMillis: Long,
        totalScanJobs: Int,
        totalOptOutJobs: Int,
        profileQueryCount: Int,
        brokerCount: Int,
    ) {
        val totalTimeMillis = currentTimeProvider.currentTimeMillis() - startTimeInMillis
        if (executionType.isManual) {
            val batteryOptimizationsEnabled = !context.isIgnoringBatteryOptimizations()
            pixelSender.reportManualScanCompleted(
                totalTimeInMillis = totalTimeMillis,
                batteryOptimizationsEnabled = batteryOptimizationsEnabled,
                totalScanJobs = totalScanJobs,
                totalOptOutJobs = totalOptOutJobs,
                profileQueryCount = profileQueryCount,
                brokerCount = brokerCount,
                isPowerSavingEnabled = context.isPowerSavingModeEnabled(),
                executionType = executionType,
                notificationsPermissionGranted = context.areNotificationsPermissionGranted(),
            )
        } else {
            pixelSender.reportScheduledScanCompleted(
                totalTimeInMillis = totalTimeMillis,
                totalScanJobs = totalScanJobs,
                totalOptOutJobs = totalOptOutJobs,
                profileQueryCount = profileQueryCount,
                brokerCount = brokerCount,
            )
        }
    }

    private suspend fun obtainProfiles(): List<ProfileQuery> {
        return pirRepository.getAllUserProfileQueries()
    }

    private suspend fun attemptCreateScanJobs(
        activeBrokers: Set<String>,
        profileQueries: List<ProfileQuery>,
    ) {
        logcat { "PIR-JOB-RUNNER: Attempting to create new scan jobs" }
        // No scan jobs mean that this is the first time PIR is being run OR all jobs have been invalidated.
        val toCreate = mutableListOf<ScanJobRecord>()

        profileQueries.filter {
            // we should not create any new scan jobs for deprecated profile queries
            !it.deprecated
        }.forEach { profile ->
            activeBrokers.forEach { broker ->
                if (pirSchedulingRepository.getValidScanJobRecord(broker, profile.id) == null) {
                    toCreate.add(
                        ScanJobRecord(
                            brokerName = broker,
                            userProfileId = profile.id,
                        ),
                    )
                }
            }
        }

        if (toCreate.isNotEmpty()) {
            logcat { "PIR-JOB-RUNNER: New scan job records created. Saving... $toCreate" }
            pirSchedulingRepository.saveScanJobRecords(toCreate)
        } else {
            logcat { "PIR-JOB-RUNNER: No new scan job records created." }
        }
    }

    private suspend fun executeScanJobs(
        context: Context,
        executionType: PirExecutionType,
        eligibleJobs: List<ScanJobRecord>,
        onJobCompleted: (suspend () -> Unit)? = null,
        onScanJobsResolved: (suspend (Int) -> Unit)? = null,
    ): Int {
        val runType = if (executionType.isManual) {
            RunType.MANUAL
        } else {
            RunType.SCHEDULED
        }
        if (eligibleJobs.isNotEmpty()) {
            logcat { "PIR-JOB-RUNNER: Executing scan for ${eligibleJobs.size} eligible scan jobs." }
            pirScan.executeScanForJobs(eligibleJobs, context, runType, onJobCompleted, onScanJobsResolved)
        } else {
            logcat { "PIR-JOB-RUNNER: No eligible scan jobs to execute." }
            onScanJobsResolved?.invoke(0)
        }
        return eligibleJobs.size
    }

    private suspend fun attemptCreateOptOutJobs(activeFormOptOutBrokers: Set<String>) {
        val toCreate = mutableListOf<OptOutJobRecord>()
        val extractedProfiles = pirRepository.getAllExtractedProfiles()

        extractedProfiles.forEach {
            // we should create new opt-out jobs even for deprecated profile queries that have extracted profiles associated with them
            if (activeFormOptOutBrokers.contains(it.brokerName) &&
                pirSchedulingRepository.getValidOptOutJobRecord(it.dbId, includeDeprecated = true) == null
            ) {
                toCreate.add(
                    OptOutJobRecord(
                        extractedProfileId = it.dbId,
                        brokerName = it.brokerName,
                        userProfileId = it.profileQueryId,
                    ),
                )
            }
        }

        if (toCreate.isNotEmpty()) {
            logcat { "PIR-JOB-RUNNER: New opt-out job records created. Saving... $toCreate" }
            pirSchedulingRepository.saveOptOutJobRecords(toCreate)
        } else {
            logcat { "PIR-JOB-RUNNER: No new opt-out job records created." }
        }
    }

    private suspend fun executeOptOutJobs(
        context: Context,
        activeFormOptOutBrokers: Set<String>,
    ): Int {
        val eligibleJobs = eligibleOptOutJobProvider.getAllEligibleOptOutJobs(currentTimeProvider.currentTimeMillis())
            .filter { activeFormOptOutBrokers.contains(it.brokerName) }
        if (eligibleJobs.isNotEmpty()) {
            logcat { "PIR-JOB-RUNNER: Executing opt-outs for ${eligibleJobs.size} eligible optout jobs." }
            pirOptOut.executeOptOutForJobs(eligibleJobs, context)
        } else {
            logcat { "PIR-JOB-RUNNER: No eligible opt-out jobs to execute." }
        }
        return eligibleJobs.size
    }

    override fun stop() {
        pirScan.stop()
        pirOptOut.stop()
    }

    private fun Context.isPowerSavingModeEnabled(): Boolean {
        return runCatching {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        }.getOrDefault(false)
    }

    private fun Context.areNotificationsPermissionGranted(): Boolean {
        return runCatching {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }.getOrDefault(false)
    }

    private suspend fun NetworkProtectionState.safeIsVpnRunning(): Boolean {
        return runCatching { isRunning() }.getOrDefault(false)
    }

    private companion object {
        const val REASON_NO_ACTIVE_BROKERS = "no_active_brokers"
    }
}
