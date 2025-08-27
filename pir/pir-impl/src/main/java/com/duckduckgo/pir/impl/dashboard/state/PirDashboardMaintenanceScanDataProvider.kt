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
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.logcat

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
        return@withContext getExtractedProfileResults().filter {
            it.optOutRemovedDateInMillis == null || it.optOutRemovedDateInMillis == 0L
        }
    }

    override suspend fun getRemovedOptOuts(): List<DashboardRemovedExtractedProfileResult> = withContext(dispatcherProvider.io()) {
        val allRemovedExtractedProfiles = getExtractedProfileResults().filter {
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
        ).also {
            logcat { "KLDIMSUM last: $it" }
        }
    }

    override suspend fun getNextScanDetails(): DashboardScanDetails {
        val startDate = currentTimeProvider.currentTimeMillis()
        val endDate = startDate + SCAN_DETAILS_RANGE
        val schedulingConfigMap = pirRepository.getAllBrokerSchedulingConfigs().associateBy { it.brokerName }

        val allValidBrokerMatches = getBrokerMatches(
            scanFilter = {
                val schedulingConfig = schedulingConfigMap[it.brokerName] ?: return@getBrokerMatches false
                it.getNextRunMillis(schedulingConfig) in startDate..endDate
            },
            getDateMillis = {
                val schedulingConfig = schedulingConfigMap[it.brokerName] ?: return@getBrokerMatches 0L
                it.getNextRunMillis(schedulingConfig)
            },
        )

        return DashboardScanDetails(
            dateInMillis = allValidBrokerMatches.firstOrNull()?.dateInMillis ?: 0L,
            brokerMatches = allValidBrokerMatches,
        ).also {
            logcat { "KLDIMSUM next: $it" }
        }
    }

    override suspend fun getScannedBrokerCount(): Int {
        return getBrokersAndMirrorSitesWithProgressStatus().size
    }

    private fun ScanJobRecord.getNextRunMillis(schedulingConfig: BrokerSchedulingConfig): Long {
        return when (this.status) {
            ScanJobRecord.ScanJobStatus.NOT_EXECUTED -> {
                // Should be executed immediately
                currentTimeProvider.currentTimeMillis()
            }

            ScanJobRecord.ScanJobStatus.MATCHES_FOUND -> {
                lastScanDateInMillis + schedulingConfig.maintenanceScanInMillis
            }

            ScanJobRecord.ScanJobStatus.NO_MATCH_FOUND -> {
                lastScanDateInMillis + schedulingConfig.maintenanceScanInMillis
            }

            ScanJobRecord.ScanJobStatus.ERROR -> {
                lastScanDateInMillis + schedulingConfig.retryErrorInMillis
            }

            ScanJobRecord.ScanJobStatus.INVALID -> {
                // Canceled, so no next run
                0L
            }
        }.also {
            logcat { "KLDIMSUM: ${this.brokerName} with ${this.status} -> $it" }
        }
    }

    private suspend fun getBrokerMatches(
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
