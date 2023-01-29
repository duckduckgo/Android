/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service.notification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.FakeVpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AppTpEnabledNotificationContentPluginTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository
    private lateinit var db: VpnDatabase
    private lateinit var vpnTrackerDao: VpnTrackerDao

    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private lateinit var plugin: AppTpEnabledNotificationContentPlugin

    @Before
    fun setup() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vpnTrackerDao = db.vpnTrackerDao()
        appTrackerBlockingStatsRepository = RealAppTrackerBlockingStatsRepository(db, coroutineTestRule.testDispatcherProvider)

        vpnFeaturesRegistry = FakeVpnFeaturesRegistry().apply {
            registerFeature(AppTpVpnFeature.APPTP_VPN)
        }

        plugin = AppTpEnabledNotificationContentPlugin(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            resources,
            appTrackerBlockingStatsRepository,
            vpnFeaturesRegistry,
        ) { null }
    }

    @After
    fun after() {
        kotlin.runCatching { db.close() }
    }

    @Test
    fun getInitialContentThenReturnsCorrectNotificationContent() {
        val content = plugin.getInitialContent()

        content!!.assertTitleEquals("App Tracking Protection is enabled and blocking tracking attempts across your apps")
        content.assertMessageEquals("")
        assertNull(content.notificationAction)
    }

    @Test
    fun getInitialContentAppTpNotEnabledThenReturnsCorrectNotificationContent() {
        vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)
        assertNull(plugin.getInitialContent())
    }

    @Test
    fun getUpdateContentThenReturnsCorrectInitialUpdatedNotificationContent() = runTest {
        plugin.getUpdatedContent().test {
            val item = awaitItem()

            item.assertTitleEquals("Scanning for tracking activity… beep… boop")
            item.assertMessageEquals("")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentAppTpNotEnabledThenReturnsCorrectInitialUpdatedNotificationContent() = runTest {
        vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)

        plugin.getUpdatedContent().test {
            val item = awaitItem()

            item.assertTitleEquals("")
            item.assertMessageEquals("")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentOneCompanyThenReturnsCorrectUpdatedNotificationContent() = runTest {
        plugin.getUpdatedContent().test {
            vpnTrackerDao.insert(aTrackerAndCompany())

            skipItems(1)
            val item = awaitItem()

            item.assertTitleEquals("Tracking attempts blocked in 1 app (past hour).")
            item.assertMessageEquals("")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentOneCompanyAppTpNotEnabledThenReturnsCorrectUpdatedNotificationContent() = runTest {
        vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)

        plugin.getUpdatedContent().test {
            vpnTrackerDao.insert(aTrackerAndCompany())

            skipItems(1)
            val item = awaitItem()

            item.assertTitleEquals("")
            item.assertMessageEquals("")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentMultipleDifferentAppsThenReturnsCorrectUpdatedNotificationContent() = runTest {
        plugin.getUpdatedContent().test {
            vpnTrackerDao.insert(
                aTrackerAndCompany(
                    appContainingTracker = trackingApp2(),
                ),
            )
            vpnTrackerDao.insert(
                aTrackerAndCompany(
                    appContainingTracker = trackingApp1(),
                ),
            )

            val item = expectMostRecentItem()

            item.assertTitleEquals("Tracking attempts blocked across 2 apps (past hour).")
            item.assertMessageEquals("")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentMultipleDifferentAppsAppTpNotEnabledThenReturnsCorrectUpdatedNotificationContent() = runTest {
        vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)

        plugin.getUpdatedContent().test {
            vpnTrackerDao.insert(
                aTrackerAndCompany(
                    appContainingTracker = trackingApp2(),
                ),
            )
            vpnTrackerDao.insert(
                aTrackerAndCompany(
                    appContainingTracker = trackingApp1(),
                ),
            )

            skipItems(1)
            val item = awaitItem()

            item.assertTitleEquals("")
            item.assertMessageEquals("")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentMultipleSameThenReturnsCorrectUpdatedNotificationContent() = runTest {
        plugin.getUpdatedContent().test {
            vpnTrackerDao.insert(aTrackerAndCompany())
            vpnTrackerDao.insert(aTrackerAndCompany())

            val item = expectMostRecentItem()

            item.assertTitleEquals("Tracking attempts blocked in 1 app (past hour).")
            item.assertMessageEquals("")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentMultipleSameAppTpNotEnabledThenReturnsCorrectUpdatedNotificationContent() = runTest {
        vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)

        plugin.getUpdatedContent().test {
            vpnTrackerDao.insert(aTrackerAndCompany())
            vpnTrackerDao.insert(aTrackerAndCompany())

            val item = expectMostRecentItem()

            item.assertTitleEquals("")
            item.assertMessageEquals("")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun isActiveWhenAppTpEnabledThenReturnsTrue() {
        assertTrue(plugin.isActive())
    }

    @Test
    fun isActiveWhenAppTpNotEnabledThenReturnsFalse() {
        vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)
        assertFalse(plugin.isActive())
    }

    private fun aTrackerAndCompany(
        domain: String = "example.com",
        trackerCompanyName: String = "Tracking LLC",
        trackerCompanyId: Int = -1,
        appContainingTracker: TrackingApp = TrackingApp("app.foo.com", "Foo App"),
        timestamp: String = DatabaseDateFormatter.bucketByHour(),
    ): VpnTracker {
        return VpnTracker(
            trackerCompanyId = trackerCompanyId,
            domain = domain,
            timestamp = timestamp,
            company = trackerCompanyName,
            companyDisplayName = trackerCompanyName,
            trackingApp = appContainingTracker,
        )
    }

    private fun trackingApp1() = TrackingApp("package1", "app1")
    private fun trackingApp2() = TrackingApp("package2", "app2")
}

private fun VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent.assertTitleEquals(expected: String) {
    assertEquals(expected, this.title.toString())
}

private fun VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent.assertMessageEquals(expected: String) {
    assertEquals(expected, this.message.toString())
}
