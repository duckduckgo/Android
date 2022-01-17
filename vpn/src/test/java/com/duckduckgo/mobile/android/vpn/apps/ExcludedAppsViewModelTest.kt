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
import kotlinx.coroutines.test.runTest
import com.duckduckgo.mobile.android.vpn.apps.Command.LaunchFeedback
import com.duckduckgo.mobile.android.vpn.apps.ui.ManuallyDisableAppProtectionDialog
import com.duckduckgo.mobile.android.vpn.apps.ui.ManuallyDisableAppProtectionDialog.Companion.STOPPED_WORKING
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen.IssueDescriptionForm
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.verifyNoInteractions
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
    fun whenPackageNameIsExcludedThenProtectedAppsExcludesIt() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(ManuallyDisableAppProtectionDialog.NO_REASON_NEEDED, packageName, packageName, skippedReport = false)

        verifyNoInteractions(deviceShieldPixels)
        verify(trackingProtectionAppsRepository).manuallyExcludedApp(packageName)
    }

    @Test
    fun whenPackageNameIsExcludedBecauseStoppedWorkingThenProtectedAppsExcludesItAndLaunchesFeedback() = runTest {
        viewModel.commands().test {
            val packageName = "com.package.name"
            val appName = "name"
            viewModel.onAppProtectionDisabled(STOPPED_WORKING, appName, packageName, skippedReport = false)

            verify(trackingProtectionAppsRepository).manuallyExcludedApp(packageName)

            assertEquals(LaunchFeedback(IssueDescriptionForm("name", "com.package.name")), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPackageNameIsExcludedAndWasPreviouslyExcludedThenProtectedAppsExcludesItAndPixelIsSent() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(ManuallyDisableAppProtectionDialog.DONT_USE, packageName, packageName, skippedReport = false)

        verify(trackingProtectionAppsRepository).manuallyExcludedApp(packageName)
    }

    @Test
    fun whenPackageNameIsEnabledAndAppHasNoIssuesThenProtectedAppsEnablesIt() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionEnabled(packageName, 0)

        verifyNoInteractions(deviceShieldPixels)
        verify(trackingProtectionAppsRepository).manuallyEnabledApp(packageName)
    }

    @Test
    fun whenPackageNameIsEnabledAndAppHasIssuesThenProtectedAppsEnablesItAndSendsPixel() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionEnabled(packageName, 1, true)

        verify(trackingProtectionAppsRepository).manuallyEnabledApp(packageName)
    }

    @Test
    fun whenUserWantsToRestoreDefaultThenDefaultListIsRestoredAndVpnRestarted() = runTest {
        viewModel.commands().test {
            viewModel.restoreProtectedApps()
            assertEquals(Command.RestartVpn, awaitItem())
            verify(trackingProtectionAppsRepository).restoreDefaultProtectedList()
            verify(deviceShieldPixels).restoreDefaultProtectionList()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserLeavesScreenAndChangesWereMadeThenTheVpnIsRestarted() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(0, packageName, packageName, skippedReport = true)

        viewModel.commands().test {
            viewModel.onLeavingScreen()
            assertEquals(Command.RestartVpn, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAppProtectionDisabledSkipDialogThenDoNotLaunchFeedback() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(STOPPED_WORKING, packageName, packageName, skippedReport = true)

        viewModel.commands().test {
            expectNoEvents()
        }
    }

    @Test
    fun whenOnAppProtectionDisabledSkipFalseDialogThenLaunchFeedback() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionDisabled(STOPPED_WORKING, packageName, packageName, skippedReport = false)

        viewModel.commands().test {
            assertEquals(LaunchFeedback(IssueDescriptionForm(packageName, packageName)), awaitItem())
        }
    }

    @Test
    fun whenUserLeavesScreenAndNoChangesWereMadeThenTheVpnIsNotRestarted() = runTest {
        viewModel.commands().test {
            viewModel.onLeavingScreen()
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithKnownIssuesIsEnabledThenEnableProtectionDialogIsShown() = runTest {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithKnownIssues, 0, true)
            assertEquals(Command.ShowEnableProtectionDialog(appWithKnownIssues, 0), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithKnownIssuesIsDisabledThenNoDialogIsShown() = runTest {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithKnownIssues, 0, false)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppLoadsWebsitesIsEnabledThenEnableProtectionDialogIsShown() = runTest {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appLoadsWebsites, 0, true)
            assertEquals(Command.ShowEnableProtectionDialog(appLoadsWebsites, 0), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppLoadsWebsitesIsDisabledThenNoDialogIsShown() = runTest {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appLoadsWebsites, 0, false)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithNoIssuesIsEnabledThenNoDialogIsShown() = runTest {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithoutIssues, 0, true)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppWithNoIssuesIsDisabledThenDisabledDialogIsShown() = runTest {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appWithoutIssues, 0, false)
            assertEquals(Command.ShowDisableProtectionDialog(appWithoutIssues), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppManuallyDisabledIsEnabledThenNoDialogIsShown() = runTest {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appManuallyExcluded, 0, true)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppManuallyDisabledIsDisabledThenDisableDialogIsShown() = runTest {
        viewModel.commands().test {
            viewModel.onAppProtectionChanged(appManuallyExcluded, 0, false)
            assertEquals(Command.ShowDisableProtectionDialog(appManuallyExcluded), awaitItem())
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
