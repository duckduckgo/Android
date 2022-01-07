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

package com.duckduckgo.mobile.android.vpn.breakage

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.mobile.android.vpn.apps.AppCategory
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class ReportBreakageAppListViewModelTest {

    private val trackingProtectionAppsRepository = mock<TrackingProtectionAppsRepository>()
    private val protectedAppsChannel = Channel<List<TrackingProtectionAppInfo>>(1, BufferOverflow.DROP_LATEST)

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var viewModel: ReportBreakageAppListViewModel

    @Before
    fun setup() {

        viewModel = ReportBreakageAppListViewModel(trackingProtectionAppsRepository)
    }

    @Test
    fun whenOnSubmitBreakageAndNoSelectedItemThenEmitNoCommand() = runTest {
        viewModel.commands().test {
            viewModel.onSubmitBreakage()

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnSubmitBreakageAndAppSelectedThenEmitLaunchBreakageFormCommand() = runTest {
        viewModel.commands().test {
            val expectedItem = InstalledApp(packageName = "com.android.ddg", name = "ddg", isSelected = true)
            viewModel.onAppSelected(expectedItem)
            viewModel.onSubmitBreakage()

            assertEquals(ReportBreakageAppListView.Command.LaunchBreakageForm(expectedItem), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetInstalledAppsAndNoInstalledAppsThenEmitNoItem() = runTest {
        whenever(trackingProtectionAppsRepository.getProtectedApps()).thenReturn(protectedAppsChannel.receiveAsFlow())
        viewModel.getInstalledApps().test {
            expectNoEvents()
        }
    }

    @Test
    fun whenGetInstalledAppsThenEmitState() = runTest {
        whenever(trackingProtectionAppsRepository.getProtectedApps()).thenReturn(protectedAppsChannel.receiveAsFlow())
        viewModel.getInstalledApps().test {
            protectedAppsChannel.send(listOf(appWithoutIssues))
            assertEquals(
                ReportBreakageAppListView.State(
                    listOf(InstalledApp(packageName = appWithoutIssues.packageName, name = appWithoutIssues.name)),
                    false
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun whenGetInstalledAppsAndSelectedAppThenEmitState() = runTest {
        whenever(trackingProtectionAppsRepository.getProtectedApps()).thenReturn(protectedAppsChannel.receiveAsFlow())
        viewModel.getInstalledApps().test {
            viewModel.onAppSelected(InstalledApp(packageName = appWithIssues.packageName, name = appWithIssues.name))
            protectedAppsChannel.send(listOf(appWithoutIssues, appWithIssues))
            val expected = listOf(
                InstalledApp(packageName = appWithoutIssues.packageName, name = appWithoutIssues.name),
                InstalledApp(packageName = appWithIssues.packageName, name = appWithIssues.name, isSelected = true),
            )
            assertEquals(
                ReportBreakageAppListView.State(expected, true),
                awaitItem()
            )
        }
    }

    @Test
    fun whenGetInstalledAppsAndUnknownSelectedAppThenEmitState() = runTest {
        whenever(trackingProtectionAppsRepository.getProtectedApps()).thenReturn(protectedAppsChannel.receiveAsFlow())
        viewModel.getInstalledApps().test {
            viewModel.onAppSelected(InstalledApp(packageName = "unknown.package.name", name = appWithIssues.name))
            protectedAppsChannel.send(listOf(appWithoutIssues, appWithIssues))
            val expected = listOf(
                InstalledApp(packageName = appWithoutIssues.packageName, name = appWithoutIssues.name),
                InstalledApp(packageName = appWithIssues.packageName, name = appWithIssues.name),
            )
            assertEquals(
                ReportBreakageAppListView.State(expected, false),
                awaitItem()
            )
        }
    }

    @Test
    fun whenOnBreakageSubmittedNoExtraInfoThenEmitSendBreakageInfoCommand() = runTest {
        viewModel.commands().test {
            val selectedApp = InstalledApp("com.package.com", name = "AppName")
            viewModel.onAppSelected(selectedApp)
            viewModel.onBreakageSubmitted(IssueReport())

            val expectedCommand = ReportBreakageAppListView.Command.SendBreakageInfo(
                IssueReport(appPackageId = selectedApp.packageName)
            )

            assertEquals(expectedCommand, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnBreakageSubmittedWithExtraInfoThenEmitSendBreakageInfoCommand() = runTest {
        viewModel.commands().test {
            val selectedApp = InstalledApp("com.package.com", name = "AppName")
            viewModel.onAppSelected(selectedApp)
            viewModel.onBreakageSubmitted(IssueReport(description = "description"))

            val expectedCommand = ReportBreakageAppListView.Command.SendBreakageInfo(
                IssueReport(description = "description", appPackageId = selectedApp.packageName)
            )

            assertEquals(expectedCommand, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    private val appWithoutIssues = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = false,
        knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
        userModifed = false
    )

    private val appWithIssues = TrackingProtectionAppInfo(
        packageName = "com.issues.package.name",
        name = "App",
        type = "None",
        category = AppCategory.Undefined,
        isExcluded = false,
        knownProblem = TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON,
        userModifed = false
    )
}
