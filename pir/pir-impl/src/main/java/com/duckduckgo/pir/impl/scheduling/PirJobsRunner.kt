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
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isIgnoringBatteryOptimizations
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.optout.PirOptOut
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scan.PirScan
import com.duckduckgo.pir.impl.scheduling.PirExecutionType.MANUAL
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
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
) : PirJobsRunner {
    override suspend fun runEligibleJobs(
        context: Context,
        executionType: PirExecutionType,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        val startTimeInMillis = currentTimeProvider.currentTimeMillis()

        // Multiple profile support (includes deprecated profiles as we need to process opt-out for them if there are extracted profiles)
        val profileQueries = obtainProfiles()
        emitStartPixel(context, executionType, profileQueries.size)

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

        val activeBrokers = pirRepository.getAllActiveBrokers().toHashSet()

        if (profileQueries.isEmpty()) {
            logcat { "PIR-JOB-RUNNER: No profile queries available. Completing run." }
            emitCompletedPixel(context, executionType, startTimeInMillis, totalScanJobs = 0, totalOptOutJobs = 0)
            return@withContext Result.success(Unit)
        }

        if (activeBrokers.isEmpty()) {
            logcat { "PIR-JOB-RUNNER: No active brokers available. Completing run." }
            emitCompletedPixel(context, executionType, startTimeInMillis, totalScanJobs = 0, totalOptOutJobs = 0)
            return@withContext Result.success(Unit)
        }

        storeScanStats(startTimeInMillis, executionType)
        attemptCreateScanJobs(activeBrokers, profileQueries)
        val totalScanJobs = executeScanJobs(context, executionType, activeBrokers)

        // We emit a pixel after the scans are completed from the foreground scan
        if (executionType == MANUAL) {
            pixelSender.reportInitialScanDuration(
                durationMs = currentTimeProvider.currentTimeMillis() - startTimeInMillis,
                profileQueryCount = profileQueries.size,
            )
        }

        val formOptOutBrokers = pirRepository.getBrokersForOptOut(true).toSet()
        val activeFormOptOutBrokers = formOptOutBrokers.intersect(activeBrokers)

        if (activeFormOptOutBrokers.isEmpty()) {
            logcat { "PIR-JOB-RUNNER: No active parent brokers available for optout. Completing run." }
            emitCompletedPixel(context, executionType, startTimeInMillis, totalScanJobs, totalOptOutJobs = 0)
            return@withContext Result.success(Unit)
        }

        attemptCreateOptOutJobs(activeFormOptOutBrokers)
        val totalOptOutJobs = executeOptOutJobs(context, activeFormOptOutBrokers)

        logcat { "PIR-JOB-RUNNER: Completed." }
        emitCompletedPixel(context, executionType, startTimeInMillis, totalScanJobs, totalOptOutJobs)
        return@withContext Result.success(Unit)
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

    private fun emitStartPixel(
        context: Context,
        executionType: PirExecutionType,
        profileQueryCount: Int,
    ) {
        if (executionType == MANUAL) {
            val isPowerSavingEnabled = runCatching {
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
            }.getOrDefault(false)
            pixelSender.reportManualScanStarted(isPowerSavingEnabled, profileQueryCount)
        } else {
            pixelSender.reportScheduledScanStarted()
        }
    }

    private fun emitCompletedPixel(
        context: Context,
        executionType: PirExecutionType,
        startTimeInMillis: Long,
        totalScanJobs: Int,
        totalOptOutJobs: Int,
    ) {
        val totalTimeMillis = currentTimeProvider.currentTimeMillis() - startTimeInMillis
        if (executionType == MANUAL) {
            val batteryOptimizationsEnabled = !context.isIgnoringBatteryOptimizations()
            pixelSender.reportManualScanCompleted(totalTimeMillis, batteryOptimizationsEnabled, totalScanJobs, totalOptOutJobs)
        } else {
            pixelSender.reportScheduledScanCompleted(totalTimeMillis)
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
        activeBrokers: Set<String>,
    ): Int {
        val eligibleJobs = eligibleScanJobProvider.getAllEligibleScanJobs(currentTimeProvider.currentTimeMillis())
            .filter { it.brokerName in activeBrokers }
        val runType = if (executionType == MANUAL) {
            RunType.MANUAL
        } else {
            RunType.SCHEDULED
        }
        if (eligibleJobs.isNotEmpty()) {
            logcat { "PIR-JOB-RUNNER: Executing scan for ${eligibleJobs.size} eligible scan jobs." }
            pirScan.executeScanForJobs(eligibleJobs, context, runType)
        } else {
            logcat { "PIR-JOB-RUNNER: No eligible scan jobs to execute." }
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
}
