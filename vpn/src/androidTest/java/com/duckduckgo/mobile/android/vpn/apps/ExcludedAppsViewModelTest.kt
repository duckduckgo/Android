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
import com.duckduckgo.mobile.android.vpn.apps.ui.ManuallyDisableAppProtectionDialog
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class ExcludedAppsViewModelTest {

    private val trackingProtectionProtectedApps = mock<TrackingProtectionProtectedApps>()
    private val deviceShieldPixels = mock<DeviceShieldPixels>()

    private lateinit var viewModel: ExcludedAppsViewModel

    @Before
    fun setup() {
        viewModel = ExcludedAppsViewModel(
            trackingProtectionProtectedApps,
            deviceShieldPixels
        )
    }

    @Test
    fun whenPackageNameIsExcludedThenProtectedAppsExcludesIt() = runBlocking {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(ManuallyDisableAppProtectionDialog.NO_REASON_NEEDED, packageName)

        verifyZeroInteractions(deviceShieldPixels)
        verify(trackingProtectionProtectedApps).manuallyExcludedApp(packageName)
    }

    @Test
    fun whenPackageNameIsExcludedAndWasPreviouslyExcludedThenProtectedAppsExcludesItAndPixelIsSent() = runBlocking {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(ManuallyDisableAppProtectionDialog.DONT_USE, packageName)

        verify(deviceShieldPixels).disableAppProtection(packageName, ManuallyDisableAppProtectionDialog.DONT_USE)
        verify(trackingProtectionProtectedApps).manuallyExcludedApp(packageName)
    }

    @Test
    fun whenPackageNameIsEnabledThenProtectedAppsEnablesIt() = runBlocking {
        val packageName = "com.package.name"
        viewModel.onAppProtectionEnabled(packageName, 0)

        verify(deviceShieldPixels).enableAppProtection(packageName, 0)
        verify(trackingProtectionProtectedApps).manuallyEnabledApp(packageName)
    }

    @Test
    fun whenUserWantsToRestoreDefaultThenDefaultListIsRestoredAndVpnRestarted() = runBlocking {
        viewModel.commands().test {
            viewModel.restoreProtectedApps()
            Assert.assertEquals(Command.RestartVpn, expectItem())
            verify(trackingProtectionProtectedApps).restoreDefaultProtectedList()
            verify(deviceShieldPixels).restoreDefaultProtectionList()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserLeavesScreenAndChangesWereMadeThenTheVpnIsRestarted() = runBlocking {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(0, packageName)

        viewModel.commands().test {
            viewModel.onLeavingScreen()
            Assert.assertEquals(Command.RestartVpn, expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserLeavesScreenAndNoChangesWereMadeThenTheVpnIsNotRestarted() = runBlocking {
        viewModel.commands().test {
            viewModel.onLeavingScreen()
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithKnownIssuesIsEnabledThenEnableProtectionDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithKnownIssues, 0, true)
            Assert.assertEquals(Command.ShowEnableProtectionDialog(appWithKnownIssues, 0), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithKnownIssuesIsDisabledThenNoDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithKnownIssues, 0, false)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppLoadsWebsitesIsEnabledThenEnableProtectionDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appLoadsWebsites, 0, true)
            Assert.assertEquals(Command.ShowEnableProtectionDialog(appLoadsWebsites, 0), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppLoadsWebsitesIsDisabledThenNoDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appLoadsWebsites, 0, false)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithNoIssuesIsEnabledThenNoDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithoutIssues, 0, true)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithNoIssuesIsDisabledThenDisabledDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithoutIssues, 0, false)
            Assert.assertEquals(Command.ShowDisableProtectionDialog(appWithoutIssues), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppManuallyDisabledIsEnabledThenNoDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appManuallyExcluded, 0, true)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppManuallyDisabledIsDisabledThenDisableDialogIsShown() = runBlocking {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appManuallyExcluded, 0, false)
            Assert.assertEquals(Command.ShowDisableProtectionDialog(appManuallyExcluded), expectItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    private val appWithKnownIssues = VpnExcludedInstalledAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        excludingReason = VpnExcludedInstalledAppInfo.KNOWN_ISSUES_EXCLUSION_REASON
    )

    private val appLoadsWebsites = VpnExcludedInstalledAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        excludingReason = VpnExcludedInstalledAppInfo.LOADS_WEBSITES_EXCLUSION_REASON
    )

    private val appManuallyExcluded = VpnExcludedInstalledAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        excludingReason = VpnExcludedInstalledAppInfo.MANUALLY_EXCLUDED
    )

    private val appWithoutIssues = VpnExcludedInstalledAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        excludingReason = VpnExcludedInstalledAppInfo.NO_ISSUES
    )
}
