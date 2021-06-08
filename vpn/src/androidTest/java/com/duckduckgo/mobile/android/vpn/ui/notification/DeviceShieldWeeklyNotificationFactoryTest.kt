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
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.threeten.bp.LocalDateTime

class DeviceShieldWeeklyNotificationFactoryTest {

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

        factory = DeviceShieldNotificationFactory(InstrumentationRegistry.getInstrumentation().targetContext.resources, appTrackerBlockingStatsRepository)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun createsWeeklyReportNotificationWhenTrackersFoundInOneApp() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        notification.assertTitleEquals("Device Shield blocked 1 tracker in Foo App (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsWeeklyReportNotificationWhenTrackersFoundInTwoApps() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        notification.assertTitleEquals("Device Shield blocked 2 trackers in app1 and 1 other app (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsWeeklyReportNotificationWhenTrackersFoundInMoreThanTwoApps() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, appContainingTracker = trackingApp3())

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        notification.assertTitleEquals("Device Shield blocked 3 trackers in app1 and 2 other apps (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsWeeklyReportNotificationWithCorrectSortOrder() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, appContainingTracker = trackingApp3())
        trackerFound(trackerDomain, appContainingTracker = trackingApp3())
        trackerFound(trackerDomain, appContainingTracker = trackingApp3())

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        notification.assertTitleEquals("Device Shield blocked 6 trackers in app3 and 2 other apps (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsHiddenWeeklyReportNotificationWhenNoTrackersFound() {
        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        assertTrue(notification.hidden)
    }

    @Test
    fun createsWeeklyTopTrackerCompanyNotificationWhenTrackersFoundInOneApp() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(1)
        notification.assertTitleEquals("Device Shield blocked Tracking LLC in 1 app, Foo App (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsWeeklyTopTrackerCompanyNotificationWhenTrackersFoundInTwoApps() {
        val trackerDomain = "example.com"

        trackerFound(trackerDomain, company = "Google", appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, company = "Google", appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, company = "Facebook", appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, company = "Google", appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, company = "Google", appContainingTracker = trackingApp2())
        trackerFound(
            trackerDomain, company = "Google", appContainingTracker = trackingApp2(),
            timestamp = DatabaseDateFormatter.bucketByHour(
                LocalDateTime.now().plusHours(2)
            )
        )

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(1)
        notification.assertTitleEquals("Device Shield blocked Google across 2 apps, including app2 (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsHiddenWeeklyTopTrackerCompanyNotificationWhenNoTrackersFound() {
        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(1)
        assertTrue(notification.hidden)
    }

    private fun trackerFound(
        domain: String = "example.com",
        trackerCompanyId: Int = -1,
        company: String = "Tracking LLC",
        appContainingTracker: TrackingApp = defaultApp(),
        timestamp: String = DatabaseDateFormatter.bucketByHour()
    ) {
        val tracker = VpnTracker(
            trackerCompanyId = trackerCompanyId,
            domain = domain,
            timestamp = timestamp,
            company = company,
            companyDisplayName = company,
            trackingApp = appContainingTracker
        )
        vpnTrackerDao.insert(tracker)
    }

    private fun defaultApp() = TrackingApp("app.foo.com", "Foo App")
    private fun trackingApp1() = TrackingApp("package1", "app1")
    private fun trackingApp2() = TrackingApp("package2", "app2")
    private fun trackingApp3() = TrackingApp("package3", "app3")

}

private fun DeviceShieldNotificationFactory.DeviceShieldNotification.assertTitleEquals(expected: String) {
    assertEquals("Given notification titles do not match", expected, this.title.toString())
}
