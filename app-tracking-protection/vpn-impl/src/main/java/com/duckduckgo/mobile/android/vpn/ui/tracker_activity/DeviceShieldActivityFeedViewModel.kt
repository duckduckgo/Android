/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.mobile.android.vpn.model.BucketizedVpnTracker
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.app.global.formatters.time.TimeDiffFormatter
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.TimeWindow
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.ERROR
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerCompanyBadge
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem.TrackerDescriptionFeed
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem.TrackerLoadingSkeleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.threeten.bp.LocalDateTime
import timber.log.Timber
import java.lang.Integer.min
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@ContributesViewModel(FragmentScope::class)
class DeviceShieldActivityFeedViewModel @Inject constructor(
    private val statsRepository: AppTrackerBlockingStatsRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val timeDiffFormatter: TimeDiffFormatter,
    private val excludedApps: TrackingProtectionAppsRepository,
    private val vpnStateMonitor: VpnStateMonitor
) : ViewModel() {

    private val MAX_BADGES_TO_DISPLAY = 5
    private val MAX_ELEMENTS_TO_DISPLAY = 6
    private val MAX_ICONS_TO_DISPLAY = 4

    private val tickerChannel = MutableStateFlow(System.currentTimeMillis())
    private var tickerJob: Job? = null

    private val refreshVpnRunningState = MutableStateFlow(System.currentTimeMillis())

    private fun startTickerRefresher() {
        Timber.i("startTickerRefresher")
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(1))
                tickerChannel.emit(System.currentTimeMillis())
            }
        }
    }

    private suspend fun getRunningState(): Flow<VpnState> = withContext(dispatcherProvider.io()) {
        return@withContext vpnStateMonitor
            .getStateFlow(AppTpVpnFeature.APPTP_VPN)
            .combine(refreshVpnRunningState.asStateFlow()) { state, _ -> state }
    }

    suspend fun getMostRecentTrackers(
        timeWindow: TimeWindow,
        showHeadings: Boolean
    ): Flow<TrackerFeedViewState> = withContext(dispatcherProvider.io()) {
        return@withContext statsRepository.getMostRecentVpnTrackers { timeWindow.asString() }
            .combine(tickerChannel.asStateFlow()) { trackers, _ -> trackers }
            .map { aggregateDataPerApp(it, showHeadings) }
            .combine(getRunningState()) { trackers, runningState ->
                TrackerFeedIntermediateData(trackers, runningState)
            }
            .combine(getAppsData()) { trackerIntermediateState, appsProtectionData ->
                if (trackerIntermediateState.trackers.isEmpty()) {
                    TrackerFeedViewState(listOf(TrackerDescriptionFeed), appsProtectionData, trackerIntermediateState.runningState)
                } else {
                    TrackerFeedViewState(trackerIntermediateState.trackers, appsProtectionData, trackerIntermediateState.runningState)
                }
            }
            .flowOn(dispatcherProvider.default())
            .onStart {
                startTickerRefresher()
                emit(TrackerFeedViewState(listOf(TrackerLoadingSkeleton), null, VpnState(DISABLED, ERROR)))
                delay(300)
            }
    }

    data class AppsData(
        val appsCount: Int,
        val isProtected: Boolean,
        val packageNames: List<String>
    )

    data class AppsProtectionData(
        val protectedAppsData: AppsData,
        val unprotectedAppsData: AppsData
    )

    internal data class TrackerFeedIntermediateData(
        val trackers: List<TrackerFeedItem>,
        val runningState: VpnState
    )
    data class TrackerFeedViewState(
        val trackers: List<TrackerFeedItem>,
        val appsProtectionData: AppsProtectionData?,
        val vpnState: VpnState
    )

    private suspend fun getAppsData(): Flow<AppsProtectionData> = withContext(dispatcherProvider.io()) {
        return@withContext excludedApps.getAppsAndProtectionInfo().map { list ->
            val unprotected = list.filter { it.isExcluded }
            val protected = list.filter { !it.isExcluded }
            val unprotectedCount = unprotected.size
            val protectedCount = list.size - unprotectedCount

            val protectedAppsData = AppsData(
                appsCount = protectedCount,
                isProtected = true,
                packageNames = getPackageNamesList(protected)
            )

            val unProtectedAppsData = AppsData(
                appsCount = unprotectedCount,
                isProtected = false,
                packageNames = getPackageNamesList(unprotected)
            )

            AppsProtectionData(protectedAppsData, unProtectedAppsData)
        }
    }

    private fun getPackageNamesList(appInfoList: List<TrackingProtectionAppInfo>): List<String> {
        if (appInfoList.isEmpty()) return emptyList()
        val size = min(appInfoList.size, MAX_ICONS_TO_DISPLAY)
        return appInfoList.slice(IntRange(0, size - 1)).map { it.packageName }
    }

    private suspend fun aggregateDataPerApp(
        trackerData: List<BucketizedVpnTracker>,
        showHeadings: Boolean
    ): List<TrackerFeedItem> {
        val sourceData = mutableListOf<TrackerFeedItem>()
        val perSessionData = trackerData.groupBy { it.trackerCompanySignal.tracker.bucket }

        perSessionData.values.forEach { sessionTrackers ->
            coroutineContext.ensureActive()

            val perAppData = sessionTrackers.groupBy { it.trackerCompanySignal.tracker.trackingApp.packageId }
            var firstInBucket = true

            perAppData.values.forEach { appTrackers ->
                val item = appTrackers.sortedByDescending { it.trackerCompanySignal.tracker.timestamp }.first()

                var totalTrackerCount = 0
                val perTrackerData = appTrackers.groupBy { it.trackerCompanySignal.tracker.company }
                val trackingCompanyInfo = mutableListOf<TrackingCompanyInfo>()
                perTrackerData.forEach { trackerBucket ->
                    val trackerCompanyName = trackerBucket.value.first().trackerCompanySignal.tracker.company
                    val trackerCompanyDisplayName = trackerBucket.value.first().trackerCompanySignal.tracker.companyDisplayName
                    val timestamp =
                        trackerBucket.value
                            .sortedByDescending { it.trackerCompanySignal.tracker.timestamp }
                            .first().trackerCompanySignal.tracker.timestamp
                    val trackerCompanyPrevalence = trackerBucket.value.first().trackerCompanySignal.trackerEntity.score
                    trackingCompanyInfo.add(
                        TrackingCompanyInfo(
                            companyName = trackerCompanyName,
                            companyDisplayName = trackerCompanyDisplayName,
                            timestamp = timestamp,
                            companyPrevalence = trackerCompanyPrevalence
                        )
                    )
                    totalTrackerCount += trackerBucket.value.sumOf { it.trackerCompanySignal.tracker.count }
                }

                if (firstInBucket && showHeadings) {
                    sourceData.add(TrackerFeedItem.TrackerFeedItemHeader(item.trackerCompanySignal.tracker.timestamp))
                        .also { firstInBucket = false }
                }

                sourceData.add(
                    TrackerFeedItem.TrackerFeedData(
                        id = item.trackerCompanySignal.tracker.bucket.hashCode() + item.trackerCompanySignal.tracker.trackingApp.packageId.hashCode(),
                        bucket = item.trackerCompanySignal.tracker.bucket,
                        trackingApp = TrackingApp(
                            item.trackerCompanySignal.tracker.trackingApp.packageId,
                            item.trackerCompanySignal.tracker.trackingApp.appDisplayName
                        ),
                        trackersTotalCount = totalTrackerCount,
                        trackingCompanyBadges = mapTrackingCompanies(trackingCompanyInfo),
                        timestamp = item.trackerCompanySignal.tracker.timestamp,
                        displayTimestamp = timeDiffFormatter.formatTimePassed(
                            LocalDateTime.now(),
                            LocalDateTime.parse(item.trackerCompanySignal.tracker.timestamp)
                        )
                    )
                )
            }
        }

        return sourceData
    }

    private fun mapTrackingCompanies(trackingCompanyInfo: MutableList<TrackingCompanyInfo>): List<TrackerCompanyBadge> {
        val trackingBadges = mutableListOf<TrackerCompanyBadge>()
        val trackingCompanies = trackingCompanyInfo.sortedByDescending { it.companyPrevalence }
        if (trackingCompanies.size > MAX_ELEMENTS_TO_DISPLAY) {
            val visibleBadges = trackingCompanies.take(MAX_BADGES_TO_DISPLAY)
            visibleBadges.forEach {
                trackingBadges.add(TrackerCompanyBadge.Company(it.companyName, it.companyDisplayName))
            }
            trackingBadges.add(TrackerCompanyBadge.Extra(trackingCompanies.size - MAX_BADGES_TO_DISPLAY))
        } else {
            trackingCompanies.forEach {
                trackingBadges.add(TrackerCompanyBadge.Company(it.companyName, it.companyDisplayName))
            }
        }
        return trackingBadges
    }

    data class TrackingCompanyInfo(
        val companyName: String,
        val companyDisplayName: String,
        val timestamp: String,
        val companyPrevalence: Int
    )
}
