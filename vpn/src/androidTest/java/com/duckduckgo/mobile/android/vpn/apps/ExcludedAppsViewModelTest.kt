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

package com.duckduckgo.mobile.android.vpn.apps

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.mobile.android.vpn.apps.ui.ManuallyDisableAppProtectionDialog
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class ExcludedAppsViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val trackingProtectionAppsRepository = mock<TrackingProtectionAppsRepository>()
    private val deviceShieldPixels = mock<DeviceShieldPixels>()

    private lateinit var viewModel: ExcludedAppsViewModel

    @Before
    fun setup() {
        viewModel = ExcludedAppsViewModel(
            trackingProtectionAppsRepository,
            deviceShieldPixels
        )
    }

    @Test
    fun whenPackageNameIsExcludedThenProtectedAppsExcludesIt() = coroutineRule.runBlocking {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(ManuallyDisableAppProtectionDialog.NO_REASON_NEEDED, packageName, packageName)

        verifyZeroInteractions(deviceShieldPixels)
        verify(trackingProtectionAppsRepository).manuallyExcludedApp(packageName)
    }

    @Test
    fun whenPackageNameIsExcludedBecauseStoppedWorkingThenProtectedAppsExcludesItAndLaunchesFeedback() = coroutineRule.runBlocking {
        viewModel.commands().test {
            val packageName = "com.package.name"
            val appName = "name"
            viewModel.onAppProtectionDisabled(ManuallyDisableAppProtectionDialog.STOPPED_WORKING, appName, packageName)

            verify(trackingProtectionAppsRepository).manuallyExcludedApp(packageName)
            verify(deviceShieldPixels).disableAppProtection(packageName, ManuallyDisableAppProtectionDialog.STOPPED_WORKING)

            Assert.assertEquals(Command.LaunchFeedback(ReportBreakageScreen.IssueDescriptionForm("name", "com.package.name")), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPackageNameIsExcludedAndWasPreviouslyExcludedThenProtectedAppsExcludesItAndPixelIsSent() = coroutineRule.runBlocking {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(ManuallyDisableAppProtectionDialog.DONT_USE, packageName, packageName)

        verify(deviceShieldPixels).disableAppProtection(packageName, ManuallyDisableAppProtectionDialog.DONT_USE)
        verify(trackingProtectionAppsRepository).manuallyExcludedApp(packageName)
    }

    @Test
    fun whenPackageNameIsEnabledThenProtectedAppsEnablesIt() = coroutineRule.runBlocking {
        val packageName = "com.package.name"
        viewModel.onAppProtectionEnabled(packageName, 0)

        verify(deviceShieldPixels).enableAppProtection(packageName, 0)
        verify(trackingProtectionAppsRepository).manuallyEnabledApp(packageName)
    }

    @Test
    fun whenUserWantsToRestoreDefaultThenDefaultListIsRestoredAndVpnRestarted() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.restoreProtectedApps()
            Assert.assertEquals(Command.RestartVpn, expectItem())
            verify(trackingProtectionAppsRepository).restoreDefaultProtectedList()
            verify(deviceShieldPixels).restoreDefaultProtectionList()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserLeavesScreenAndChangesWereMadeThenTheVpnIsRestarted() = coroutineRule.runBlocking {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(0, packageName, packageName)

        viewModel.commands().test {
            viewModel.onLeavingScreen()
            Assert.assertEquals(Command.RestartVpn, expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserLeavesScreenAndNoChangesWereMadeThenTheVpnIsNotRestarted() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onLeavingScreen()
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithKnownIssuesIsEnabledThenEnableProtectionDialogIsShown() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithKnownIssues, 0, true)
            Assert.assertEquals(Command.ShowEnableProtectionDialog(appWithKnownIssues, 0), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithKnownIssuesIsDisabledThenNoDialogIsShown() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithKnownIssues, 0, false)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppLoadsWebsitesIsEnabledThenEnableProtectionDialogIsShown() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appLoadsWebsites, 0, true)
            Assert.assertEquals(Command.ShowEnableProtectionDialog(appLoadsWebsites, 0), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppLoadsWebsitesIsDisabledThenNoDialogIsShown() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appLoadsWebsites, 0, false)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithNoIssuesIsEnabledThenNoDialogIsShown() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithoutIssues, 0, true)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithNoIssuesIsDisabledThenDisabledDialogIsShown() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithoutIssues, 0, false)
            Assert.assertEquals(Command.ShowDisableProtectionDialog(appWithoutIssues, 0), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppManuallyDisabledIsEnabledThenNoDialogIsShown() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appManuallyExcluded, 0, true)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppManuallyDisabledIsDisabledThenDisableDialogIsShown() = coroutineRule.runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appManuallyExcluded, 0, false)
            Assert.assertEquals(Command.ShowDisableProtectionDialog(appManuallyExcluded, 0), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    private val appWithKnownIssues = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        knownProblem = TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON,
        userModifed = false
    )

    private val appLoadsWebsites = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        knownProblem = TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON,
        userModifed = false
    )

    private val appManuallyExcluded = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
        userModifed = true
    )

    private val appWithoutIssues = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = false,
        knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
        userModifed = false
    )
}
