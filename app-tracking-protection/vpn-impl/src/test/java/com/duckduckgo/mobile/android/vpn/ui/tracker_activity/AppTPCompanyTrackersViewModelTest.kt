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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.common.utils.formatters.time.TimeDiffFormatter
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository.ProtectionState.PROTECTED
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository.ProtectionState.UNPROTECTED
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository.ProtectionState.UNPROTECTED_THROUGH_NETP
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerWithEntity
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.BannerState
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.ViewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.ExperimentalTime

@ExperimentalTime
class AppTPCompanyTrackersViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val statsRepository = mock<AppTrackerBlockingStatsRepository>()
    private val appsRepository = mock<TrackingProtectionAppsRepository>()
    private val timeDiffFormatter = mock<TimeDiffFormatter>()
    private val deviceShieldPixels = mock<DeviceShieldPixels>()

    private lateinit var viewModel: AppTPCompanyTrackersViewModel

    @Before
    fun setup() {
        viewModel = AppTPCompanyTrackersViewModel(
            statsRepository,
            appsRepository,
            timeDiffFormatter,
            deviceShieldPixels,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Ignore
    @Test
    fun whenLoadsDataReturnsTrackersForAppFromDate() = runBlocking {
        val date = "2020-10-21"
        val packageName = "com.duckduckgo.android"

        val someTrackers = someTrackers()

        whenever(statsRepository.getTrackersForAppFromDate(date, packageName)).thenReturn(getTrackersFlow(someTrackers))
        viewModel.viewState().test {
            assertEquals(someTrackers, awaitItem())
        }

        viewModel.loadData(date, packageName)
    }

    @Test
    fun whenAppIsUnprotectedThroughNetpThenReturnViewStateWithtoggleUncheckedDisabledAndShowCorrectBanner() = runTest {
        val date = "2020-10-21"
        val packageName = "com.duckduckgo.android"

        whenever(statsRepository.getTrackersForAppFromDate(date, packageName)).thenReturn(flowOf(emptyList()))
        whenever(appsRepository.getAppProtectionStatus(packageName)).thenReturn(UNPROTECTED_THROUGH_NETP)

        viewModel.loadData(date, packageName)

        viewModel.viewState().test {
            assertEquals(
                ViewState(
                    totalTrackingAttempts = 0,
                    lastTrackerBlockedAgo = "",
                    trackingCompanies = emptyList(),
                    toggleChecked = false,
                    bannerState = BannerState.SHOW_UNPROTECTED_THROUGH_NETP,
                    toggleEnabled = false,
                ),
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun whenAppIsProtectedThenReturnViewStateWithtoggleCheckedEnabledAndShowCorrectBanner() = runTest {
        val date = "2020-10-21"
        val packageName = "com.duckduckgo.android"

        whenever(statsRepository.getTrackersForAppFromDate(date, packageName)).thenReturn(flowOf(emptyList()))
        whenever(appsRepository.getAppProtectionStatus(packageName)).thenReturn(PROTECTED)

        viewModel.loadData(date, packageName)

        viewModel.viewState().test {
            assertEquals(
                ViewState(
                    totalTrackingAttempts = 0,
                    lastTrackerBlockedAgo = "",
                    trackingCompanies = emptyList(),
                    toggleChecked = true,
                    bannerState = BannerState.NONE,
                    toggleEnabled = true,
                ),
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun whenAppIsUnProtectedThenReturnViewStateWithtoggleUnCheckedEnabledAndShowCorrectBanner() = runTest {
        val date = "2020-10-21"
        val packageName = "com.duckduckgo.android"

        whenever(statsRepository.getTrackersForAppFromDate(date, packageName)).thenReturn(flowOf(emptyList()))
        whenever(appsRepository.getAppProtectionStatus(packageName)).thenReturn(UNPROTECTED)

        viewModel.loadData(date, packageName)

        viewModel.viewState().test {
            assertEquals(
                ViewState(
                    totalTrackingAttempts = 0,
                    lastTrackerBlockedAgo = "",
                    trackingCompanies = emptyList(),
                    toggleChecked = false,
                    bannerState = BannerState.SHOW_UNPROTECTED,
                    toggleEnabled = true,
                ),
                expectMostRecentItem(),
            )
        }
    }

    private fun getTrackersFlow(trackers: List<VpnTrackerWithEntity>): Flow<List<VpnTrackerWithEntity>> = flow {
        while (true) {
            emit(trackers)
        }
    }

    private fun someTrackers(): List<VpnTrackerWithEntity> {
        val defaultTrackingApp = TrackingApp("app.foo.com", "Foo App")
        val domain = "example.com"
        val trackerCompanyId: Int = -1
        val timestamp: String = DatabaseDateFormatter.bucketByHour()

        return listOf(
            VpnTrackerWithEntity(
                VpnTracker(
                    trackerCompanyId = trackerCompanyId,
                    domain = domain,
                    timestamp = timestamp,
                    company = "",
                    companyDisplayName = "",
                    trackingApp = defaultTrackingApp,
                ),
                AppTrackerEntity(trackerCompanyId, "Google", 100, listOf("unique_identifier")),
            ),
        )
    }
}
