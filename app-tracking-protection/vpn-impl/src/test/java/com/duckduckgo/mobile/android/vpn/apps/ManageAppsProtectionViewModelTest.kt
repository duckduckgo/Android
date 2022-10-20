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
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun whenAllAppsFilterAppliedAndGetProtectedAppsCalledThenProtectedAndUnprotectedAppsAreReturned() = runTest {
        val protectedApps = listOf(appWithoutIssues)
        val unprotectedApps = listOf(appWithKnownIssues, appLoadsWebsites, appManuallyExcluded)
        val allApps = protectedApps + unprotectedApps
        whenever(trackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(
            flowOf(
                allApps
            )
        )
        viewModel.applyAppsFilter(TrackingProtectionExclusionListActivity.Companion.AppsFilter.ALL)

        viewModel.getProtectedApps().test {
            assertEquals(ViewState(allApps, R.string.atp_ExcludedAppsFilterAllLabel, BannerContent.ALL_OR_PROTECTED_APPS), awaitItem())
        }
    }

    @Test
    fun whenProtectedOnlyFilterAppliedAndGetProtectedAppsCalledThenOnlyProtectedAppsAreReturned() = runTest {
        val protectedApps = listOf(appWithoutIssues)
        val unprotectedApps = listOf(appWithKnownIssues, appLoadsWebsites, appManuallyExcluded)
        val allApps = protectedApps + unprotectedApps
        whenever(trackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(
            flowOf(
                allApps
            )
        )
        viewModel.applyAppsFilter(TrackingProtectionExclusionListActivity.Companion.AppsFilter.PROTECTED_ONLY)

        viewModel.getProtectedApps().test {
            assertEquals(
                ViewState(protectedApps, R.string.atp_ExcludedAppsFilterProtectedLabel, BannerContent.ALL_OR_PROTECTED_APPS),
                awaitItem()
            )
        }
    }

    @Test
    fun whenProtectedOnlyFilterAppliedAndAllAppsAreUnprotectedAndGetProtectedAppsCalledThenEmptyListIsReturned() = runTest {
        val protectedApps = emptyList<TrackingProtectionAppInfo>()
        val unprotectedApps = listOf(appWithKnownIssues, appLoadsWebsites, appManuallyExcluded)
        val allApps = protectedApps + unprotectedApps
        whenever(trackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(
            flowOf(
                allApps
            )
        )
        viewModel.applyAppsFilter(TrackingProtectionExclusionListActivity.Companion.AppsFilter.PROTECTED_ONLY)

        viewModel.getProtectedApps().test {
            assertEquals(ViewState(emptyList(), R.string.atp_ExcludedAppsFilterProtectedLabel), awaitItem())
        }
    }

    @Test
    fun whenUnprotectedOnlyFilterAppliedAndAllAppsAreProtectedAndGetProtectedAppsCalledThenEmptyListIsReturned() = runTest {
        val protectedApps = listOf(appWithoutIssues)
        val unprotectedApps = emptyList<TrackingProtectionAppInfo>()
        val allApps = protectedApps + unprotectedApps
        whenever(trackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(
            flowOf(
                allApps
            )
        )
        viewModel.applyAppsFilter(TrackingProtectionExclusionListActivity.Companion.AppsFilter.UNPROTECTED_ONLY)

        viewModel.getProtectedApps().test {
            assertEquals(
                ViewState(emptyList(), R.string.atp_ExcludedAppsFilterUnprotectedLabel, BannerContent.UNPROTECTED_APPS),
                awaitItem()
            )
        }
    }

    @Test
    fun whenUnprotectedOnlyFilterAppliedAndGetProtectedAppsCalledThenOnlyUnprotectedAppsAreReturnedAndUnprotectedAppsBannerShown() =
        runTest {
            val protectedApps = listOf(appWithoutIssues)
            val unprotectedApps = listOf(appWithKnownIssues, appLoadsWebsites, appManuallyExcluded)
            val allApps = protectedApps + unprotectedApps
            whenever(trackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(
                flowOf(
                    allApps
                )
            )
            viewModel.applyAppsFilter(TrackingProtectionExclusionListActivity.Companion.AppsFilter.UNPROTECTED_ONLY)

            viewModel.getProtectedApps().test {
                assertEquals(
                    ViewState(unprotectedApps, R.string.atp_ExcludedAppsFilterUnprotectedLabel, BannerContent.UNPROTECTED_APPS),
                    awaitItem()
                )
            }
        }

    @Test
    fun whenAtLeastOneAppManuallyExcludedAndGetProtectedAppsCalledThenShowAllOrProtectedBannerContent() = runTest {
        val expectedBannerContent = BannerContent.ALL_OR_PROTECTED_APPS
        val allApps = listOf(appWithKnownIssues, appManuallyExcluded)
        whenever(trackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(
            flowOf(
                allApps
            )
        )
        viewModel.applyAppsFilter(TrackingProtectionExclusionListActivity.Companion.AppsFilter.ALL)

        viewModel.getProtectedApps().test {
            assertEquals(ViewState(allApps, R.string.atp_ExcludedAppsFilterAllLabel, expectedBannerContent), awaitItem())
        }
    }

    @Test
    fun whenAtLeastOneAppProblematicNotExcludedAndGetProtectedAppsCalledThenShowCustomBannerContent() = runTest {
        val expectedBannerContent = BannerContent.CUSTOMISED_PROTECTION
        val allApps = listOf(appWithKnownIssues, appProblematicNotExcluded)
        whenever(trackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(
            flowOf(
                allApps
            )
        )
        viewModel.applyAppsFilter(TrackingProtectionExclusionListActivity.Companion.AppsFilter.ALL)

        viewModel.getProtectedApps().test {
            assertEquals(ViewState(allApps, R.string.atp_ExcludedAppsFilterAllLabel, expectedBannerContent), awaitItem())
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

    private val appProblematicNotExcluded = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = false,
        knownProblem = TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON,
        userModified = true
    )
}
