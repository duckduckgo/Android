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

package com.duckduckgo.mobile.android.vpn.ui.notification

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeviceShieldNotificationFactoryTest {

    private lateinit var db: VpnDatabase
    private lateinit var vpnTrackerDao: VpnTrackerDao
    private lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository

    private lateinit var factory: DeviceShieldNotificationFactory

    @Before
    fun before() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vpnTrackerDao = db.vpnTrackerDao()
        appTrackerBlockingStatsRepository = AppTrackerBlockingStatsRepository(db)

        factory =
            DeviceShieldNotificationFactory(InstrumentationRegistry.getInstrumentation().targetContext.resources, appTrackerBlockingStatsRepository)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun createsDeviceShieldEnabledNotification() {
        val notification = factory.createNotificationDeviceShieldEnabled()
        notification.assertTitleEquals("App Tracking Protection is enabled and blocking tracking attempts across your apps")
        assertFalse(notification.hidden)
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenTrackersFoundInOneApp() {
        val trackers = listOf(aTrackerAndCompany())
        val notification = factory.createNotificationNewTrackerFound(trackers)

        notification.assertTitleEquals("Tracking attempts blocked in 1 app (past hour).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenTrackersFoundInTwoApps() {
        val trackers = listOf(
            aTrackerAndCompany(appContainingTracker = trackingApp1()),
            aTrackerAndCompany(appContainingTracker = trackingApp2())
        )
        val notification = factory.createNotificationNewTrackerFound(trackers)

        notification.assertTitleEquals("Tracking attempts blocked across 2 apps (past hour).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenMultipleTrackersFoundInSameAppAppIsNotCountedTwice() {
        val trackers = listOf(
            aTrackerAndCompany(appContainingTracker = trackingApp1()),
            aTrackerAndCompany(appContainingTracker = trackingApp1()),
            aTrackerAndCompany(appContainingTracker = trackingApp1()),
            aTrackerAndCompany(appContainingTracker = trackingApp2())
        )
        val notification = factory.createNotificationNewTrackerFound(trackers)
        notification.assertTitleEquals("Tracking attempts blocked across 2 apps (past hour).")
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenNoTrackersFound() {
        val notification = factory.createNotificationNewTrackerFound(emptyList())
        notification.assertTitleEquals("Scanning for tracking activity… beep… boop")
    }

    private fun aTrackerAndCompany(
        domain: String = "example.com",
        trackerCompanyName: String = "Tracking LLC",
        trackerCompanyId: Int = -1,
        appContainingTracker: TrackingApp = defaultApp(),
        timestamp: String = DatabaseDateFormatter.bucketByHour()
    ): VpnTracker {
        return VpnTracker(
            trackerCompanyId = trackerCompanyId,
            domain = domain,
            timestamp = timestamp,
            company = trackerCompanyName,
            companyDisplayName = trackerCompanyName,
            trackingApp = appContainingTracker
        )
    }

    private fun defaultApp() = TrackingApp("app.foo.com", "Foo App")
    private fun trackingApp1() = TrackingApp("package1", "app1")
    private fun trackingApp2() = TrackingApp("package2", "app2")
}

private fun DeviceShieldNotificationFactory.DeviceShieldNotification.assertTitleEquals(expected: String) {
    assertEquals("Given notification titles do not match", expected, this.title.toString())
}
