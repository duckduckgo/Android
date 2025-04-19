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

import android.app.PendingIntent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.NotificationActions
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppTPAndNetPEnabledNotificationContentPluginTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository
    private lateinit var db: VpnDatabase

    @Mock
    private lateinit var appTrackingProtection: AppTrackingProtection

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    private val intentProvider = object : AppTpEnabledNotificationContentPlugin.IntentProvider {
        override fun getOnPressNotificationIntent(): PendingIntent? = null

        override fun getDeleteNotificationIntent(): PendingIntent? = null
    }

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private lateinit var plugin: AppTPAndNetPEnabledNotificationContentPlugin

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appTrackerBlockingStatsRepository = RealAppTrackerBlockingStatsRepository(db, coroutineTestRule.testDispatcherProvider)

        plugin = AppTPAndNetPEnabledNotificationContentPlugin(
            resources,
            appTrackerBlockingStatsRepository,
            appTrackingProtection,
            networkProtectionState,
            intentProvider,
        )
    }

    @After
    fun after() {
        kotlin.runCatching { db.close() }
    }

    @Test
    fun getInitialContentNoServerLocationThenReturnsCorrectNotificationContent() = runTest {
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.serverLocation()).thenReturn(null)

        val content = plugin.getInitialContent()

        content!!.assertTextEquals("Device traffic routing through the VPN. No tracking attempts blocked in apps (past hour).")
        assertEquals(NotificationActions.VPNActions, content.notificationActions)
    }

    @Test
    fun getInitialContentWithServerLocationThenReturnsCorrectNotificationContent() = runTest {
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.serverLocation()).thenReturn("Stockholm, SE")

        val content = plugin.getInitialContent()

        content!!.assertTextEquals("Device traffic routing through Stockholm, SE. No tracking attempts blocked in apps (past hour).")
        assertEquals(NotificationActions.VPNActions, content.notificationActions)
    }

    @Test
    fun getInitialContentAppTpNotEnabledThenReturnsCorrectNotificationContent() = runTest {
        whenever(appTrackingProtection.isEnabled()).thenReturn(false)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        assertNull(plugin.getInitialContent())
    }

    @Test
    fun getInitialContentNetPNotEnabledThenReturnsCorrectNotificationContent() = runTest {
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        assertNull(plugin.getInitialContent())
    }

    @Test
    fun getUpdateContentThenReturnsCorrectInitialUpdatedNotificationContent() = runTest {
        whenever(networkProtectionState.serverLocation()).thenReturn("Stockholm, SE")
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        plugin.getUpdatedContent().test {
            val item = awaitItem()

            item.assertTextEquals("Device traffic routing through Stockholm, SE. No tracking attempts blocked in apps (past hour).")
            assertTrue(item.notificationActions is NotificationActions.VPNActions)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentOneCompanyThenReturnsCorrectUpdatedNotificationContent() = runTest {
        whenever(networkProtectionState.serverLocation()).thenReturn("Stockholm, SE")
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        plugin.getUpdatedContent().test {
            val trackers = listOf(aTrackerAndCompany())
            appTrackerBlockingStatsRepository.insert(trackers)
            db.vpnAppTrackerBlockingDao().insertTrackerEntities(trackers.map { it.asEntity() })

            skipItems(1)
            val item = awaitItem()

            item.assertTextEquals("Device traffic routing through Stockholm, SE. Tracking attempts blocked in 1 app (past hour).")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentMultipleDifferentAppsThenReturnsCorrectUpdatedNotificationContent() = runTest {
        whenever(networkProtectionState.serverLocation()).thenReturn("Stockholm, SE")
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        plugin.getUpdatedContent().test {
            val trackers = listOf(
                aTrackerAndCompany(
                    appContainingTracker = trackingApp2(),
                ),
                aTrackerAndCompany(
                    appContainingTracker = trackingApp1(),
                ),
            )
            appTrackerBlockingStatsRepository.insert(trackers)
            db.vpnAppTrackerBlockingDao().insertTrackerEntities(trackers.map { it.asEntity() })

            val item = expectMostRecentItem()

            item.assertTextEquals("Device traffic routing through Stockholm, SE. Tracking attempts blocked across 2 apps (past hour).")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentMultipleDifferentAppsNoLocationThenReturnsCorrectUpdatedNotificationContent() = runTest {
        whenever(networkProtectionState.serverLocation()).thenReturn(null)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        plugin.getUpdatedContent().test {
            val trackers = listOf(
                aTrackerAndCompany(
                    appContainingTracker = trackingApp2(),
                ),
                aTrackerAndCompany(
                    appContainingTracker = trackingApp1(),
                ),
            )
            appTrackerBlockingStatsRepository.insert(trackers)
            db.vpnAppTrackerBlockingDao().insertTrackerEntities(trackers.map { it.asEntity() })

            val item = expectMostRecentItem()

            item.assertTextEquals("Device traffic routing through the VPN. Tracking attempts blocked across 2 apps (past hour).")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentTrackersWithoutEntityThenReturnsCorrectUpdatedNotificationContent() = runTest {
        whenever(networkProtectionState.serverLocation()).thenReturn("Stockholm, SE")
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        plugin.getUpdatedContent().test {
            appTrackerBlockingStatsRepository.insert(listOf(aTrackerAndCompany(), aTrackerAndCompany()))

            val item = expectMostRecentItem()

            item.assertTextEquals("Device traffic routing through Stockholm, SE. No tracking attempts blocked in apps (past hour).")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentTrackersWithoutEntityNoLocationThenReturnsCorrectUpdatedNotificationContent() = runTest {
        whenever(networkProtectionState.serverLocation()).thenReturn(null)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        plugin.getUpdatedContent().test {
            appTrackerBlockingStatsRepository.insert(listOf(aTrackerAndCompany(), aTrackerAndCompany()))

            val item = expectMostRecentItem()

            item.assertTextEquals("Device traffic routing through the VPN. No tracking attempts blocked in apps (past hour).")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentMultipleSameThenReturnsCorrectUpdatedNotificationContent() = runTest {
        whenever(networkProtectionState.serverLocation()).thenReturn("Stockholm, SE")
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        plugin.getUpdatedContent().test {
            appTrackerBlockingStatsRepository.insert(listOf(aTrackerAndCompany(), aTrackerAndCompany()))
            db.vpnAppTrackerBlockingDao().insertTrackerEntities(
                listOf(aTrackerAndCompany().asEntity()),
            )

            val item = expectMostRecentItem()

            item.assertTextEquals("Device traffic routing through Stockholm, SE. Tracking attempts blocked in 1 app (past hour).")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getUpdateContentMultipleSameNoLocationThenReturnsCorrectUpdatedNotificationContent() = runTest {
        whenever(networkProtectionState.serverLocation()).thenReturn(null)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        plugin.getUpdatedContent().test {
            appTrackerBlockingStatsRepository.insert(listOf(aTrackerAndCompany(), aTrackerAndCompany()))
            db.vpnAppTrackerBlockingDao().insertTrackerEntities(
                listOf(aTrackerAndCompany().asEntity()),
            )

            val item = expectMostRecentItem()

            item.assertTextEquals("Device traffic routing through the VPN. Tracking attempts blocked in 1 app (past hour).")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun isActiveWhenAppTpAndNetPEnabledThenReturnsTrue() = runTest {
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        assertTrue(plugin.isActive())
    }

    @Test
    fun isActiveWhenAppTpAndNetPNotEnabledThenReturnsFalse() = runTest {
        whenever(appTrackingProtection.isEnabled()).thenReturn(false)
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        assertFalse(plugin.isActive())
    }

    @Test
    fun isActiveWhenAppTpOnlyEnabledThenReturnsFalse() = runTest {
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        assertFalse(plugin.isActive())
    }

    @Test
    fun isActiveWhenNetPOnlyEnabledThenReturnsFalse() = runTest {
        whenever(appTrackingProtection.isEnabled()).thenReturn(false)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

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
    private fun VpnTracker.asEntity(): AppTrackerEntity {
        return AppTrackerEntity(
            trackerCompanyId = this.trackerCompanyId,
            entityName = "name",
            score = 0,
            signals = emptyList(),
        )
    }
}
