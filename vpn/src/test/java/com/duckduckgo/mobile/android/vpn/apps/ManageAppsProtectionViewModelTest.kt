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
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class ManageAppsProtectionViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val trackingProtectionAppsRepository = mock<TrackingProtectionAppsRepository>()
    private val appTrackersRepository = mock<AppTrackerBlockingStatsRepository>()
    private val deviceShieldPixels = mock<DeviceShieldPixels>()
    private val manuallyExcludedApps = Channel<List<Pair<String, Boolean>>>(1, BufferOverflow.DROP_OLDEST)

    private lateinit var viewModel: ManageAppsProtectionViewModel

    @Before
    fun setup() {
        whenever(trackingProtectionAppsRepository.manuallyExcludedApps()).thenReturn(manuallyExcludedApps.consumeAsFlow())

        viewModel = ManageAppsProtectionViewModel(
            trackingProtectionAppsRepository,
            appTrackersRepository,
            deviceShieldPixels,
            coroutineRule.testDispatcherProvider
        )
    }

    @Test
    fun whenAppIsManuallyExcludedThenUserMadeChangesReturnsTrue() = runTest {
        manuallyExcludedApps.send(listOf())

        viewModel.onResume(mock())

        manuallyExcludedApps.send(listOf("package.com" to true))

        assertTrue(viewModel.userMadeChanges())
    }

    @Test
    fun whenNoManuallyExcludedAppsThenUserMadeChangesReturnsFalse() = runTest {
        manuallyExcludedApps.send(listOf())

        assertFalse(viewModel.userMadeChanges())
    }

    @Test
    fun whenPackageNameIsExcludedThenProtectedAppsExcludesIt() = runTest {
        val packageName = "com.package.name"
        val appName = "App"
        val report = true

        viewModel.onAppProtectionDisabled(appName, packageName, report)

        verify(trackingProtectionAppsRepository).manuallyExcludeApp(packageName)
    }

    @Test
    fun whenAppProtectionisSubmittedAndReportIsSkippedThenSkipPixelIsSent() = runTest {
        val packageName = "com.package.name"
        val appName = "App"
        val report = false

        viewModel.onAppProtectionDisabled(appName, packageName, report)

        verify(deviceShieldPixels).didSkipManuallyDisableAppProtectionDialog()
    }

    @Test
    fun whenAppProtectionisSubmittedAndReportIsSentThenSubmitPixelIsSent() = runTest {
        val packageName = "com.package.name"
        val appName = "App"
        val report = true

        viewModel.onAppProtectionDisabled(appName, packageName, report)

        verify(deviceShieldPixels).didSubmitManuallyDisableAppProtectionDialog()
    }

    @Test
    fun whenAppProtectionisSubmittedAndReportIsSentThenReportCommandIsSent() = runTest {
        val packageName = "com.package.name"
        val appName = "App"
        val report = true

        viewModel.commands().test {
            viewModel.onAppProtectionDisabled(appName, packageName, report)

            assertEquals(Command.LaunchFeedback(ReportBreakageScreen.IssueDescriptionForm(appName, packageName)), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPackageNameIsEnabledAndAppHasNoIssuesThenProtectedAppsEnablesIt() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionEnabled(packageName)

        verify(deviceShieldPixels).didEnableAppProtectionFromApps()
        verify(trackingProtectionAppsRepository).manuallyEnabledApp(packageName)
    }

    @Test
    fun whenPackageNameIsEnabledAndAppHasIssuesThenProtectedAppsEnablesItAndSendsPixel() = runTest {
        val packageName = "com.package.name"
        viewModel.onAppProtectionEnabled(packageName)

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
    fun whenRestoreProtectionsThenDoNotRestartVpnOnLeavingScreen() = runTest {
        viewModel.commands().test {
            viewModel.restoreProtectedApps()
            assertEquals(Command.RestartVpn, awaitItem())
            viewModel.onPause(mock())
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserLeavesScreenAndChangesWereMadeThenTheVpnIsRestarted() = runTest {
        viewModel.commands().test {
            manuallyExcludedApps.send(listOf())
            viewModel.onResume(mock())
            manuallyExcludedApps.send(listOf("com.package.name" to true))
            viewModel.onPause(mock())
            assertEquals(Command.RestartVpn, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserLeavesScreenAndNoChangesWereMadeThenTheVpnIsNotRestarted() = runTest {
        viewModel.commands().test {
            manuallyExcludedApps.send(listOf())
            viewModel.onResume(mock())
            viewModel.onPause(mock())
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
        userModified = false
    )

    private val appLoadsWebsites = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        knownProblem = TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON,
        userModified = false
    )

    private val appManuallyExcluded = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = true,
        knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
        userModified = true
    )

    private val appWithoutIssues = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = false,
        knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
        userModified = false
    )
}
