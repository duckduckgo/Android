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

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.model.BucketizedVpnTracker
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.OriginatingAppPackageIdentifier
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.OriginatingAppPackageIdentifierStrategy
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerInfo
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DeviceShieldActivityFeedViewModel(
    private val statsRepository: AppTrackerBlockingStatsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    suspend fun getMostRecentTrackers(timeWindow: TimeWindow): Flow<List<TrackerFeedItem>> = withContext(dispatcherProvider.io()) {
        return@withContext statsRepository.getMostRecentVpnTrackers { timeWindow.asString() }
            .map { aggregateDataPerApp(it) }
            .map { if (it.isEmpty()) listOf(TrackerFeedItem.TrackerEmptyFeed) else it }
            .onStart { emit(listOf(TrackerFeedItem.TrackerEmptyFeed)) }
    }

    private fun aggregateDataPerApp(trackerData: List<BucketizedVpnTracker>): List<TrackerFeedItem> {
        val sourceData = mutableListOf<TrackerFeedItem>()
        val perSessionData = trackerData.groupBy { it.bucket }

        perSessionData.values.forEach { sessionTrackers ->
            val perAppData = sessionTrackers.filter { it.vpnTracker.trackingApp.appDisplayName != OriginatingAppPackageIdentifierStrategy.UNKNOWN }
                .groupBy { it.vpnTracker.trackingApp.packageId }
            var firstInBucket = true

            perAppData.values.forEach { appTrackers ->
                val item = appTrackers.sortedByDescending { it.vpnTracker.timestamp }.first()

                var totalTrackerCount = 0
                val perTrackerData = appTrackers.groupBy { it.vpnTracker.company }
                val trackerList = mutableListOf<TrackerInfo>()
                perTrackerData.forEach { trackerBucket ->
                    val trackerCompanyName = trackerBucket.value.first().vpnTracker.company
                    val trackerCompanyDisplayName = trackerBucket.value.first().vpnTracker.companyDisplayName
                    val timestamp = trackerBucket.value.sortedByDescending { it.vpnTracker.timestamp }.first().vpnTracker.timestamp
                    trackerList.add(
                        TrackerInfo(
                            companyName = trackerCompanyName,
                            companyDisplayName = trackerCompanyDisplayName,
                            timestamp = timestamp
                        )
                    )
                    totalTrackerCount += trackerBucket.value.size
                }

                if (firstInBucket) {
                    sourceData.add(TrackerFeedItem.TrackerFeedItemHeader(item.vpnTracker.timestamp))
                }

                sourceData.add(
                    TrackerFeedItem.TrackerFeedData(
                        id = item.bucket.hashCode() + item.vpnTracker.trackingApp.packageId.hashCode(),
                        bucket = item.bucket,
                        trackingApp = TrackingApp(
                            item.vpnTracker.trackingApp.packageId,
                            item.vpnTracker.trackingApp.appDisplayName
                        ),
                        trackersTotalCount = totalTrackerCount,
                        trackers = trackerList.sortedByDescending { it.timestamp },
                        timestamp = item.vpnTracker.timestamp,
                        firstInBucket = firstInBucket.also { firstInBucket = false }
                    )
                )
            }
        }

        return sourceData
    }

    data class TimeWindow(val value: Long, val unit: TimeUnit) {
        fun asString(): String {
            return DatabaseDateFormatter.timestamp(LocalDateTime.now().minusSeconds(unit.toSeconds(value)))
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class DeviceShieldActivityFeedViewModelFactory @Inject constructor(
    private val repository: AppTrackerBlockingStatsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(DeviceShieldActivityFeedViewModel::class.java) -> (DeviceShieldActivityFeedViewModel(repository, dispatcherProvider) as T)
                else -> null
            }
        }
    }
}
