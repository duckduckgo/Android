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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.TimeDiffFormatter
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerWithEntity
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.ERROR
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.TimeWindow
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.AppsData
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.AppsProtectionData
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerCompanyBadge
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem.TrackerDescriptionFeed
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem.TrackerLoadingSkeleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import logcat.logcat
import java.lang.Integer.min
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@ContributesViewModel(FragmentScope::class)
class DeviceShieldActivityFeedViewModel @Inject constructor(
    private val statsRepository: AppTrackerBlockingStatsRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val timeDiffFormatter: TimeDiffFormatter,
    private val excludedApps: TrackingProtectionAppsRepository,
    private val vpnStateMonitor: VpnStateMonitor,
) : ViewModel() {

    private val MAX_BADGES_TO_DISPLAY = 5
    private val MAX_ELEMENTS_TO_DISPLAY = 6
    private val MAX_ICONS_TO_DISPLAY = 4

    private val tickerChannel = MutableStateFlow(System.currentTimeMillis())
    private var tickerJob: Job? = null

    sealed class Command {
        data class ShowProtectedAppsList(
            val vpnState: VpnState,
        ) : Command()
        data class ShowUnprotectedAppsList(
            val vpnState: VpnState,
        ) : Command()
        data class TrackerListDisplayed(
            val trackersListSize: Int,
        ) : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    private fun startTickerRefresher() {
        logcat { "startTickerRefresher" }
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
    }

    suspend fun getMostRecentTrackers(
        timeWindow: TimeWindow,
        config: DeviceShieldActivityFeedFragment.ActivityFeedConfig,
    ): Flow<TrackerFeedViewState> = withContext(dispatcherProvider.io()) {
        val showHeadings = config.showTimeWindowHeadings
        return@withContext statsRepository.getMostRecentVpnTrackers { timeWindow.asString() }
            .combine(tickerChannel.asStateFlow()) { trackers, _ -> trackers }
            .map { aggregateDataPerApp(it, showHeadings) }
            .combine(getRunningState()) { trackers, runningState ->
                TrackerFeedIntermediateData(trackers, runningState)
            }
            .combine(getAppsData()) { trackerIntermediateState, appsProtectionData ->
                val trackers = if (trackerIntermediateState.trackers.isEmpty()) {
                    listOf(TrackerDescriptionFeed)
                } else {
                    trackerIntermediateState.trackers
                }
                val appDataItems = if (appendAppsData(trackers, trackerIntermediateState.runningState, appsProtectionData, config)) {
                    listOf(TrackerFeedItem.TrackerTrackerAppsProtection(appsProtectionData))
                } else {
                    emptyList()
                }
                TrackerFeedViewState(trackers + appDataItems, trackerIntermediateState.runningState)
            }
            .flowOn(dispatcherProvider.io())
            .onStart {
                startTickerRefresher()
                emit(TrackerFeedViewState(listOf(TrackerLoadingSkeleton), VpnState(DISABLED, ERROR)))
                delay(300)
            }
    }

    fun trackerListDisplayed(viewState: TrackerFeedViewState) {
        viewModelScope.launch {
            if (viewState.trackers.isNotEmpty() && viewState.trackers.first() != TrackerLoadingSkeleton) {
                command.send(
                    Command.TrackerListDisplayed(viewState.trackers.count { it is TrackerFeedItem.TrackerFeedData }),
                )
            }
        }
    }

    fun showAppsList(vpnState: VpnState, item: TrackerFeedItem.TrackerTrackerAppsProtection) {
        viewModelScope.launch {
            if (item.selectedFilter == TrackingProtectionExclusionListActivity.Companion.AppsFilter.PROTECTED_ONLY) {
                command.send(Command.ShowProtectedAppsList(vpnState))
            } else if (item.selectedFilter == TrackingProtectionExclusionListActivity.Companion.AppsFilter.UNPROTECTED_ONLY) {
                command.send(Command.ShowUnprotectedAppsList(vpnState))
            }
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    internal data class TrackerFeedIntermediateData(
        val trackers: List<TrackerFeedItem>,
        val runningState: VpnState,
    )
    data class TrackerFeedViewState(
        val trackers: List<TrackerFeedItem>,
        val vpnState: VpnState,
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
                packageNames = getPackageNamesList(protected),
            )

            val unProtectedAppsData = AppsData(
                appsCount = unprotectedCount,
                isProtected = false,
                packageNames = getPackageNamesList(unprotected),
            )

            AppsProtectionData(protectedAppsData, unProtectedAppsData)
        }
    }

    private fun appendAppsData(
        trackers: List<TrackerFeedItem>,
        vpnState: VpnState,
        appsProtectionData: AppsProtectionData?,
        config: DeviceShieldActivityFeedFragment.ActivityFeedConfig,
    ): Boolean {
        return vpnState.state == VpnStateMonitor.VpnRunningState.ENABLED &&
            trackers.size < config.maxRows &&
            !config.unboundedRows() &&
            appsProtectionData != null
    }

    private fun getPackageNamesList(appInfoList: List<TrackingProtectionAppInfo>): List<String> {
        if (appInfoList.isEmpty()) return emptyList()
        val size = min(appInfoList.size, MAX_ICONS_TO_DISPLAY)
        return appInfoList.slice(IntRange(0, size - 1)).map { it.packageName }
    }

    private suspend fun aggregateDataPerApp(
        trackerData: List<VpnTrackerWithEntity>,
        showHeadings: Boolean,
    ): List<TrackerFeedItem> {
        val sourceData = mutableListOf<TrackerFeedItem>()
        val perSessionData = trackerData.groupBy { it.tracker.bucket }

        perSessionData.values.forEach { sessionTrackers ->
            coroutineContext.ensureActive()

            val perAppData = sessionTrackers.groupBy { it.tracker.trackingApp.packageId }
            var firstInBucket = true

            perAppData.values.forEach { appTrackers ->
                val item = appTrackers.sortedByDescending { it.tracker.timestamp }.first()

                var totalTrackerCount = 0
                val perTrackerData = appTrackers.groupBy { it.tracker.company }
                val trackingCompanyInfo = mutableListOf<TrackingCompanyInfo>()
                perTrackerData.forEach { trackerBucket ->
                    val trackerCompanyName = trackerBucket.value.first().tracker.company
                    val trackerCompanyDisplayName = trackerBucket.value.first().tracker.companyDisplayName
                    val timestamp =
                        trackerBucket.value
                            .sortedByDescending { it.tracker.timestamp }
                            .first().tracker.timestamp
                    val trackerCompanyPrevalence = trackerBucket.value.first().trackerEntity.score
                    trackingCompanyInfo.add(
                        TrackingCompanyInfo(
                            companyName = trackerCompanyName,
                            companyDisplayName = trackerCompanyDisplayName,
                            timestamp = timestamp,
                            companyPrevalence = trackerCompanyPrevalence,
                        ),
                    )
                    totalTrackerCount += trackerBucket.value.sumOf { it.tracker.count }
                }

                if (firstInBucket && showHeadings) {
                    sourceData.add(
                        TrackerFeedItem.TrackerFeedItemHeader(
                            timeDiffFormatter.formatTimePassedInDays(
                                LocalDateTime.now(),
                                LocalDateTime.parse(item.tracker.timestamp),
                            ),
                        ),
                    ).also { firstInBucket = false }
                }

                sourceData.add(
                    TrackerFeedItem.TrackerFeedData(
                        id = item.tracker.bucket.hashCode() + item.tracker.trackingApp.packageId.hashCode(),
                        bucket = item.tracker.bucket,
                        trackingApp = TrackingApp(
                            item.tracker.trackingApp.packageId,
                            item.tracker.trackingApp.appDisplayName,
                        ),
                        trackersTotalCount = totalTrackerCount,
                        trackingCompanyBadges = mapTrackingCompanies(trackingCompanyInfo),
                        timestamp = item.tracker.timestamp,
                        displayTimestamp = timeDiffFormatter.formatTimePassed(
                            LocalDateTime.now(),
                            LocalDateTime.parse(item.tracker.timestamp),
                        ),
                    ),
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
        val companyPrevalence: Int,
    )
}
