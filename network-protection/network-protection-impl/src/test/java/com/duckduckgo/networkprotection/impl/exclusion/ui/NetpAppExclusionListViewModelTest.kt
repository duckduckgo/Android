/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.exclusion.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.exclusion.SystemAppOverridesProvider
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.networkprotection.impl.R.string
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.AppType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.FilterType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.HeaderType
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.RestartVpn
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowDisableProtectionDialog
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowIssueReportingPage
import com.duckduckgo.networkprotection.impl.exclusion.ui.HeaderContent.DEFAULT
import com.duckduckgo.networkprotection.impl.exclusion.ui.NetpAppExclusionListActivity.Companion.AppsFilter
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NetpAppExclusionListViewModelTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var mockPackageManager: PackageManager

    @Mock
    private lateinit var netPExclusionListRepository: NetPExclusionListRepository

    @Mock
    private lateinit var systemAppOverridesProvider: SystemAppOverridesProvider

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels
    private val testbreakageCategories = listOf(AppBreakageCategory("test", "test description"))
    private val exclusionListFlow = MutableStateFlow(MANUAL_EXCLUSION_LIST)
    private lateinit var testee: NetpAppExclusionListViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockPackageManager.getInstalledApplications(PackageManager.GET_META_DATA)).thenReturn(INSTALLED_APPS.asApplicationInfo())
        whenever(mockPackageManager.getApplicationLabel(any())).thenReturn("App Name")
        whenever(netPExclusionListRepository.getManualAppExclusionListFlow()).thenReturn(exclusionListFlow)
        testee = NetpAppExclusionListViewModel(
            mockPackageManager,
            coroutineRule.testDispatcherProvider,
            netPExclusionListRepository,
            testbreakageCategories,
            systemAppOverridesProvider,
            networkProtectionPixels,
        )

        testee.initialize()
    }

    private fun List<String>.asApplicationInfo(): List<ApplicationInfo> {
        return this.map {
            ApplicationInfo()
                .apply {
                    packageName = it
                    category = if (it == "com.example.game") ApplicationInfo.CATEGORY_GAME else ApplicationInfo.CATEGORY_UNDEFINED
                    flags = if (it.startsWith("com.example.system")) ApplicationInfo.FLAG_SYSTEM else 0
                }
        }
    }

    @Test
    fun whenFilterIsAllAndGetAppsThenReturnCorrectViewState() = runTest {
        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = DEFAULT),
                        FilterType(string.netpExclusionListFilterMenuAllLabel, 5),
                        AppType(NetpExclusionListApp("com.example.app1", "App Name", true)),
                        AppType(NetpExclusionListApp("com.example.app2", "App Name", false)),
                        AppType(NetpExclusionListApp("com.example.app3", "App Name", false)),
                        AppType(NetpExclusionListApp("com.example.game", "App Name", true)),
                        AppType(NetpExclusionListApp("com.duckduckgo.mobile", "App Name", true)),
                    ),
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }

        verify(networkProtectionPixels).reportExclusionListShown()
    }

    @Test
    fun whenFilterIsProtectedOnlyAndGetAppsThenReturnCorrectViewState() = runTest {
        testee.applyAppsFilter(AppsFilter.PROTECTED_ONLY)

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = DEFAULT),
                        FilterType(string.netpExclusionListFilterMenuProtectedLabel, 3),
                        AppType(NetpExclusionListApp("com.example.app1", "App Name", true)),
                        AppType(NetpExclusionListApp("com.example.game", "App Name", true)),
                        AppType(NetpExclusionListApp("com.duckduckgo.mobile", "App Name", true)),
                    ),
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFilterIsUnprotectedOnlyAndGetAppsThenReturnCorrectViewState() = runTest {
        testee.applyAppsFilter(AppsFilter.UNPROTECTED_ONLY)

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = DEFAULT),
                        FilterType(string.netpExclusionListFilterMenuUnprotectedLabel, 2),
                        AppType(NetpExclusionListApp("com.example.app2", "App Name", false)),
                        AppType(NetpExclusionListApp("com.example.app3", "App Name", false)),
                    ),
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetAppsAndSystemAppIsInOverrideThenReturnCorrectViewState() = runTest {
        whenever(systemAppOverridesProvider.getSystemAppOverridesList()).thenReturn(listOf("com.example.system"))

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = DEFAULT),
                        FilterType(string.netpExclusionListFilterMenuAllLabel, 6),
                        AppType(NetpExclusionListApp("com.example.app1", "App Name", true)),
                        AppType(NetpExclusionListApp("com.example.app2", "App Name", false)),
                        AppType(NetpExclusionListApp("com.example.app3", "App Name", false)),
                        AppType(NetpExclusionListApp("com.example.game", "App Name", true)),
                        AppType(NetpExclusionListApp("com.example.system", "App Name", true)),
                        AppType(NetpExclusionListApp("com.duckduckgo.mobile", "App Name", true)),
                    ),
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAppProtectionChangedToProtectedThenManuallyEnableApp() {
        testee.onAppProtectionChanged(
            NetpExclusionListApp("com.example.app2", "App Name", false),
            true,
        )
        verify(netPExclusionListRepository).manuallyEnableApp("com.example.app2")
    }

    @Test
    fun whenOnAppProtectionChangedToUnProtectedThenShowDisabledDialog() = runTest {
        val app = NetpExclusionListApp("com.example.app1", "App Name", true)
        testee.onAppProtectionChanged(app, false)

        testee.commands().test {
            assertEquals(
                ShowDisableProtectionDialog(app),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAppProtectionDisabledWithNoReportThenOnlyManuallyExcludeApp() = runTest {
        testee.onAppProtectionDisabled("App Name", "com.example.app1", false)

        verify(netPExclusionListRepository).manuallyExcludeApp("com.example.app1")
        verify(networkProtectionPixels).reportAppAddedToExclusionList()
        verify(networkProtectionPixels).reportSkippedReportAfterExcludingApp()
    }

    @Test
    fun whenOnAppProtectionDisabledAndReportThenManuallyExcludeAndShowIssueReporting() = runTest {
        testee.onAppProtectionDisabled("App Name", "com.example.app1", true)

        verify(netPExclusionListRepository).manuallyExcludeApp("com.example.app1")
        verify(networkProtectionPixels).reportAppAddedToExclusionList()
        verify(networkProtectionPixels).reportExclusionListLaunchBreakageReport()
        testee.commands().test {
            assertEquals(
                ShowIssueReportingPage(
                    OpenVpnBreakageCategoryWithBrokenApp(
                        launchFrom = "netp",
                        appName = "App Name",
                        appPackageId = "com.example.app1",
                        breakageCategories = testbreakageCategories,
                    ),
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLaunchFeedbackThenShowIssueReporting() = runTest {
        testee.launchFeedback()

        verify(networkProtectionPixels).reportExclusionListLaunchBreakageReport()
        testee.commands().test {
            assertEquals(
                ShowIssueReportingPage(
                    OpenVpnBreakageCategoryWithBrokenApp(
                        launchFrom = "netp",
                        appName = "",
                        appPackageId = "",
                        breakageCategories = testbreakageCategories,
                    ),
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRestoreProtectedAppsTheResetRepositoryAndRestartVpn() = runTest {
        testee.restoreProtectedApps()

        verify(netPExclusionListRepository).restoreDefaultProtectedList()
        verify(networkProtectionPixels).reportExclusionListRestoreDefaults()
        testee.commands().test {
            assertEquals(RestartVpn, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserMadeChangesToExclusionListThenOnPauseRestartVpn() = runTest {
        testee.onResume(mock())
        exclusionListFlow.emit(
            listOf(
                NetPManuallyExcludedApp("com.example.app1", true),
                NetPManuallyExcludedApp("com.example.app2", false),
                NetPManuallyExcludedApp("com.example.app3", false),
                NetPManuallyExcludedApp("com.example.game", false),
            ),
        )
        testee.onPause(mock())

        testee.commands().test {
            assertEquals(RestartVpn, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserMadeNoChangesToExclusionListThenOnPauseDoNothing() = runTest {
        testee.onResume(mock())
        testee.onPause(mock())

        testee.commands().test {
            expectNoEvents()
        }
    }

    @Test
    fun whenExclusionListResetToOriginalThenOnPauseDoNothing() = runTest {
        testee.onResume(mock())
        exclusionListFlow.emit(
            listOf(
                NetPManuallyExcludedApp("com.example.app1", true),
                NetPManuallyExcludedApp("com.example.app2", false),
                NetPManuallyExcludedApp("com.example.app3", false),
                NetPManuallyExcludedApp("com.example.game", false),
            ),
        )
        exclusionListFlow.emit(MANUAL_EXCLUSION_LIST)
        testee.onPause(mock())

        testee.commands().test {
            expectNoEvents()
        }
    }

    companion object {
        private val INSTALLED_APPS = listOf(
            "com.example.app1",
            "com.example.app2",
            "com.example.app3",
            "com.example.game",
            "com.example.system",
            "com.duckduckgo.mobile",
        )
        private val MANUAL_EXCLUSION_LIST = listOf(
            NetPManuallyExcludedApp("com.example.app1", true),
            NetPManuallyExcludedApp("com.example.app2", false),
            NetPManuallyExcludedApp("com.example.app3", false),
        )
    }
}
