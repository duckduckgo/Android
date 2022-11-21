/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.stats

import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.*
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import org.threeten.bp.LocalDateTime

interface AppTrackerBlockingStatsRepository {

    data class TimeWindow(
        val value: Long,
        val unit: TimeUnit,
    ) {
        fun asString(): String {
            return DatabaseDateFormatter.timestamp(LocalDateTime.now().minusSeconds(unit.toSeconds(value)))
        }
    }

    fun noStartDate(): String {
        return DatabaseDateFormatter.timestamp(LocalDateTime.of(2000, 1, 1, 0, 0))
    }

    private fun noEndDate(): String {
        return DatabaseDateFormatter.timestamp(LocalDateTime.of(9999, 1, 1, 0, 0))
    }

    fun getVpnTrackers(
        startTime: () -> String,
        endTime: String = noEndDate(),
    ): Flow<List<VpnTracker>>

    fun getMostRecentVpnTrackers(startTime: () -> String): Flow<List<BucketizedVpnTracker>>

    fun getVpnTrackersSync(
        startTime: () -> String,
        endTime: String = noEndDate(),
    ): List<VpnTracker>

    fun getTrackersForAppFromDate(
        date: String,
        packageName: String,
    ): Flow<List<VpnTrackerCompanySignal>>

    fun getBlockedTrackersCountBetween(
        startTime: () -> String,
        endTime: String = noEndDate(),
    ): Flow<Int>

    fun getTrackingAppsCountBetween(
        startTime: () -> String,
        endTime: String = noEndDate(),
    ): Flow<Int>
}

@ContributesBinding(AppScope::class)
class RealAppTrackerBlockingStatsRepository @Inject constructor(
    val vpnDatabase: VpnDatabase,
    private val dispatchers: DispatcherProvider,
) : AppTrackerBlockingStatsRepository {

    private val trackerDao = vpnDatabase.vpnTrackerDao()

    override fun getVpnTrackers(
        startTime: () -> String,
        endTime: String,
    ): Flow<List<VpnTracker>> {
        return trackerDao.getTrackersBetween(startTime(), endTime)
            .conflate()
            .distinctUntilChanged()
            .map { it.filter { tracker -> tracker.timestamp >= startTime() } }
            .flowOn(dispatchers.default())
    }

    @WorkerThread
    override fun getVpnTrackersSync(
        startTime: () -> String,
        endTime: String,
    ): List<VpnTracker> {
        return trackerDao.getTrackersBetweenSync(startTime(), endTime)
            .filter { tracker -> tracker.timestamp >= startTime() }
    }

    @WorkerThread
    override fun getMostRecentVpnTrackers(startTime: () -> String): Flow<List<BucketizedVpnTracker>> {
        return trackerDao.getPagedTrackersSince(startTime())
            .conflate()
            .distinctUntilChanged()
    }

    @WorkerThread
    override fun getTrackersForAppFromDate(
        date: String,
        packageName: String,
    ): Flow<List<VpnTrackerCompanySignal>> {
        return trackerDao.getTrackersForAppFromDate(date, packageName)
            .conflate()
            .distinctUntilChanged()
    }

    @WorkerThread
    override fun getBlockedTrackersCountBetween(
        startTime: () -> String,
        endTime: String,
    ): Flow<Int> {
        return trackerDao.getTrackersCountBetween(startTime(), endTime)
            .conflate()
            .distinctUntilChanged()
    }

    @WorkerThread
    override fun getTrackingAppsCountBetween(
        startTime: () -> String,
        endTime: String,
    ): Flow<Int> {
        return trackerDao.getTrackingAppsCountBetween(startTime(), endTime)
            .conflate()
            .distinctUntilChanged()
    }
}
