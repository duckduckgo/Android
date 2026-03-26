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

package com.duckduckgo.mobile.android.vpn.ui.notification

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationFactory.DeviceShieldNotification
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DeviceShieldWeeklyNotificationFactoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var db: VpnDatabase
    private lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository

    private lateinit var factory: DeviceShieldNotificationFactory

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appTrackerBlockingStatsRepository = RealAppTrackerBlockingStatsRepository(db, coroutineTestRule.testDispatcherProvider)

        factory =
            DeviceShieldNotificationFactory(InstrumentationRegistry.getInstrumentation().targetContext.resources, appTrackerBlockingStatsRepository)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun createsWeeklyReportNotificationWhenTrackersFoundInOneApp() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        notification.assertTitleEquals("App Tracking Protection blocked 1 tracking attempt in Foo App (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsWeeklyReportNotificationWhenTrackersFoundInTwoApps() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(
            trackerDomain,
            appContainingTracker = trackingApp2(),
            timestamp = DatabaseDateFormatter.bucketByHour(LocalDateTime.now().plusHours(1)),
        )

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        notification.assertTitleEquals("App Tracking Protection blocked 2 tracking attempts in app2 and 1 other app (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsWeeklyReportNotificationWhenTrackersFoundInMoreThanTwoApps() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(
            trackerDomain,
            appContainingTracker = trackingApp3(),
            timestamp = DatabaseDateFormatter.bucketByHour(LocalDateTime.now().plusHours(1)),
        )

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        notification.assertTitleEquals("App Tracking Protection blocked 3 tracking attempts in app3 and 2 other apps (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsWeeklyReportNotificationWithCorrectSortOrder() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain, appContainingTracker = trackingApp1())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, appContainingTracker = trackingApp2())
        trackerFound(trackerDomain, appContainingTracker = trackingApp3())
        trackerFound(trackerDomain, appContainingTracker = trackingApp3())
        trackerFound(
            trackerDomain,
            appContainingTracker = trackingApp3(),
            timestamp = DatabaseDateFormatter.bucketByHour(LocalDateTime.now().plusHours(1)),
        )

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        notification.assertTitleEquals("App Tracking Protection blocked 6 tracking attempts in app3 and 2 other apps (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsHiddenWeeklyReportNotificationWhenNoTrackersFound() = runBlocking {
        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(0)
        assertTrue(notification.hidden)
    }

    @Test
    fun createsWeeklyTopTrackerCompanyNotificationWhenTrackersFoundInOneApp() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(1)
        notification.assertTitleEquals("App Tracking Protection blocked Tracking LLC in 1 app, Foo App (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsWeeklyTopTrackerCompanyNotificationWhenTrackersFoundInTwoApps() = runBlocking {
        val trackerDomain = "example.com"

        trackerFound("google.com", company = "Google", appContainingTracker = trackingApp1())
        trackerFound("google.com", company = "Google", appContainingTracker = trackingApp1())
        trackerFound("facebook.com", company = "Facebook", appContainingTracker = trackingApp2())
        trackerFound("google.com", company = "Google", appContainingTracker = trackingApp2())
        trackerFound("google.com", company = "Google", appContainingTracker = trackingApp2())
        trackerFound(
            "google.com",
            company = "Google",
            appContainingTracker = trackingApp2(),
            timestamp = DatabaseDateFormatter.bucketByHour(
                LocalDateTime.now().plusHours(2),
            ),
        )

        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(1)
        notification.assertTitleEquals("App Tracking Protection blocked Google across 2 apps, including app2 (past week).")
        assertFalse(notification.hidden)
    }

    @Test
    fun createsHiddenWeeklyTopTrackerCompanyNotificationWhenNoTrackersFound() = runBlocking {
        val notification = factory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(1)
        assertTrue(notification.hidden)
    }

    private fun trackerFound(
        domain: String = "example.com",
        trackerCompanyId: Int = -1,
        company: String = "Tracking LLC",
        appContainingTracker: TrackingApp = defaultApp(),
        timestamp: String = DatabaseDateFormatter.bucketByHour(),
    ) {
        val tracker = VpnTracker(
            trackerCompanyId = trackerCompanyId,
            domain = domain,
            timestamp = timestamp,
            company = company,
            companyDisplayName = company,
            trackingApp = appContainingTracker,
        )
        val trackers = listOf(tracker)
        appTrackerBlockingStatsRepository.insert(trackers)
        db.vpnAppTrackerBlockingDao().insertTrackerEntities(trackers.map { it.asEntity() })
    }

    private fun VpnTracker.asEntity(): AppTrackerEntity {
        return AppTrackerEntity(
            trackerCompanyId = this.trackerCompanyId,
            entityName = "name",
            score = 0,
            signals = emptyList(),
        )
    }

    private fun defaultApp() = TrackingApp("app.foo.com", "Foo App")
    private fun trackingApp1() = TrackingApp("package1", "app1")
    private fun trackingApp2() = TrackingApp("package2", "app2")
    private fun trackingApp3() = TrackingApp("package3", "app3")
}

private fun DeviceShieldNotification.assertTitleEquals(expected: String) {
    assertEquals("Given notification titles do not match", expected, this.text.toString())
}
