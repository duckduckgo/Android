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

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.content.edit
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import dummy.ui.VpnPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class DeviceShieldTrackerActivityViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: VpnDatabase
    private lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository
    private lateinit var viewModel: DeviceShieldTrackerActivityViewModel
    private lateinit var defaultTracker: VpnTracker
    private lateinit var vpnPreferences: VpnPreferences

    @Mock private lateinit var appBuildConfig: AppBuildConfig

    private val deviceShieldPixels = mock<DeviceShieldPixels>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        whenever(appBuildConfig.isDebug).thenReturn(true)

        db = createInMemoryDb()

        defaultTracker = VpnTracker(
            trackerCompanyId = 1,
            company = "Google LLC",
            companyDisplayName = "Google",
            trackingApp = TrackingApp("app.foo.com", "Foo app"),
            domain = "doubleclick.net"
        )

        context.getSharedPreferences(VpnPreferences.PREFS_FILENAME, Context.MODE_PRIVATE).edit { clear() }
        vpnPreferences = VpnPreferences(context, appBuildConfig)

        appTrackerBlockingStatsRepository = AppTrackerBlockingStatsRepository(db)
        viewModel = DeviceShieldTrackerActivityViewModel(
            InstrumentationRegistry.getInstrumentation().context,
            deviceShieldPixels,
            vpnPreferences,
            appTrackerBlockingStatsRepository,
            CoroutineTestRule().testDispatcherProvider
        )
    }

    @Test
    fun whenGetTrackingAppCountThenReturnTrackingCount() = runBlocking {
        val tracker = VpnTracker(
            trackerCompanyId = 1,
            company = "Google LLC",
            companyDisplayName = "Google",
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

        val count = viewModel.getTrackingAppsCount().take(1).toList()
        assertEquals(TrackingAppCount(2), count.first())
    }

    @Test
    fun whenGetTrackerCountThenReturnTrackingCount() = runBlocking {
        db.vpnTrackerDao().insert(defaultTracker)
        db.vpnTrackerDao().insert(defaultTracker)
        db.vpnTrackerDao().insert(defaultTracker.copy(domain = "facebook.com"))
        db.vpnTrackerDao().insert(defaultTracker.copy(trackingApp = TrackingApp("app.bar.com", "Bar app")))

        val count = viewModel.getBlockedTrackersCount().take(1).toList()
        assertEquals(TrackerCount(4), count.first())
    }

    @Test
    fun whenLaunchAppTrackersViewEventThenCommandIsLaunchAppTrackers() = runBlocking {
        viewModel.commands().test {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchAppTrackersFAQ)

            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchAppTrackersFAQ, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLaunchBetaInstructionsViewEventThenCommandIsLaunchBetaInstructions() = runBlocking {
        viewModel.commands().test {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchBetaInstructions)

            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchBetaInstructions, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLaunchDeviceShieldFAQViewEventThenCommandIsLaunchDeviceShieldFAQ() = runBlocking {
        viewModel.commands().test {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchDeviceShieldFAQ)

            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchDeviceShieldFAQ, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLaunchExcludedAppsViewEventThenCommandIsLaunchExcludedApps() = runBlocking {
        viewModel.commands().test {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchExcludedApps)

            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchExcludedApps(true), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLaunchMostRecentActivityViewEventThenCommandIsLaunchMostRecentActivity() = runBlocking {
        viewModel.commands().test {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchMostRecentActivity)

            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchMostRecentActivity, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenToggleIsSwitchedOnThenTrackingProtectionIsEnabled() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppTPToggleSwitched(true)

            verify(deviceShieldPixels).enableFromSummaryTrackerActivity()
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.StartDeviceShield, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenToggleIsSwitchedOffThenConfirmationDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppTPToggleSwitched(false)

            verifyNoInteractions(deviceShieldPixels)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.ShowDisableConfirmationDialog, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppTPIsManuallyDisabledThenTrackingProtectionIsStopped() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppTpManuallyDisabled()

            verify(deviceShieldPixels).disableFromSummaryTrackerActivity()
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.StopDeviceShield, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    private fun createInMemoryDb(): VpnDatabase {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
        return Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            VpnDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }
}
