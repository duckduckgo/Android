// /*
//  * Copyright (c) 2021 DuckDuckGo
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */
//
// package com.duckduckgo.mobile.android.vpn.ui.tracker_activity
//
// import androidx.room.Room
// import androidx.test.platform.app.InstrumentationRegistry
// import app.cash.turbine.test
// import com.duckduckgo.app.CoroutineTestRule
// import com.duckduckgo.mobile.android.vpn.model.TrackingApp
// import com.duckduckgo.mobile.android.vpn.model.VpnTracker
// import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
// import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
// import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
// import com.duckduckgo.mobile.android.vpn.time.TimeDiffFormatter
// import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
// import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerCompanyBadge
// import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
// import com.jakewharton.threetenabp.AndroidThreeTen
// import kotlinx.coroutines.ExperimentalCoroutinesApi
// import kotlinx.coroutines.runBlocking
// import org.junit.Assert.assertEquals
// import org.junit.Before
// import org.junit.Test
// import java.util.concurrent.TimeUnit
// import kotlin.time.ExperimentalTime
//
// @ExperimentalTime
// @ExperimentalCoroutinesApi
// class DeviceShieldActivityFeedViewModelTest {
//
//     private lateinit var db: VpnDatabase
//     private lateinit var viewModel: DeviceShieldActivityFeedViewModel
//
//     @Before
//     fun setup() {
//         AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
//         db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
//             .allowMainThreadQueries()
//             .build()
//
//         viewModel = DeviceShieldActivityFeedViewModel(
//             AppTrackerBlockingStatsRepository(db),
//             CoroutineTestRule().testDispatcherProvider,
//             TimeDiffFormatter(InstrumentationRegistry.getInstrumentation().targetContext)
//         )
//     }
//
//     @Test
//     fun whenGetMostRecentTrackersCalledStartWithSkeleton() = runBlocking {
//         viewModel.getMostRecentTrackers(timeWindow, false).test {
//             assertEquals(listOf(TrackerFeedItem.TrackerLoadingSkeleton), awaitItem())
//             cancelAndConsumeRemainingEvents()
//         }
//     }
//
//     @Test
//     fun whenGetMostRecentTrackersIsEmptyThenEmitEmpty() = runBlocking {
//         viewModel.getMostRecentTrackers(timeWindow, false).test {
//             assertEquals(listOf(TrackerFeedItem.TrackerLoadingSkeleton), awaitItem())
//             assertEquals(listOf(TrackerFeedItem.TrackerEmptyFeed), awaitItem())
//             cancelAndConsumeRemainingEvents()
//         }
//     }
//
//     @Test
//     fun whenGetMostRecentTrackersIsNotEmptyThenStartWithSkeletonThenEmit() = runBlocking {
//         db.vpnTrackerDao().insert(dummyTrackers[2])
//         db.vpnTrackerDao().insert(dummyTrackers[1])
//         db.vpnTrackerDao().insert(dummyTrackers[0])
//         db.vpnAppTrackerBlockingDao().insertTrackerEntities(dummySignals)
//
//         viewModel.getMostRecentTrackers(timeWindow, false).test {
//             assertEquals(listOf(TrackerFeedItem.TrackerLoadingSkeleton), awaitItem())
//             assertEquals(
//                 listOf(
//                     TrackerFeedItem.TrackerFeedData(
//                         id = dummyTrackers[0].id(),
//                         bucket = dummyTrackers[0].bucket(),
//                         trackingApp = dummyTrackers[0].trackingApp,
//                         trackingCompanyBadges = listOf(
//                             TrackerCompanyBadge.Company(dummyTrackers[0].company, dummyTrackers[0].companyDisplayName),
//                             TrackerCompanyBadge.Company(dummyTrackers[1].company, dummyTrackers[1].companyDisplayName),
//                         ),
//                         timestamp = TEST_TIMESTAMP,
//                         displayTimestamp = "just now",
//                         trackersTotalCount = 2
//                     ),
//                     TrackerFeedItem.TrackerFeedData(
//                         id = dummyTrackers[2].id(),
//                         bucket = dummyTrackers[2].bucket(),
//                         trackingApp = dummyTrackers[2].trackingApp,
//                         trackingCompanyBadges = listOf(
//                             TrackerCompanyBadge.Company(dummyTrackers[2].company, dummyTrackers[2].companyDisplayName),
//                         ),
//                         timestamp = TEST_TIMESTAMP,
//                         displayTimestamp = "just now",
//                         trackersTotalCount = 1
//                     ),
//                 ),
//                 awaitItem()
//             )
//             expectNoEvents()
//             cancelAndConsumeRemainingEvents()
//         }
//     }
//
//     @Test
//     fun whenGetMostRecentTrackersIsNotEmptyAndOutsideTimeWindowThenEmitEmpty() = runBlocking {
//         db.vpnTrackerDao().insert(dummyTrackers[3])
//
//         // we always start with skeleton, that's why we expect two items
//         viewModel.getMostRecentTrackers(timeWindow, false).test {
//             assertEquals(listOf(TrackerFeedItem.TrackerLoadingSkeleton), awaitItem())
//             assertEquals(listOf(TrackerFeedItem.TrackerEmptyFeed), awaitItem())
//             expectNoEvents()
//             cancelAndConsumeRemainingEvents()
//         }
//     }
//
//     companion object {
//         private val timeWindow = DeviceShieldActivityFeedViewModel.TimeWindow(1, TimeUnit.DAYS)
//     }
// }
//
// private fun VpnTracker.id(): Int {
//     return timestamp.substringBefore("T").hashCode() + trackingApp.packageId.hashCode()
// }
//
// private fun VpnTracker.bucket(): String {
//     return timestamp.substringBefore("T")
// }
//
// private val TEST_TIMESTAMP = DatabaseDateFormatter.timestamp()
// private val TEST_TIMESTAMP_IN_THE_PAST = "2021-01-01T10:00:00"
//
// private val dummySignals = listOf(
//     AppTrackerEntity(
//         0,
//         "Google",
//         100,
//         emptyList()
//     ),
//     AppTrackerEntity(
//         1,
//         "Segment",
//         100,
//         emptyList()
//     ),
//     AppTrackerEntity(
//         2,
//         "Facebook",
//         100,
//         emptyList()
//     )
// )
//
// private val dummyTrackers = listOf(
//     VpnTracker(
//         timestamp = TEST_TIMESTAMP,
//         trackerCompanyId = 0,
//         domain = "www.facebook.com",
//         company = "Facebook, Inc.",
//         companyDisplayName = "Facebook",
//         trackingApp = TrackingApp(
//             packageId = "foo.package.id",
//             appDisplayName = "Foo"
//         )
//     ),
//     VpnTracker(
//         timestamp = TEST_TIMESTAMP,
//         trackerCompanyId = 1,
//         domain = "api.segment.io",
//         company = "Segment.io",
//         companyDisplayName = "Segment",
//         trackingApp = TrackingApp(
//             packageId = "foo.package.id",
//             appDisplayName = "Foo"
//         )
//     ),
//     VpnTracker(
//         timestamp = TEST_TIMESTAMP,
//         trackerCompanyId = 2,
//         domain = "crashlyticsreports-pa.googleapis.com",
//         company = "Google LLC",
//         companyDisplayName = "Google",
//         trackingApp = TrackingApp(
//             packageId = "lion.package.id",
//             appDisplayName = "LION"
//         )
//     ),
//     VpnTracker(
//         timestamp = TEST_TIMESTAMP_IN_THE_PAST,
//         trackerCompanyId = 0,
//         domain = "doubleclick.net",
//         company = "Google LLC",
//         companyDisplayName = "Google",
//         trackingApp = TrackingApp(
//             packageId = "foo.package.id",
//             appDisplayName = "Foo"
//         )
//     ),
//     VpnTracker(
//         timestamp = TEST_TIMESTAMP,
//         trackerCompanyId = 0,
//         domain = "doubleclick.net",
//         company = "Google LLC",
//         companyDisplayName = "Google",
//         trackingApp = TrackingApp(
//             packageId = "com.duckduckgo.mobile.android.vpn",
//             appDisplayName = "DuckDuckGo"
//         )
//     )
// )
