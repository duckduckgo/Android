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

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.network.VpnDetector
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.UNKNOWN
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivityViewModel.ViewEvent
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class DeviceShieldTrackerActivityViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val defaultTracker = VpnTracker(
        trackerCompanyId = 1,
        company = "Google LLC",
        companyDisplayName = "Google",
        trackingApp = TrackingApp("app.foo.com", "Foo app"),
        domain = "doubleclick.net"
    )

    private lateinit var viewModel: DeviceShieldTrackerActivityViewModel

    private val appTrackerBlockingStatsRepository = mock<AppTrackerBlockingStatsRepository>()
    private val deviceShieldPixels = mock<DeviceShieldPixels>()
    private val vpnDetector = mock<VpnDetector>()
    private val vpnStateMonitor = mock<VpnStateMonitor>()
    private val workManager = mock<WorkManager>()
    private val vpnFeatureRemover = mock<VpnFeatureRemover>()
    private val vpnStore = mock<VpnStore>()

    @Before
    fun setup() {
        viewModel = DeviceShieldTrackerActivityViewModel(
            deviceShieldPixels,
            appTrackerBlockingStatsRepository,
            vpnStateMonitor,
            vpnDetector,
            vpnFeatureRemover,
            vpnStore,
            coroutineRule.testDispatcherProvider
        )
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
        whenever(vpnStateMonitor.getState()).thenReturn(VpnState(ENABLED, UNKNOWN))
        viewModel.commands().test {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchExcludedApps)

            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchManageAppsProtection, awaitItem())

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
    fun whenToggleIsSwitchedOnAndOtherVPNIsDisabledThenTrackingProtectionIsEnabled() = runBlocking {
        whenever(vpnDetector.isVpnDetected()).thenReturn(false)
        viewModel.commands().test {
            viewModel.onAppTPToggleSwitched(true)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.CheckVPNPermission, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenToggleIsSwitchedOffAndOtherVPNIsDisabledThenConfirmationDialogIsShown() = runBlocking {
        whenever(vpnDetector.isVpnDetected()).thenReturn(false)
        viewModel.commands().test {
            viewModel.onAppTPToggleSwitched(false)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.ShowDisableVpnConfirmationDialog, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnPermissionIsNeededThenVpnPermissionRequestIsLaunched() = runBlocking {
        viewModel.commands().test {
            val permissionIntent = Intent()
            viewModel.onVPNPermissionNeeded(permissionIntent)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.RequestVPNPermission(permissionIntent), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnPermissionResultIsOKThenVpnIsLaunched() = runBlocking {
        viewModel.commands().test {
            viewModel.onVPNPermissionResult(AppCompatActivity.RESULT_OK)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchVPN, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVPNLaunchedAndShouldPromoteAlwaysOnThenShowPromoteAlwaysOnDialogCommandisSent() = runBlocking {
        whenever(vpnStore.getAppTPManuallyEnables()).thenReturn(6)
        whenever(vpnStore.userAllowsShowPromoteAlwaysOn()).thenReturn(true)
        whenever(vpnStore.isAlwaysOnEnabled()).thenReturn(false)

        viewModel.commands().test {
            viewModel.onVPNPermissionResult(AppCompatActivity.RESULT_OK)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchVPN, awaitItem())
            verify(vpnStore).resetAppTPManuallyEnablesCounter()
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.ShowAlwaysOnPromotionDialog, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVPNLaunchedLessThanEnoughTimesThenShowPromoteAlwaysOnDialogCommandisNotSent() = runBlocking {
        whenever(vpnStore.getAppTPManuallyEnables()).thenReturn(1)
        whenever(vpnStore.userAllowsShowPromoteAlwaysOn()).thenReturn(true)
        whenever(vpnStore.isAlwaysOnEnabled()).thenReturn(false)

        viewModel.commands().test {
            viewModel.onVPNPermissionResult(AppCompatActivity.RESULT_OK)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchVPN, expectMostRecentItem())
            verify(vpnStore, times(0)).resetAppTPManuallyEnablesCounter()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserForgotAlwaysOnPromotionDialogThenShowPromoteAlwaysOnDialogCommandisNotSent() = runBlocking {
        whenever(vpnStore.getAppTPManuallyEnables()).thenReturn(6)
        whenever(vpnStore.userAllowsShowPromoteAlwaysOn()).thenReturn(false)
        whenever(vpnStore.isAlwaysOnEnabled()).thenReturn(false)

        viewModel.commands().test {
            viewModel.onVPNPermissionResult(AppCompatActivity.RESULT_OK)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchVPN, expectMostRecentItem())
            verify(vpnStore, times(0)).resetAppTPManuallyEnablesCounter()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVPNInAlwaysOnModeThenShowPromoteAlwaysOnDialogCommandisNotSent() = runBlocking {
        whenever(vpnStore.getAppTPManuallyEnables()).thenReturn(6)
        whenever(vpnStore.userAllowsShowPromoteAlwaysOn()).thenReturn(true)
        whenever(vpnStore.isAlwaysOnEnabled()).thenReturn(true)

        viewModel.commands().test {
            viewModel.onVPNPermissionResult(AppCompatActivity.RESULT_OK)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.LaunchVPN, expectMostRecentItem())
            verify(vpnStore, times(0)).resetAppTPManuallyEnablesCounter()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnPermissionResultIsDeniedAndRequestTimeWasSmallerThanNeededThenVpnConflictDialogIsShown() = runBlocking {
        viewModel.commands().test {
            val permissionIntent = Intent()
            viewModel.onVPNPermissionNeeded(permissionIntent)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.RequestVPNPermission(permissionIntent), awaitItem())

            viewModel.onVPNPermissionResult(AppCompatActivity.RESULT_CANCELED)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.ShowVpnAlwaysOnConflictDialog, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnPermissionResultIsDeniedAndRequestTimeWasHigherThanNeededThenVpnIsStopped() = runBlocking {
        viewModel.commands().test {
            val permissionIntent = Intent()
            viewModel.onVPNPermissionNeeded(permissionIntent)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.RequestVPNPermission(permissionIntent), awaitItem())

            delay(2000)
            viewModel.onVPNPermissionResult(AppCompatActivity.RESULT_CANCELED)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.VPNPermissionNotGranted, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenToggleIsSwitchedOnAndOtherVPNIsEnabledThenVpnConflictDialogIsShown() = runBlocking {
        whenever(vpnDetector.isVpnDetected()).thenReturn(true)
        viewModel.commands().test {
            viewModel.onAppTPToggleSwitched(true)
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.ShowVpnConflictDialog, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppTPIsManuallyDisabledThenTrackingProtectionIsStopped() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppTpManuallyDisabled()

            verify(deviceShieldPixels).disableFromSummaryTrackerActivity()
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.StopVPN, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserWantsToRemoveFeatureThenDalogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onViewEvent(ViewEvent.AskToRemoveFeature)

            assertEquals(DeviceShieldTrackerActivityViewModel.Command.ShowRemoveFeatureConfirmationDialog, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserAcceptsToRemoveFeatureThenFeatureIsRemovedAndVpnAndScreenClosed() = runBlocking {
        viewModel.commands().test {
            viewModel.onViewEvent(ViewEvent.RemoveFeature)

            verify(deviceShieldPixels).didChooseToRemoveTrackingProtectionFeature()
            verify(vpnFeatureRemover).manuallyRemoveFeature()
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.StopVPN, awaitItem())
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.CloseScreen, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPromoteAlwaysOnOpenSettingsSelectedThenCommandIsSent() = runBlocking {
        viewModel.commands().test {
            viewModel.onViewEvent(ViewEvent.PromoteAlwaysOnOpenSettings)

            verify(deviceShieldPixels).didChooseToOpenSettingsFromPromoteAlwaysOnDialog()
            assertEquals(DeviceShieldTrackerActivityViewModel.Command.OpenVpnSettings, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPromoteAlwaysOnRemindLaterThenPixelIsSent() {
        viewModel.onViewEvent(ViewEvent.PromoteAlwaysOnRemindLater)

        verify(deviceShieldPixels).didChooseToDismissPromoteAlwaysOnDialog()
    }

    @Test
    fun whenPromoteAlwaysOnForgetThenPixelIsSent() {
        viewModel.onViewEvent(ViewEvent.PromoteAlwaysOnForget)

        verify(deviceShieldPixels).didChooseToForgetPromoteAlwaysOnDialog()
        verify(vpnStore).onForgetPromoteAlwaysOn()
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
