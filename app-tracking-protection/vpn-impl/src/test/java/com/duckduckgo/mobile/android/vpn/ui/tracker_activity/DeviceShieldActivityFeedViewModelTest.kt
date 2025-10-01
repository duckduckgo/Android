/*
 * Copyright (c) 2022 DuckDuckGo
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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.common.utils.formatters.time.RealTimeDiffFormatter
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.exclusion.AppCategory
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.ERROR
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.UNKNOWN
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.TimeWindow
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldActivityFeedViewModel.TrackerFeedViewState
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.AppsData
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.AppsProtectionData
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerCompanyBadge
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem.TrackerLoadingSkeleton
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit.DAYS
import kotlin.time.ExperimentalTime

@ExperimentalTime
@RunWith(AndroidJUnit4::class)
class DeviceShieldActivityFeedViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var db: VpnDatabase
    private lateinit var viewModel: DeviceShieldActivityFeedViewModel
    private lateinit var repository: AppTrackerBlockingStatsRepository

    private val mockExcludedApps: TrackingProtectionAppsRepository = mock()
    private val mockVpnStateMonitor: VpnStateMonitor = mock()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = RealAppTrackerBlockingStatsRepository(db, coroutineTestRule.testDispatcherProvider)
        viewModel = DeviceShieldActivityFeedViewModel(
            repository,
            CoroutineTestRule().testDispatcherProvider,
            RealTimeDiffFormatter(InstrumentationRegistry.getInstrumentation().targetContext),
            mockExcludedApps,
            mockVpnStateMonitor,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenGetMostRecentTrackersCalledStartWithSkeleton() = runBlocking {
        viewModel.getMostRecentTrackers(timeWindow, config).test {
            assertEquals(TrackerFeedViewState(listOf(TrackerLoadingSkeleton), VpnState(DISABLED, ERROR)), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetMostRecentTrackersIsNotEmptyThenStartWithSkeletonThenEmit() = runBlocking {
        repository.insert(listOf(dummyTrackers[0], dummyTrackers[1], dummyTrackers[2]))
        db.vpnAppTrackerBlockingDao().insertTrackerEntities(dummySignals)

        mockVpnEnabled()
        getAppsAndProtectionInfoReturnsEmptyList()

        viewModel.getMostRecentTrackers(timeWindow, config).test {
            assertEquals(TrackerFeedViewState(listOf(TrackerLoadingSkeleton), VpnState(DISABLED, ERROR)), awaitItem())
            assertEquals(
                TrackerFeedViewState(
                    listOf(
                        trackerFeedDataWithTwoTrackers,
                        trackerFeedDataWithOneTracker,
                    ),
                    vpnState = VpnState(state = ENABLED, stopReason = UNKNOWN),
                ),
                awaitItem(),
            )
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetMostRecentTrackersIsNotEmptyAndOutsideTimeWindowThenEmitLoadingSkeleton() = runBlocking {
        repository.insert(listOf(dummyTrackers[3]))

        viewModel.getMostRecentTrackers(timeWindow, config).test {
            assertEquals(
                TrackerFeedViewState(
                    listOf(TrackerLoadingSkeleton),
                    vpnState = VpnState(state = DISABLED, stopReason = ERROR),
                ),
                awaitItem(),
            )
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetMostRecentTrackersIsEmptyAndVpnEnabledAndBoundedConfigThenEmitDescriptionAndAppsProtectionState() = runBlocking {
        mockVpnEnabled()
        whenever(mockExcludedApps.getAppsAndProtectionInfo()).thenReturn(flowOf(listOf(app, excludedApp)))
        val boundedConfig = config.copy(maxRows = 5)

        viewModel.getMostRecentTrackers(timeWindow, boundedConfig).test {
            assertEquals(TrackerFeedViewState(listOf(TrackerLoadingSkeleton), VpnState(DISABLED, ERROR)), awaitItem())
            assertEquals(
                TrackerFeedViewState(
                    listOf(
                        TrackerFeedItem.TrackerDescriptionFeed,
                        TrackerFeedItem.TrackerTrackerAppsProtection(
                            appsData = AppsProtectionData(
                                protectedAppsData = AppsData(
                                    appsCount = 1,
                                    isProtected = true,
                                    packageNames = listOf(app.packageName),
                                ),
                                unprotectedAppsData = AppsData(
                                    appsCount = 1,
                                    isProtected = false,
                                    packageNames = listOf(excludedApp.packageName),
                                ),
                            ),
                        ),
                    ),
                    vpnState = VpnState(state = ENABLED, stopReason = UNKNOWN),
                ),
                awaitItem(),
            )
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetMostRecentTrackersIsEmptyAndVpnDisabledAndFiveRowsThenEmitDescription() = runBlocking {
        mockVpnDisabled()
        whenever(mockExcludedApps.getAppsAndProtectionInfo()).thenReturn(flowOf(listOf(app, excludedApp)))
        val boundedConfig = config.copy(maxRows = 5)

        viewModel.getMostRecentTrackers(timeWindow, boundedConfig).test {
            assertEquals(TrackerFeedViewState(listOf(TrackerLoadingSkeleton), VpnState(DISABLED, ERROR)), awaitItem())
            assertEquals(
                TrackerFeedViewState(
                    listOf(
                        TrackerFeedItem.TrackerDescriptionFeed,
                    ),
                    vpnState = VpnState(state = DISABLED, stopReason = UNKNOWN),
                ),
                awaitItem(),
            )
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetMostRecentTrackersReturnsLessTrackersThanMaxRowsAndVpnEnabledThenEmitOnlyTrackerFeedDataItems() = runBlocking {
        repository.insert(listOf(dummyTrackers[0], dummyTrackers[1], dummyTrackers[2]))

        db.vpnAppTrackerBlockingDao().insertTrackerEntities(dummySignals)

        mockVpnEnabled()
        whenever(mockExcludedApps.getAppsAndProtectionInfo()).thenReturn(flowOf(listOf(app, excludedApp)))
        val boundedConfig = config.copy(maxRows = 2)

        viewModel.getMostRecentTrackers(timeWindow, boundedConfig).test {
            assertEquals(TrackerFeedViewState(listOf(TrackerLoadingSkeleton), VpnState(DISABLED, ERROR)), awaitItem())
            assertEquals(
                TrackerFeedViewState(
                    listOf(
                        trackerFeedDataWithTwoTrackers,
                        trackerFeedDataWithOneTracker,
                    ),
                    vpnState = VpnState(state = ENABLED, stopReason = UNKNOWN),
                ),
                awaitItem(),
            )
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    private suspend fun getAppsAndProtectionInfoReturnsEmptyList() {
        whenever(mockExcludedApps.getAppsAndProtectionInfo()).thenReturn(flowOf(emptyList()))
    }

    private fun mockVpnEnabled() {
        whenever(mockVpnStateMonitor.getStateFlow(AppTpVpnFeature.APPTP_VPN)).thenReturn(flowOf(VpnState(ENABLED, UNKNOWN)))
    }

    private fun mockVpnDisabled() {
        whenever(mockVpnStateMonitor.getStateFlow(AppTpVpnFeature.APPTP_VPN)).thenReturn(flowOf(VpnState(DISABLED, UNKNOWN)))
    }

    companion object {
        private val timeWindow = TimeWindow(
            config.timeWindow.toLong(),
            config.timeWindowUnits,
        )
    }
}

private fun VpnTracker.id(): Int {
    return timestamp.substringBefore("T").hashCode() + trackingApp.packageId.hashCode()
}

private fun VpnTracker.bucket(): String {
    return timestamp.substringBefore("T")
}

private val TEST_TIMESTAMP = DatabaseDateFormatter.timestamp()
private val TEST_TIMESTAMP_IN_THE_PAST = "2021-01-01T10:00:00"

private val dummySignals = listOf(
    AppTrackerEntity(
        0,
        "Google",
        100,
        emptyList(),
    ),
    AppTrackerEntity(
        1,
        "Segment",
        100,
        emptyList(),
    ),
    AppTrackerEntity(
        2,
        "Facebook",
        100,
        emptyList(),
    ),
)

private val dummyTrackers = listOf(
    VpnTracker(
        timestamp = TEST_TIMESTAMP,
        trackerCompanyId = 0,
        domain = "www.facebook.com",
        company = "Facebook, Inc.",
        companyDisplayName = "Facebook",
        trackingApp = TrackingApp(
            packageId = "foo.package.id",
            appDisplayName = "Foo",
        ),
    ),
    VpnTracker(
        timestamp = TEST_TIMESTAMP,
        trackerCompanyId = 1,
        domain = "api.segment.io",
        company = "Segment.io",
        companyDisplayName = "Segment",
        trackingApp = TrackingApp(
            packageId = "foo.package.id",
            appDisplayName = "Foo",
        ),
    ),
    VpnTracker(
        timestamp = TEST_TIMESTAMP,
        trackerCompanyId = 2,
        domain = "crashlyticsreports-pa.googleapis.com",
        company = "Google LLC",
        companyDisplayName = "Google",
        trackingApp = TrackingApp(
            packageId = "lion.package.id",
            appDisplayName = "LION",
        ),
    ),
    VpnTracker(
        timestamp = TEST_TIMESTAMP_IN_THE_PAST,
        trackerCompanyId = 0,
        domain = "doubleclick.net",
        company = "Google LLC",
        companyDisplayName = "Google",
        trackingApp = TrackingApp(
            packageId = "foo.package.id",
            appDisplayName = "Foo",
        ),
    ),
    VpnTracker(
        timestamp = TEST_TIMESTAMP,
        trackerCompanyId = 0,
        domain = "doubleclick.net",
        company = "Google LLC",
        companyDisplayName = "Google",
        trackingApp = TrackingApp(
            packageId = "com.duckduckgo.mobile.android.vpn",
            appDisplayName = "DuckDuckGo",
        ),
    ),
)

private val trackerFeedDataWithTwoTrackers = TrackerFeedItem.TrackerFeedData(
    id = dummyTrackers[0].id(),
    bucket = dummyTrackers[0].bucket(),
    trackingApp = dummyTrackers[0].trackingApp,
    trackingCompanyBadges = listOf(
        TrackerCompanyBadge.Company(dummyTrackers[0].company, dummyTrackers[0].companyDisplayName),
        TrackerCompanyBadge.Company(dummyTrackers[1].company, dummyTrackers[1].companyDisplayName),
    ),
    timestamp = TEST_TIMESTAMP,
    displayTimestamp = "Just Now",
    trackersTotalCount = 2,
)

private val trackerFeedDataWithOneTracker = TrackerFeedItem.TrackerFeedData(
    id = dummyTrackers[2].id(),
    bucket = dummyTrackers[2].bucket(),
    trackingApp = dummyTrackers[2].trackingApp,
    trackingCompanyBadges = listOf(
        TrackerCompanyBadge.Company(dummyTrackers[2].company, dummyTrackers[2].companyDisplayName),
    ),
    timestamp = TEST_TIMESTAMP,
    displayTimestamp = "Just Now",
    trackersTotalCount = 1,
)

private val config = DeviceShieldActivityFeedFragment.ActivityFeedConfig(
    maxRows = Int.MAX_VALUE,
    timeWindow = 1,
    timeWindowUnits = DAYS,
    showTimeWindowHeadings = false,
)

private val app = TrackingProtectionAppInfo(
    packageName = "com.package.name",
    name = "App",
    category = AppCategory.Undefined,
    isExcluded = false,
    knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
    userModified = false,
)

private val excludedApp = TrackingProtectionAppInfo(
    packageName = "com.package.name",
    name = "App",
    category = AppCategory.Undefined,
    isExcluded = true,
    knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
    userModified = false,
)
