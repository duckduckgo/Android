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

package com.duckduckgo.pir.impl.dashboard.state

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider.DashboardBrokerMatch
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider.DashboardRemovedExtractedProfileResult
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider.DashboardScanDetails
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.isExtant
import com.duckduckgo.pir.impl.models.scheduling.BrokerSchedulingConfig
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus.REMOVED
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus.REQUESTED
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface PirDashboardMaintenanceScanDataProvider {
    /**
     * returns all Extracted Profiles that has not been completely removed.
     */
    suspend fun getInProgressOptOuts(): List<DashboardExtractedProfileResult>

    /**
     * returns all Extracted Profiles that has been completely removed.
     */
    suspend fun getRemovedOptOuts(): List<DashboardRemovedExtractedProfileResult>

    /**
     * Return the scan details for the last scan run.
     * This will include scans that has been completed within the last 8 days.
     * This also applies for extant Mirror Sites that were added since the last run of its parent.
     */
    suspend fun getLastScanDetails(): DashboardScanDetails

    /**
     * Return the scan details for the next scan that is scheduled to run.
     * This will include scans that are scheduled to run in the next 8 days.
     * This also applies for extant Mirror Sites that were added since the last run of its parent.
     */
    suspend fun getNextScanDetails(): DashboardScanDetails

    /**
     * Returns the number of brokers that have been scanned at least once. Includes MirrorSites.
     */
    suspend fun getScannedBrokerCount(): Int

    data class DashboardRemovedExtractedProfileResult(
        val result: DashboardExtractedProfileResult,
        val matches: Int,
    )

    data class DashboardScanDetails(
        val dateInMillis: Long,
        val brokerMatches: List<DashboardBrokerMatch>,
    )

    data class DashboardBrokerMatch(
        val broker: DashboardBroker,
        val dateInMillis: Long,
    )
}

@ContributesBinding(
    scope = ActivityScope::class,
    boundType = PirDashboardMaintenanceScanDataProvider::class,
)
@SingleInstanceIn(ActivityScope::class)
class RealPirDashboardMaintenanceScanDataProvider @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirRepository: PirRepository,
    private val pirSchedulingRepository: PirSchedulingRepository,
) : PirDashboardStateProvider(currentTimeProvider, pirRepository, pirSchedulingRepository), PirDashboardMaintenanceScanDataProvider {
    override suspend fun getInProgressOptOuts(): List<DashboardExtractedProfileResult> = withContext(dispatcherProvider.io()) {
        return@withContext getAllExtractedProfileResults().filter {
            it.optOutRemovedDateInMillis == null || it.optOutRemovedDateInMillis == 0L
        }
    }

    override suspend fun getRemovedOptOuts(): List<DashboardRemovedExtractedProfileResult> = withContext(dispatcherProvider.io()) {
        val allRemovedExtractedProfiles = getAllExtractedProfileResults().filter {
            it.optOutRemovedDateInMillis != null && it.optOutRemovedDateInMillis != 0L
        }

        val brokerMatchesMap = allRemovedExtractedProfiles.groupBy { it.broker }

        return@withContext allRemovedExtractedProfiles.map {
            DashboardRemovedExtractedProfileResult(
                result = it,
                matches = brokerMatchesMap[it.broker]?.size ?: 0,
            )
        }
    }

    override suspend fun getLastScanDetails(): DashboardScanDetails {
        val endDate = currentTimeProvider.currentTimeMillis()
        val startDate = endDate - SCAN_DETAILS_RANGE

        val allValidBrokerMatches = getBrokerMatches(
            scanFilter = {
                it.lastScanDateInMillis in startDate..endDate
            },
            getDateMillis = { it.lastScanDateInMillis },
        )

        return DashboardScanDetails(
            dateInMillis = allValidBrokerMatches.firstOrNull()?.dateInMillis ?: 0L,
            brokerMatches = allValidBrokerMatches,
        )
    }

    override suspend fun getNextScanDetails(): DashboardScanDetails {
        val startDate = currentTimeProvider.currentTimeMillis()
        val endDate = startDate + SCAN_DETAILS_RANGE
        val schedulingConfigMap = pirRepository.getAllBrokerSchedulingConfigs().associateBy { it.brokerName }
        val nextRunFromOptOutDataMap = getNextRunFromOptOutRecords(schedulingConfigMap, startDate, endDate)

        val allValidBrokerMatches = getBrokerMatches(
            scanFilter = {
                val schedulingConfig = schedulingConfigMap[it.brokerName] ?: return@getBrokerMatches false
                it.getNextRunMillis(schedulingConfig, nextRunFromOptOutDataMap[it.brokerName], startDate, endDate) in startDate..endDate
            },
            getDateMillis = {
                val schedulingConfig = schedulingConfigMap[it.brokerName] ?: return@getBrokerMatches 0L
                it.getNextRunMillis(schedulingConfig, nextRunFromOptOutDataMap[it.brokerName], startDate, endDate)
            },
        )

        return DashboardScanDetails(
            dateInMillis = allValidBrokerMatches.firstOrNull()?.dateInMillis ?: 0L,
            brokerMatches = allValidBrokerMatches,
        )
    }

    private suspend fun getNextRunFromOptOutRecords(
        schedulingConfigMap: Map<String, BrokerSchedulingConfig>,
        startDate: Long,
        endDate: Long,
    ): Map<String, Long?> {
        // This is a map for broker to the next potential run from the opt-out records that are either in REQUESTED or REMOVED
        return pirSchedulingRepository.getAllValidOptOutJobRecords()
            .filter {
                it.status == REQUESTED || it.status == REMOVED
            }
            .groupBy { it.brokerName }
            .mapValues { (_, optOutJobRecords) ->
                // From the opt out jobs for the broker, we take the earliest next run that is within the range
                optOutJobRecords.mapNotNull { optOutJob ->
                    val schedulingConfig = schedulingConfigMap[optOutJob.brokerName] ?: return@mapNotNull null
                    when (optOutJob.status) {
                        REQUESTED -> optOutJob.optOutRequestedDateInMillis + schedulingConfig.confirmOptOutScanInMillis
                        REMOVED -> optOutJob.optOutRemovedDateInMillis + schedulingConfig.maintenanceScanInMillis
                        else -> null
                    }
                }.filter {
                    it in startDate..endDate
                }.minOrNull()
            }
    }

    override suspend fun getScannedBrokerCount(): Int {
        return getBrokersAndMirrorSitesWithProgressStatus().size
    }

    private fun ScanJobRecord.getNextRunMillis(
        schedulingConfig: BrokerSchedulingConfig,
        nextRunFromOptOutData: Long?,
        startDate: Long,
        endDate: Long,
    ): Long {
        return when (this.status) {
            ScanJobRecord.ScanJobStatus.NOT_EXECUTED -> {
                // Should be executed immediately
                currentTimeProvider.currentTimeMillis()
            }

            ScanJobRecord.ScanJobStatus.MATCHES_FOUND -> {
                // If a match is found, the next run could be a maintenance scan for the broker or confirmation/maintenance scan from an opt-out
                // We will take the earliest within the range
                val nextMaintenanceScan = lastScanDateInMillis + schedulingConfig.maintenanceScanInMillis

                if (nextRunFromOptOutData != null) {
                    listOf(nextMaintenanceScan, nextRunFromOptOutData).filter {
                        it in startDate..endDate
                    }.minOrNull() ?: 0L
                } else {
                    nextMaintenanceScan
                }
            }

            ScanJobRecord.ScanJobStatus.NO_MATCH_FOUND -> {
                // Next run would be a maintenance scan
                lastScanDateInMillis + schedulingConfig.maintenanceScanInMillis
            }

            ScanJobRecord.ScanJobStatus.ERROR -> {
                // Next run would be an error retry
                lastScanDateInMillis + schedulingConfig.retryErrorInMillis
            }

            ScanJobRecord.ScanJobStatus.INVALID -> {
                // Canceled, so no next run
                0L
            }
        }
    }

    suspend fun getBrokerMatches(
        scanFilter: (ScanJobRecord) -> Boolean,
        getDateMillis: (ScanJobRecord) -> Long,
    ): List<DashboardBrokerMatch> {
        val activeBrokerMap = pirRepository.getAllActiveBrokerObjects().associateBy { it.name }
        // Only consider active brokers and ignore removed ones
        val activeBrokerOptOutUrls = pirRepository.getAllBrokerOptOutUrls()

        val validScansJobs = getAllValidAndActiveScanJobs(activeBrokerMap).filter {
            scanFilter(it)
        }.sortedBy {
            it.lastScanDateInMillis
        }.mapNotNull {
            val broker = activeBrokerMap[it.brokerName] ?: return@mapNotNull null
            DashboardBrokerMatch(
                broker = DashboardBroker(
                    name = broker.name,
                    url = broker.url,
                    parentUrl = broker.parent?.let { parentName ->
                        activeBrokerMap[parentName]?.url
                    },
                    optOutUrl = activeBrokerOptOutUrls[broker.name],
                ),
                dateInMillis = getDateMillis(it),
            )
        }

        val mirrorValidScanJobs = validScansJobs.getMirrorSites()
        return (validScansJobs + mirrorValidScanJobs).sortedBy { it.dateInMillis }
    }

    private suspend fun List<DashboardBrokerMatch>.getMirrorSites(): List<DashboardBrokerMatch> {
        val dashboardMatchMap = this.associateBy { it.broker.name }
        val activeMirrorSitesMap = pirRepository.getAllMirrorSites().filter {
            it.parentSite in dashboardMatchMap.keys
        }

        return activeMirrorSitesMap.mapNotNull { mirrorSite ->
            val parentBroker = dashboardMatchMap[mirrorSite.parentSite] ?: return@mapNotNull null
            if (!mirrorSite.isExtant(parentBroker.dateInMillis)) {
                return@mapNotNull null
            }

            DashboardBrokerMatch(
                broker = DashboardBroker(
                    name = mirrorSite.name,
                    url = mirrorSite.url,
                    parentUrl = parentBroker.broker.url,
                    optOutUrl = mirrorSite.optOutUrl,
                ),
                dateInMillis = parentBroker.dateInMillis,
            )
        }
    }

    private suspend fun getAllValidAndActiveScanJobs(activeBrokerMap: Map<String, Broker>): List<ScanJobRecord> {
        // Take all scan jobs that is for an active broker, this automatically remove INVALID jobs - which should be the case of removed profiles
        return pirSchedulingRepository.getAllValidScanJobRecords().filter {
            it.brokerName in activeBrokerMap.keys
        }
    }

    companion object {
        private val SCAN_DETAILS_RANGE = TimeUnit.DAYS.toMillis(8)
    }
}
