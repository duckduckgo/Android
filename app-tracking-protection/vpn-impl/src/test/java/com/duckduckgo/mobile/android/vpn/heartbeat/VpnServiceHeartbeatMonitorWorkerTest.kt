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

package com.duckduckgo.mobile.android.vpn.heartbeat

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.mobile.android.vpn.devicemigration.MigratedInstallVpnStateCleanup
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class VpnServiceHeartbeatMonitorWorkerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceMigration: DeviceMigration = mock()
    private val deviceShieldPixels: DeviceShieldPixels = mock()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn InMemorySharedPreferences()
    }
    private lateinit var db: VpnDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, VpnDatabase::class.java).allowMainThreadQueries().build()
        db.vpnHeartBeatDao().insertType(VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_ALIVE)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenMigratedInstallCarriesPreviousPhoneAliveHeartbeatThenVpnNotRestarted() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        worker().doWork()

        verify(deviceShieldPixels, never()).suddenKillBySystem()
        verify(deviceShieldPixels, never()).automaticRestart()
    }

    @Test
    fun whenNotMigratedInstallWithAliveHeartbeatThenVpnRestarted() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(false)

        worker().doWork()

        verify(deviceShieldPixels).suddenKillBySystem()
        verify(deviceShieldPixels).automaticRestart()
    }

    private fun worker(): VpnServiceHeartbeatMonitorWorker =
        TestListenableWorkerBuilder<VpnServiceHeartbeatMonitorWorker>(context).build().also {
            it.vpnHeartBeatDao = db.vpnHeartBeatDao()
            it.vpnServiceStateStatsDao = db.vpnServiceStateDao()
            it.deviceShieldPixels = deviceShieldPixels
            it.dispatcherProvider = coroutineTestRule.testDispatcherProvider
            it.migratedInstallVpnStateCleanup = MigratedInstallVpnStateCleanup(
                deviceMigration = deviceMigration,
                vpnDatabase = db,
                appTrackerBlockingStatsRepository = RealAppTrackerBlockingStatsRepository(db, coroutineTestRule.testDispatcherProvider),
                sharedPreferencesProvider = sharedPreferencesProvider,
                dispatchers = coroutineTestRule.testDispatcherProvider,
                appCoroutineScope = coroutineTestRule.testScope,
            )
        }
}
