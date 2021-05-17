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

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.mobile.android.vpn.VpnCoroutineTestRule
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class PastWeekTrackerActivityViewModelTest {

    private lateinit var db: VpnDatabase
    private lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository
    private lateinit var pastWeekTrackerActivityViewModel: PastWeekTrackerActivityViewModel
    private lateinit var defaultTracker: VpnTracker

    @Before
    fun setup() {
        db = createInMemoryDb()

        defaultTracker = VpnTracker(
            trackerCompanyId = 1,
            company = "Google LLC",
            trackingApp = TrackingApp("app.foo.com", "Foo app"),
            domain = "doubleclick.net"
        )

        appTrackerBlockingStatsRepository = AppTrackerBlockingStatsRepository(db)
        pastWeekTrackerActivityViewModel = PastWeekTrackerActivityViewModel(
            appTrackerBlockingStatsRepository,
            VpnCoroutineTestRule().testDispatcherProvider
        )
    }

    @Test
    fun whenGetTrackingAppCountThenReturnTrackingCount() = runBlocking {
        val tracker = VpnTracker(
            trackerCompanyId = 1,
            company = "Google LLC",
            trackingApp = TrackingApp("app.foo.com", "Foo app"),
            domain = "doubleclick.net"
        )

        db.vpnTrackerDao().insert(defaultTracker)
        db.vpnTrackerDao().insert(
            defaultTracker.copy(
                trackingApp = TrackingApp("app.bar.com", "bar app")
            )
        )
        db.vpnTrackerDao().insert(tracker.copy(domain = "facebook.com"))

        val count = pastWeekTrackerActivityViewModel.getTrackingAppsCount().take(1).toList()
        assertEquals(TrackingAppCount(2), count.first())
    }

    @Test
    fun whenGetTrackerCountThenReturnTrackingCount() = runBlocking {
        db.vpnTrackerDao().insert(defaultTracker)
        db.vpnTrackerDao().insert(defaultTracker)
        db.vpnTrackerDao().insert(defaultTracker.copy(domain = "facebook.com"))
        db.vpnTrackerDao().insert(defaultTracker.copy(trackingApp = TrackingApp("app.bar.com", "Bar app")))

        val count = pastWeekTrackerActivityViewModel.getBlockedTrackersCount().take(1).toList()
        assertEquals(TrackerCount(4), count.first())

    }

    private fun createInMemoryDb(): VpnDatabase {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
        return Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
