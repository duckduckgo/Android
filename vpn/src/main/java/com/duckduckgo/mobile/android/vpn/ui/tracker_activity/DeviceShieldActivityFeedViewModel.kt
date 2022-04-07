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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.BucketizedVpnTracker
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.app.global.formatters.time.TimeDiffFormatter
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerCompanyBadge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.threeten.bp.LocalDateTime
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@ContributesViewModel(AppScope::class)
class DeviceShieldActivityFeedViewModel @Inject constructor(
    private val statsRepository: AppTrackerBlockingStatsRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val timeDiffFormatter: TimeDiffFormatter
) : ViewModel() {

    private val MAX_BADGES_TO_DISPLAY = 5
    private val MAX_ELEMENTS_TO_DISPLAY = 6

    private val tickerChannel = MutableStateFlow(System.currentTimeMillis())
    private var tickerJob: Job? = null

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

    suspend fun getMostRecentTrackers(
        timeWindow: TimeWindow,
        showHeadings: Boolean
    ): Flow<List<TrackerFeedItem>> = withContext(dispatcherProvider.io()) {
        return@withContext statsRepository.getMostRecentVpnTrackers { timeWindow.asString() }
            .combine(tickerChannel.asStateFlow()) { trackers, _ -> trackers }
            .map { aggregateDataPerApp(it, showHeadings) }
            .flowOn(Dispatchers.Default)
            .map { it.ifEmpty { listOf(TrackerFeedItem.TrackerEmptyFeed) } }
            .onStart {
                startTickerRefresher()
                emit(listOf(TrackerFeedItem.TrackerLoadingSkeleton))
                delay(300)
            }
    }

    private suspend fun aggregateDataPerApp(
        trackerData: List<BucketizedVpnTracker>,
        showHeadings: Boolean
    ): List<TrackerFeedItem> {
        val sourceData = mutableListOf<TrackerFeedItem>()
        val perSessionData = trackerData.groupBy { it.bucket }

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
                    val trackeCompanyPrevalence = trackerBucket.value.first().trackerCompanySignal.trackerEntity.score
                    trackingCompanyInfo.add(
                        TrackingCompanyInfo(
                            companyName = trackerCompanyName,
                            companyDisplayName = trackerCompanyDisplayName,
                            timestamp = timestamp,
                            companyPrevalence = trackeCompanyPrevalence
                        )
                    )
                    totalTrackerCount += trackerBucket.value.size
                }

                if (firstInBucket && showHeadings) {
                    sourceData.add(TrackerFeedItem.TrackerFeedItemHeader(item.trackerCompanySignal.tracker.timestamp))
                        .also { firstInBucket = false }
                }

                sourceData.add(
                    TrackerFeedItem.TrackerFeedData(
                        id = item.bucket.hashCode() + item.trackerCompanySignal.tracker.trackingApp.packageId.hashCode(),
                        bucket = item.bucket,
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

    data class TimeWindow(
        val value: Long,
        val unit: TimeUnit
    ) {
        fun asString(): String {
            return DatabaseDateFormatter.timestamp(LocalDateTime.now().minusSeconds(unit.toSeconds(value)))
        }
    }
}
