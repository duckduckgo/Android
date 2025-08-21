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
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBroker
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBrokerWithStatus
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBrokerWithStatus.Status.COMPLETED
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBrokerWithStatus.Status.IN_PROGRESS
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardExtractedProfileResult
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.MirrorSite
import com.duckduckgo.pir.impl.models.isExtant
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus.REMOVED
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface PirDashboardInitialScanStateProvider {
    /**
     * Returns the total number of active brokers (including only currently extant mirrorSites),
     */
    suspend fun getActiveBrokersAndMirrorSitesTotal(): Int

    /**
     * Returns the number of brokers whose all scan jobs have completed
     * This also includes their currently extant mirrorSites,
     */
    suspend fun getFullyCompletedBrokersTotal(): Int

    /**
     * Returns the brokers that either have:
     *  - all scan jobs have completed, or
     *  - at least one scan job completed
     *
     *  This also includes currently extant mirrorSites,
     */
    suspend fun getInProgressAndCompletedBrokersAndMirrorSites(): List<DashboardBrokerWithStatus>

    /**
     * Returns all results which could either be:
     * - a valid ExtractedProfile from a scan for an active Broker x ProfileQuery
     * - a valid ExtractedProfile for a MirrorSite
     */
    suspend fun getScanResults(): List<DashboardExtractedProfileResult>

    data class DashboardBroker(
        val name: String,
        val url: String,
        val parentUrl: String? = null,
        val optOutUrl: String? = null,
    )

    data class DashboardBrokerWithStatus(
        val broker: DashboardBroker,
        val status: Status,
        val firstScanDateInMillis: Long = 0L,
    ) {
        /**
         * A broker is:
         * - [NOT_STARTED] if no scan has been run for it
         * - [IN_PROGRESS] if at least one scan has been run for it, but not all scans have completed
         * - [COMPLETED] if all scans have been run and completed for it
         */
        enum class Status(val statusName: String) {
            NOT_STARTED("not-started"),
            IN_PROGRESS("in-progress"),
            COMPLETED("completed"),
        }
    }

    /**
     * This class represents a valid [ExtractedProfile] that should be displayed in the dashboard.
     */
    data class DashboardExtractedProfileResult(
        val extractedProfile: ExtractedProfile,
        val broker: DashboardBroker,
        val optOutSubmittedDateInMillis: Long? = null,
        val optOutRemovedDateInMillis: Long? = null,
        val estimatedRemovalDateInMillis: Long? = null,
        val hasMatchingRecordOnParentBroker: Boolean = false,
    )
}

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class RealPirDashboardInitialScanStateProvider @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirRepository: PirRepository,
    private val pirSchedulingRepository: PirSchedulingRepository,
) : PirDashboardInitialScanStateProvider {
    override suspend fun getActiveBrokersAndMirrorSitesTotal(): Int = withContext(dispatcherProvider.io()) {
        val currentTime = currentTimeProvider.currentTimeMillis()
        val activeBrokerNames = pirRepository.getAllActiveBrokers().toHashSet()
        // Take all extant mirror sites whose parent is currently active
        val activeMirrorSites = getAllExtantMirrorSites(currentTime).filter {
            activeBrokerNames.contains(it.parentSite)
        }

        return@withContext activeBrokerNames.size + activeMirrorSites.size
    }

    override suspend fun getFullyCompletedBrokersTotal(): Int {
        return getInProgressAndCompletedBrokersAndMirrorSites().filter {
            it.status == COMPLETED
        }.size
    }

    override suspend fun getInProgressAndCompletedBrokersAndMirrorSites(): List<DashboardBrokerWithStatus> {
        val currentTime = currentTimeProvider.currentTimeMillis()
        val inProgressAndCompletedBrokers = getInProgressAndCompletedBrokers()
        val mirrorSiteBrokers = inProgressAndCompletedBrokers.getMirrorSites(currentTime)

        return (inProgressAndCompletedBrokers + mirrorSiteBrokers).sortedBy {
            it.firstScanDateInMillis
        }
    }

    override suspend fun getScanResults(): List<DashboardExtractedProfileResult> {
        val extractedProfiles = pirRepository.getAllExtractedProfiles().filter {
            !it.deprecated
        }
        val extractedProfilesFromBrokers = getExtractedProfileResultForBrokers(extractedProfiles)
        val extractedProfilesFromMirrorSites = extractedProfilesFromBrokers.getMirrorSites(
            currentTimeProvider.currentTimeMillis(),
            extractedProfiles,
        )

        return extractedProfilesFromBrokers + extractedProfilesFromMirrorSites
    }

    private suspend fun getAllExtantMirrorSites(currentTimeMillis: Long): List<MirrorSite> = pirRepository.getAllMirrorSites().filter {
        it.isExtant(currentTimeMillis)
    }

    private suspend fun getInProgressAndCompletedBrokers(): List<DashboardBrokerWithStatus> {
        // Only consider active brokers and ignore removed ones
        val activeBrokerMap = pirRepository.getAllActiveBrokerObjects().associateBy { it.name }
        val activeBrokerOptOutUrls = pirRepository.getAllBrokerOptOutUrls()

        // Take all scan jobs that is for an active broker, this automatically remove INVALID jobs - which should be the case of removed profiles
        val validScanJobMap = pirSchedulingRepository.getAllValidScanJobRecords().filter {
            it.brokerName in activeBrokerMap.keys
        }.groupBy {
            it.brokerName
        }

        return validScanJobMap.mapNotNull {
            val broker = activeBrokerMap[it.key] ?: return@mapNotNull null
            val parentBroker = broker.parent?.let { parentName ->
                activeBrokerMap[parentName]
            }

            val scanJobsForBroker = it.value

            val hasAtLeastOneInProgress = scanJobsForBroker.any { job -> job.lastScanDateInMillis != 0L }

            if (!hasAtLeastOneInProgress) {
                return@mapNotNull null
            }

            // In progress if at least one scan job is not completed.
            val isInProgress = scanJobsForBroker.any { job -> job.lastScanDateInMillis == 0L }
            val firstScanDateInMillis = scanJobsForBroker.maxOfOrNull { job -> job.lastScanDateInMillis } ?: 0L

            DashboardBrokerWithStatus(
                broker = DashboardBroker(
                    name = broker.name,
                    url = broker.url,
                    parentUrl = parentBroker?.url,
                    optOutUrl = activeBrokerOptOutUrls[broker.name],
                ),
                status = if (isInProgress) IN_PROGRESS else COMPLETED,
                firstScanDateInMillis = firstScanDateInMillis,
            )
        }
    }

    private suspend fun List<DashboardBrokerWithStatus>.getMirrorSites(currentTimeMillis: Long): List<DashboardBrokerWithStatus> {
        val dashboardBrokerMap = this.associateBy { it.broker.name }
        val activeMirrorSitesMap = getAllExtantMirrorSites(currentTimeMillis).filter { mirrorSite ->
            // We take any mirror site whose parent is part of the dashboard brokers
            mirrorSite.parentSite in dashboardBrokerMap.keys
        }

        return activeMirrorSitesMap.mapNotNull { mirrorSite ->
            val parentBroker = dashboardBrokerMap[mirrorSite.parentSite] ?: return@mapNotNull null

            DashboardBrokerWithStatus(
                broker = DashboardBroker(
                    name = mirrorSite.name,
                    url = mirrorSite.url,
                    parentUrl = parentBroker.broker.url,
                    optOutUrl = mirrorSite.optOutUrl,
                ),
                status = parentBroker.status,
                firstScanDateInMillis = parentBroker.firstScanDateInMillis,
            )
        }
    }

    private suspend fun getExtractedProfileResultForBrokers(
        extractedProfiles: List<ExtractedProfile>,
    ): List<DashboardExtractedProfileResult> {
        // Consider only active brokers and ignore removed ones
        val activeBrokerMap = pirRepository.getAllActiveBrokerObjects().associateBy { it.name }
        val brokerOptOutUrls = pirRepository.getAllBrokerOptOutUrls()
        // Consider only active extracted profiles, removed extracted profiles AND profileQueries should be marked as deprecated
        val optOutJobRecords = pirSchedulingRepository.getAllValidOptOutJobRecords()
        val removedOptOuts = optOutJobRecords.filter {
            it.status == REMOVED
        }.map { it.extractedProfileId }.toSet()
        val optOutMap = pirSchedulingRepository.getAllValidOptOutJobRecords().filter {
            it.status != REMOVED
        }.associateBy {
            it.extractedProfileId
        }

        return extractedProfiles.filter {
            !it.deprecated && it.dbId !in removedOptOuts
        }.mapNotNull { extractedProfile ->
            val broker = activeBrokerMap[extractedProfile.brokerName] ?: return@mapNotNull null
            val optOutJob = optOutMap[extractedProfile.dbId]

            DashboardExtractedProfileResult(
                extractedProfile = extractedProfile,
                broker = DashboardBroker(
                    name = broker.name,
                    url = broker.url,
                    parentUrl = broker.parent,
                    optOutUrl = brokerOptOutUrls[broker.name],
                ),
                optOutSubmittedDateInMillis = optOutJob?.optOutRequestedDateInMillis,
                optOutRemovedDateInMillis = optOutJob?.optOutRemovedDateInMillis,
                estimatedRemovalDateInMillis = getEstimatedRemovalDateInMillis(
                    optOutJob?.optOutRequestedDateInMillis ?: 0L,
                    extractedProfile.dateAddedInMillis,
                ),
                hasMatchingRecordOnParentBroker = broker.parent?.let {
                    extractedProfile.hasMatchingProfileOnParent(extractedProfiles)
                } ?: false,
            )
        }
    }

    private fun ExtractedProfile.hasMatchingProfileOnParent(extractedProfiles: List<ExtractedProfile>): Boolean {
        return extractedProfiles.any {
            it.brokerName == this.brokerName && this.matches(it)
        }
    }

    private fun ExtractedProfile.matches(extractedProfile: ExtractedProfile): Boolean {
        // TODO: Add address check
        return this.name == extractedProfile.name &&
            this.age == extractedProfile.age &&
            (
                this.alternativeNames.containsAll(extractedProfile.alternativeNames) ||
                    extractedProfile.alternativeNames.containsAll(this.alternativeNames)
                ) &&
            (this.relatives.containsAll(extractedProfile.relatives) || extractedProfile.relatives.containsAll(this.relatives))
    }

    private suspend fun List<DashboardExtractedProfileResult>.getMirrorSites(
        currentTimeMillis: Long,
        extractedProfiles: List<ExtractedProfile>,
    ): List<DashboardExtractedProfileResult> {
        val dashboardResultMap = this.groupBy { it.broker.name }
        val activeMirrorSites = getAllExtantMirrorSites(currentTimeMillis).filter {
            it.parentSite in dashboardResultMap.keys
        }

        return activeMirrorSites.mapNotNull { mirrorSite ->
            val parentBrokerResults = dashboardResultMap[mirrorSite.parentSite] ?: return@mapNotNull null

            parentBrokerResults.map { result ->
                DashboardExtractedProfileResult(
                    extractedProfile = result.extractedProfile,
                    broker = DashboardBroker(
                        name = mirrorSite.name,
                        url = mirrorSite.url,
                        parentUrl = mirrorSite.parentSite,
                        optOutUrl = result.broker.optOutUrl,
                    ),
                    optOutSubmittedDateInMillis = result.optOutSubmittedDateInMillis,
                    optOutRemovedDateInMillis = result.optOutRemovedDateInMillis,
                    estimatedRemovalDateInMillis = result.estimatedRemovalDateInMillis,
                    hasMatchingRecordOnParentBroker = mirrorSite.parentSite.let {
                        result.extractedProfile.hasMatchingProfileOnParent(extractedProfiles)
                    },
                )
            }
        }.flatten()
    }

    private fun getEstimatedRemovalDateInMillis(
        optOutRequestedDateInMillis: Long,
        addedDateInMillis: Long,
    ): Long {
        return if (optOutRequestedDateInMillis != 0L) {
            optOutRequestedDateInMillis + ESTIMATED_REMOVAL_TIME_IN_MILLIS
        } else {
            addedDateInMillis + ESTIMATED_REMOVAL_TIME_IN_MILLIS
        }
    }

    companion object {
        private val ESTIMATED_REMOVAL_TIME_IN_MILLIS = TimeUnit.DAYS.toMillis(14)
    }
}
