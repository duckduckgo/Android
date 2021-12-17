/*
 * Copyright (c) 2021 DuckDuckGo
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

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.*

@ExperimentalTime
@ExperimentalCoroutinesApi
class AppTPCompanyTrackersViewModelTest {

    @get:Rule @Suppress("unused") val coroutineRule = CoroutineTestRule()

    private val repository = mock<AppTrackerBlockingStatsRepository>()

    private lateinit var viewModel: AppTPCompanyTrackersViewModel

    @Before
    fun setup() {
        viewModel =
            AppTPCompanyTrackersViewModel(repository, CoroutineTestRule().testDispatcherProvider)
    }

    @Ignore
    @Test
    fun whenLoadsDataReturnsTrackersForAppFromDate() = runBlocking {
        val date = "2020-10-21"
        val packageName = "com.duckduckgo.android"

        val someTrackers = someTrackers()

        whenever(repository.getTrackersForAppFromDate(packageName, date))
            .thenReturn(getTrackersFlow(someTrackers))
        viewModel.getTrackersForAppFromDate(date, packageName).test {
            Assert.assertEquals(someTrackers, awaitItem())
        }
    }

    private fun getTrackersFlow(trackers: List<VpnTracker>): Flow<List<VpnTracker>> = flow {
        while (true) {
            emit(trackers)
        }
    }

    private fun someTrackers(): List<VpnTracker> {
        val defaultTrackingApp = TrackingApp("app.foo.com", "Foo App")
        val domain: String = "example.com"
        val trackerCompanyId: Int = -1
        val timestamp: String = DatabaseDateFormatter.bucketByHour()
        return listOf(
            VpnTracker(
                trackerCompanyId = trackerCompanyId,
                domain = domain,
                timestamp = timestamp,
                company = "",
                companyDisplayName = "",
                trackingApp = defaultTrackingApp))
    }
}
