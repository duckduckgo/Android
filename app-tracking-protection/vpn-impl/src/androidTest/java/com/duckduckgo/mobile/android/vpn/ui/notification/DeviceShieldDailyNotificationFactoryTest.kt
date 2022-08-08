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
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.threeten.bp.LocalDateTime

class DeviceShieldDailyNotificationFactoryTest {

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
        appTrackerBlockingStatsRepository = RealAppTrackerBlockingStatsRepository(db)

        factory =
            DeviceShieldNotificationFactory(InstrumentationRegistry.getInstrumentation().targetContext.resources, appTrackerBlockingStatsRepository)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun createsTotalTrackersNotificationWhenTrackersFoundInOneApp() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(0)

        notification.assertTitleEquals("App Tracking Protection blocked 1 tracking attempt in 1 app, Foo App (past day).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsTotalTrackersNotificationWhenTrackersFoundInTwoApps() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())

        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(0)

        notification.assertTitleEquals("App Tracking Protection blocked 2 tracking attempts across 2 apps, including app2 (past day).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsHiddenTotalTrackersNotificationWhenNoTrackersFound() = runBlocking {
        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(0)
        assertTrue(notification.hidden)
    }

    @Test
    fun createsTopTrackerCompanyNotificationWhenTrackersFoundInOneApp() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(1)

        notification.assertTitleEquals("Tracking LLC tried to track you in 1 app (past day). See More")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsTopTrackerCompanyNotificationWhenTrackersFoundInTwoApps() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = TrackingApp("foo", "An app"))
        trackerFound(trackerDomain, appContainingTracker = TrackingApp("bar", "Another app"))

        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(1)

        notification.assertTitleEquals("Tracking LLC tried to track you across 2 apps (past day). See More")
        assertFalse(notification.hidden)
    }

    @Test
    fun doesNotCreateTopTrackerCompanyNotificationWhenTrackersFoundInZeroApps() = runBlocking {
        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(1)
        assertTrue(notification.hidden)
    }

    @Test
    fun createsHiddenTopTrackerCompanyNotificationWhenNoTrackersFound() = runBlocking {
        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(1)

        assertTrue(notification.hidden)
    }

    @Test
    fun createsTopTrackerAppNumbersNotificationWhenTrackersFound() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, appContainingTracker = trackingApp3())
        trackerFound(trackerDomain, appContainingTracker = trackingApp3())

        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(2)
        notification.assertTitleEquals("App Tracking Protection blocked the most tracking attempts in app2 and app3 (past day).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsHiddenTopTrackerCompanyNumbersNotificationWhenNoTrackersFound() = runBlocking {
        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(2)

        assertTrue(notification.hidden)
    }

    @Test
    fun createsLastCompanyAttemptNotificationWhenTrackersFoundInOneApp() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(3)

        notification.assertTitleEquals("App Tracking Protection blocked Tracking LLC 1 time in ${defaultApp().appDisplayName} (past day).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsLastCompanyAttemptNotificationWhenTrackersFoundInTwoApps() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())

        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(3)

        notification.assertTitleEquals("App Tracking Protection blocked Tracking LLC 2 times in app2 and 1 other app (past day).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsLastCompanyAttemptNotificationWhenTrackersFoundInThreeApps() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, trackerCompanyId = 1, company = "Google", appContainingTracker = trackingApp1())
        trackerFound(
            trackerDomain,
            trackerCompanyId = 1,
            company = "Google",
            appContainingTracker = trackingApp2(),
            timestamp = DatabaseDateFormatter.bucketByHour(LocalDateTime.now().plusHours(3))
        )
        trackerFound(
            trackerDomain,
            trackerCompanyId = 1,
            company = "Google",
            appContainingTracker = trackingApp3(),
            timestamp = DatabaseDateFormatter.bucketByHour(LocalDateTime.now().plusHours(4))
        )

        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(3)

        notification.assertTitleEquals("App Tracking Protection blocked Google 3 times in app3 and 2 other apps (past day).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsHiddenLastCompanyAttemptNotificationWhenNoTrackersFound() = runBlocking {
        val notification = factory.dailyNotificationFactory.createDailyDeviceShieldNotification(3)
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
            companyDisplayName = company,
            company = company,
            trackingApp = appContainingTracker
        )
        vpnTrackerDao.insert(tracker)
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
            companyDisplayName = trackerCompanyName,
            company = trackerCompanyName,
            trackingApp = appContainingTracker
        )
    }

    private fun defaultApp() = TrackingApp("app.foo.com", "Foo App")
    private fun trackingApp1() = TrackingApp("package1", "app1")
    private fun trackingApp2() = TrackingApp("package2", "app2")
    private fun trackingApp3() = TrackingApp("package3", "app3")
}

private fun DeviceShieldNotificationFactory.DeviceShieldNotification.assertTitleEquals(expected: String) {
    assertEquals("Given notification titles do not match", expected, this.title.toString())
}
