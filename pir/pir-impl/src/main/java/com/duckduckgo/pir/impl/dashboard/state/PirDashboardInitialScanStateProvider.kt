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
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBrokerWithStatus
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBrokerWithStatus.Status.COMPLETED
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBrokerWithStatus.Status.IN_PROGRESS
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
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
    suspend fun getAllScannedBrokersStatus(): List<DashboardBrokerWithStatus>

    /**
     * Returns all results which could either be:
     * - a valid ExtractedProfile from a scan for an active Broker x ProfileQuery
     * - a valid ExtractedProfile for a MirrorSite
     */
    suspend fun getScanResults(): List<DashboardExtractedProfileResult>

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
}

@ContributesBinding(
    scope = ActivityScope::class,
    boundType = PirDashboardInitialScanStateProvider::class,
)
@SingleInstanceIn(ActivityScope::class)
class RealPirDashboardInitialScanStateProviderProvider @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirRepository: PirRepository,
    pirSchedulingRepository: PirSchedulingRepository,
) : PirDashboardStateProvider(currentTimeProvider, pirRepository, pirSchedulingRepository),
    PirDashboardInitialScanStateProvider {
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
        return getAllScannedBrokersStatus().filter {
            it.status == COMPLETED
        }.size
    }

    override suspend fun getAllScannedBrokersStatus(): List<DashboardBrokerWithStatus> {
        return getBrokersAndMirrorSitesWithProgressStatus()
    }

    override suspend fun getScanResults(): List<DashboardExtractedProfileResult> {
        return getExtractedProfileResults()
    }
}
