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
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerCompanySignal
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.app.global.formatters.time.TimeDiffFormatter
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.*
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class AppTPCompanyTrackersViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val statsRepository = mock<AppTrackerBlockingStatsRepository>()
    private val appsRepository = mock<TrackingProtectionAppsRepository>()
    private val timeDiffFormatter = mock<TimeDiffFormatter>()

    private lateinit var viewModel: AppTPCompanyTrackersViewModel

    @Before
    fun setup() {
        viewModel = AppTPCompanyTrackersViewModel(
            statsRepository,
            appsRepository,
            timeDiffFormatter,
            CoroutineTestRule().testDispatcherProvider
        )
    }

    @Ignore
    @Test
    fun whenLoadsDataReturnsTrackersForAppFromDate() = runBlocking {
        val date = "2020-10-21"
        val packageName = "com.duckduckgo.android"

        val someTrackers = someTrackers()

        whenever(statsRepository.getTrackersForAppFromDate(packageName, date)).thenReturn(getTrackersFlow(someTrackers))
        viewModel.viewState().test {
            Assert.assertEquals(someTrackers, awaitItem())
        }

        viewModel.loadData(date, packageName)
    }

    private fun getTrackersFlow(trackers: List<VpnTrackerCompanySignal>): Flow<List<VpnTrackerCompanySignal>> = flow {
        while (true) {
            emit(trackers)
        }
    }

    private fun someTrackers(): List<VpnTrackerCompanySignal> {
        val defaultTrackingApp = TrackingApp("app.foo.com", "Foo App")
        val domain: String = "example.com"
        val trackerCompanyId: Int = -1
        val timestamp: String = DatabaseDateFormatter.bucketByHour()

        return listOf(
            VpnTrackerCompanySignal(
                VpnTracker(
                    trackerCompanyId = trackerCompanyId,
                    domain = domain,
                    timestamp = timestamp,
                    company = "",
                    companyDisplayName = "",
                    trackingApp = defaultTrackingApp
                ),
                AppTrackerEntity(trackerCompanyId, "Google", 100, listOf("unique_identifier"))
            )
        )
    }
}
