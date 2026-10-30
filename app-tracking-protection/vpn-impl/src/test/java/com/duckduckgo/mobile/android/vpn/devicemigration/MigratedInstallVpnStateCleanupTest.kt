/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.devicemigration

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.mobile.android.vpn.dao.VpnFeatureRemoverState
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState
import com.duckduckgo.mobile.android.vpn.model.VpnServiceStateStats
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerManualExcludedApp
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MigratedInstallVpnStateCleanupTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var db: VpnDatabase
    private lateinit var repository: AppTrackerBlockingStatsRepository
    private lateinit var testee: MigratedInstallVpnStateCleanup

    private val deviceMigration: DeviceMigration = mock()
    private val preferences = InMemorySharedPreferences()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn preferences
    }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RealAppTrackerBlockingStatsRepository(db, coroutineTestRule.testDispatcherProvider)
        testee = MigratedInstallVpnStateCleanup(
            deviceMigration = deviceMigration,
            vpnDatabase = db,
            appTrackerBlockingStatsRepository = repository,
            sharedPreferencesProvider = sharedPreferencesProvider,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )
        givenPreviousPhoneState()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenMigratedInstallThenPreviousPhoneRunningStateAndTrackerHistoryCleared() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        startApp()

        assertTrue(db.vpnHeartBeatDao().hearBeats().isEmpty())
        assertNull(db.vpnServiceStateDao().getLastStateStats())
        assertTrue(repository.getTrackersForApp(TRACKING_APP.packageId).isEmpty())
    }

    @Test
    fun whenMigratedInstallThenUserChoicesAndRemoteListsKept() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        startApp()

        assertEquals(listOf(MANUALLY_EXCLUDED_APP), db.vpnAppTrackerBlockingDao().getManualAppExclusionList())
        assertTrue(db.vpnAppTrackerBlockingDao().hasTrackerEntities())
        assertEquals(true, db.vpnFeatureRemoverDao().getState()?.isFeatureRemoved)
    }

    @Test
    fun whenNotMigratedInstallThenNothingCleared() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(false)

        startApp()

        assertEquals(1, db.vpnHeartBeatDao().hearBeats().size)
        assertEquals(VpnServiceState.ENABLED, db.vpnServiceStateDao().getLastStateStats()?.state)
        assertEquals(1, repository.getTrackersForApp(TRACKING_APP.packageId).size)
    }

    @Test
    fun whenCleanupAlreadyDoneThenStateWrittenOnThisPhoneIsKept() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        startApp()

        db.vpnHeartBeatDao().insertType("ALIVE")
        startApp()
        testee.cleanUpIfNeeded()

        assertEquals(1, db.vpnHeartBeatDao().hearBeats().size)
        verify(deviceMigration, times(1)).isMigratedInstall()
    }

    @Test
    fun whenCleanedUpOnDemandBeforeAppStartThenPreviousPhoneStateAlreadyGone() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        testee.cleanUpIfNeeded()

        assertTrue(db.vpnHeartBeatDao().hearBeats().isEmpty())
        assertNull(db.vpnServiceStateDao().getLastStateStats())
    }

    @Test
    fun whenMigratedInstallCheckThrowsThenRetriedOnNextStart() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenAnswer { throw RuntimeException("boom") }.thenReturn(true)

        startApp()
        assertEquals(1, db.vpnHeartBeatDao().hearBeats().size)

        startApp()
        assertTrue(db.vpnHeartBeatDao().hearBeats().isEmpty())
    }

    private fun givenPreviousPhoneState() {
        db.vpnHeartBeatDao().insertType("ALIVE")
        db.vpnServiceStateDao().insert(VpnServiceStateStats(state = VpnServiceState.ENABLED))
        repository.insert(
            listOf(
                VpnTracker(
                    trackerCompanyId = TRACKER_COMPANY_ID,
                    domain = "tracker.example",
                    company = "Tracker Co",
                    companyDisplayName = "Tracker Co",
                    trackingApp = TRACKING_APP,
                ),
            ),
        )
        db.vpnAppTrackerBlockingDao().insertTrackerEntities(listOf(AppTrackerEntity(TRACKER_COMPANY_ID, "Tracker Co", 100, emptyList())))
        db.vpnAppTrackerBlockingDao().insertIntoManualAppExclusionList(MANUALLY_EXCLUDED_APP)
        db.vpnFeatureRemoverDao().insert(VpnFeatureRemoverState(isFeatureRemoved = true))
    }

    private fun startApp() {
        testee.onCreate(mock())
        coroutineTestRule.testScope.advanceUntilIdle()
    }

    private companion object {
        const val TRACKER_COMPANY_ID = 7
        val TRACKING_APP = TrackingApp("com.example.app", "Example")
        val MANUALLY_EXCLUDED_APP = AppTrackerManualExcludedApp("com.example.excluded", isProtected = false)
    }
}
