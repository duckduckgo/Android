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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.exclusion.SystemAppOverridesProvider
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.networkprotection.impl.R.string
import com.duckduckgo.networkprotection.impl.autoexclude.FakeAutoExcludeAppsRepository
import com.duckduckgo.networkprotection.impl.autoexclude.FakeAutoExcludePrompt
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository.SystemAppCategory
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.AppType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.DividerType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.FilterType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.HeaderType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.SystemAppCategoryType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.SystemAppHeaderType
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.RestartVpn
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowAutoExcludePrompt
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowDisableProtectionDialog
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowIssueReportingPage
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowSystemAppsExclusionWarning
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowUnifiedPproAppFeedback
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowUnifiedPproFeedback
import com.duckduckgo.networkprotection.impl.exclusion.ui.NetpAppExclusionListActivity.Companion.AppsFilter
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NetpAppExclusionListViewModelTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var mockPackageManager: PackageManager

    @Mock
    private lateinit var manualExclusionListRepository: NetPManualExclusionListRepository

    @Mock
    private lateinit var systemAppOverridesProvider: SystemAppOverridesProvider

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels

    @Mock
    private lateinit var systemAppsExclusionRepository: SystemAppsExclusionRepository

    @Mock
    private lateinit var privacyProUnifiedFeedback: PrivacyProUnifiedFeedback

    @Mock
    private lateinit var context: Context

    private val autoExcludeAppsRepository = FakeAutoExcludeAppsRepository()
    private val localConfig = FakeNetPSettingsLocalConfigFactory.create()
    private val testbreakageCategories = listOf(AppBreakageCategory("test", "test description"))
    private val exclusionListFlow = MutableStateFlow(MANUAL_EXCLUSION_LIST)
    private val autoExcludePrompt = FakeAutoExcludePrompt()
    private lateinit var testee: NetpAppExclusionListViewModel

    @SuppressLint("DenyListedApi")
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockPackageManager.getInstalledApplications(PackageManager.GET_META_DATA)).thenReturn(INSTALLED_APPS.asApplicationInfo())
        whenever(mockPackageManager.getApplicationLabel(any())).thenReturn("App Name")
        whenever(manualExclusionListRepository.getManualAppExclusionListFlow()).thenReturn(exclusionListFlow)
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(false))

        testee = NetpAppExclusionListViewModel(
            mockPackageManager,
            coroutineRule.testDispatcherProvider,
            manualExclusionListRepository,
            testbreakageCategories,
            systemAppOverridesProvider,
            networkProtectionPixels,
            systemAppsExclusionRepository,
            privacyProUnifiedFeedback,
            localConfig,
            autoExcludeAppsRepository,
            autoExcludePrompt,
            context,
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
        whenever(systemAppsExclusionRepository.getAvailableCategories()).thenReturn(emptySet())
        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = HeaderContent.Default),
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
        whenever(systemAppsExclusionRepository.getAvailableCategories()).thenReturn(emptySet())
        testee.applyAppsFilter(AppsFilter.PROTECTED_ONLY)

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = HeaderContent.Default),
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
        whenever(systemAppsExclusionRepository.getAvailableCategories()).thenReturn(emptySet())
        testee.applyAppsFilter(AppsFilter.UNPROTECTED_ONLY)

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = HeaderContent.Default),
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
        whenever(systemAppsExclusionRepository.getAvailableCategories()).thenReturn(emptySet())
        whenever(systemAppOverridesProvider.getSystemAppOverridesList()).thenReturn(listOf("com.example.system"))

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = HeaderContent.Default),
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
    fun whenSystemAppCategoriesAvailableThenReturnCorrectViewState() = runTest {
        whenever(systemAppOverridesProvider.getSystemAppOverridesList()).thenReturn(listOf("com.example.system"))
        whenever(systemAppsExclusionRepository.getAvailableCategories()).thenReturn(
            setOf(
                SystemAppCategory.Communication,
            ),
        )
        whenever(systemAppsExclusionRepository.isCategoryExcluded(SystemAppCategory.Communication)).thenReturn(false)

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = HeaderContent.Default),
                        SystemAppHeaderType,
                        SystemAppCategoryType(
                            NetpExclusionListSystemAppCategory(
                                category = SystemAppCategory.Communication,
                                text = SystemAppCategory.Communication.name,
                                isEnabled = true,
                            ),
                        ),
                        DividerType,
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
        verify(manualExclusionListRepository).manuallyEnableApp("com.example.app2")
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

        verify(manualExclusionListRepository).manuallyExcludeApp("com.example.app1")
        verify(networkProtectionPixels).reportAppAddedToExclusionList()
        verify(networkProtectionPixels).reportSkippedReportAfterExcludingApp()
    }

    @Test
    fun whenOnAppProtectionDisabledAndReportThenManuallyExcludeAndShowIssueReporting() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        testee.onAppProtectionDisabled("App Name", "com.example.app1", true)

        verify(manualExclusionListRepository).manuallyExcludeApp("com.example.app1")
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
    fun whenOnAppProtectionDisabledAndReportWithUnifiedFeedbackThenManuallyExcludeAndShowUnifiedFeedback() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(true)
        testee.onAppProtectionDisabled("App Name", "com.example.app1", true)

        verify(manualExclusionListRepository).manuallyExcludeApp("com.example.app1")
        verify(networkProtectionPixels).reportAppAddedToExclusionList()
        verify(networkProtectionPixels).reportExclusionListLaunchBreakageReport()
        testee.commands().test {
            assertEquals(
                ShowUnifiedPproAppFeedback(
                    appName = "App Name",
                    appPackageName = "com.example.app1",
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLaunchFeedbackThenShowIssueReporting() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
    fun whenLaunchFeedbackWithUnifiedFeedbackThenShowUnifiedFeedback() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(true)
        testee.launchFeedback()

        verify(networkProtectionPixels).reportExclusionListLaunchBreakageReport()
        testee.commands().test {
            assertEquals(
                ShowUnifiedPproFeedback,
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRestoreProtectedAppsTheResetRepositoryAndRestartVpn() = runTest {
        testee.restoreProtectedApps()

        verify(manualExclusionListRepository).restoreDefaultProtectedList()
        verify(networkProtectionPixels).reportExclusionListRestoreDefaults()
        verify(systemAppsExclusionRepository).restoreDefaults()
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

    @Test
    fun whenWarningNotYetShownOnSystemAppCategoryStateChangedThenShowWarning() = runTest {
        whenever(systemAppsExclusionRepository.hasShownWarning()).thenReturn(false)

        val category = NetpExclusionListSystemAppCategory(
            category = SystemAppCategory.Communication,
            text = SystemAppCategory.Communication.name,
            isEnabled = true,
        )
        testee.onSystemAppCategoryStateChanged(
            category,
            false,
        )

        testee.commands().test {
            assertEquals(ShowSystemAppsExclusionWarning(category), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
        verify(systemAppsExclusionRepository).markWarningShown()
        verify(systemAppsExclusionRepository, never()).includeCategory(any())
        verify(systemAppsExclusionRepository, never()).excludeCategory(any())
    }

    @Test
    fun whenWarningShownAndCategorySetToDisabledOnSystemAppCategoryStateChangedThenExcludeCategory() = runTest {
        whenever(systemAppsExclusionRepository.hasShownWarning()).thenReturn(true)

        val category = NetpExclusionListSystemAppCategory(
            category = SystemAppCategory.Communication,
            text = SystemAppCategory.Communication.name,
            isEnabled = true,
        )
        testee.onSystemAppCategoryStateChanged(
            category,
            false,
        )

        testee.commands().test {
            expectNoEvents()
        }
        verify(systemAppsExclusionRepository, never()).includeCategory(any())
        verify(systemAppsExclusionRepository).excludeCategory(any())
    }

    @Test
    fun whenWarningShownAndCategorySetToEnabledOnSystemAppCategoryStateChangedThenIncludeCategory() = runTest {
        whenever(systemAppsExclusionRepository.hasShownWarning()).thenReturn(true)

        val category = NetpExclusionListSystemAppCategory(
            category = SystemAppCategory.Communication,
            text = SystemAppCategory.Communication.name,
            isEnabled = false,
        )
        testee.onSystemAppCategoryStateChanged(
            category,
            true,
        )

        testee.commands().test {
            expectNoEvents()
        }
        verify(systemAppsExclusionRepository).includeCategory(any())
        verify(systemAppsExclusionRepository, never()).excludeCategory(any())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenIncompatibleAppNotInstalledAndAutoExcludeEnabledThenShowToggleEnabledOnly() = runTest {
        whenever(systemAppsExclusionRepository.getAvailableCategories()).thenReturn(emptySet())
        autoExcludeAppsRepository.setIncompatibleApps(listOf(VpnIncompatibleApp("test")))
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = true))

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = HeaderContent.WithToggle(true)),
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
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenAppsAreMarkedAsIncompatibleThenViewStateShouldHaveIsNotCompatibleSet() = runTest {
        whenever(systemAppsExclusionRepository.getAvailableCategories()).thenReturn(emptySet())
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("com.example.app1"),
                VpnIncompatibleApp("com.example.app3"),
            ),
        )
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = true))

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = HeaderContent.WithToggle(true)),
                        FilterType(string.netpExclusionListFilterMenuAllLabel, 5),
                        AppType(NetpExclusionListApp("com.example.app1", "App Name", true, isNotCompatibleWithVPN = true)),
                        AppType(NetpExclusionListApp("com.example.app2", "App Name", false)),
                        AppType(NetpExclusionListApp("com.example.app3", "App Name", false, isNotCompatibleWithVPN = true)),
                        AppType(NetpExclusionListApp("com.example.game", "App Name", true)),
                        AppType(NetpExclusionListApp("com.duckduckgo.mobile", "App Name", true)),
                    ),
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenIncompatibleAppExcludedAndAppsForPromptAvailableThenShowPrompt() = runTest {
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = false))
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("com.example.app1"),
                VpnIncompatibleApp("com.example.app3"),
            ),
        )
        autoExcludePrompt.setIncompatibleApps(listOf(VpnIncompatibleApp("com.example.app3")))

        testee.onAppProtectionDisabled("App Name", "com.example.app1", false)

        testee.commands().test {
            assertEquals(
                ShowAutoExcludePrompt(listOf(VpnIncompatibleApp("com.example.app3"))),
                awaitItem(),
            )
            this.ensureAllEventsConsumed()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenIncompatibleAppExcludedAndNoAppsForPromptAvailableThenDontShowPrompt() = runTest {
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = false))
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("com.example.app1"),
                VpnIncompatibleApp("com.example.app3"),
            ),
        )

        testee.onAppProtectionDisabled("App Name", "com.example.app1", false)

        testee.commands().test {
            this.ensureAllEventsConsumed()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenIncompatibleAppExcludedAndAutoExcludeEnabledThenDontShowPrompt() = runTest {
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = true))

        testee.commands().test {
            this.ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenAutoExcludeEnabledAndNoManualExclusionThenUpdateViewStateWithAutoExcludeStateAndExcludeAllIncompatibleApss() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionListFlow()).thenReturn(flowOf(emptyList()))
        whenever(systemAppsExclusionRepository.getAvailableCategories()).thenReturn(emptySet())
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("com.example.app1"),
                VpnIncompatibleApp("com.example.app3"),
            ),
        )
        testee.onAutoExcludeToggled(true)

        testee.commands().test {
            assertTrue(localConfig.autoExcludeBrokenApps().isEnabled())
            assertEquals(
                RestartVpn,
                awaitItem(),
            )
        }

        testee.getApps().test {
            assertEquals(
                ViewState(
                    listOf(
                        HeaderType(headerContent = HeaderContent.WithToggle(true)),
                        FilterType(string.netpExclusionListFilterMenuAllLabel, 5),
                        AppType(NetpExclusionListApp("com.example.app1", "App Name", false, isNotCompatibleWithVPN = true)),
                        AppType(NetpExclusionListApp("com.example.app2", "App Name", true)),
                        AppType(NetpExclusionListApp("com.example.app3", "App Name", false, isNotCompatibleWithVPN = true)),
                        AppType(NetpExclusionListApp("com.example.game", "App Name", true)),
                        AppType(NetpExclusionListApp("com.duckduckgo.mobile", "App Name", true)),
                    ),
                ),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }

        verify(networkProtectionPixels).reportAutoExcludeEnableViaExclusionList()
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
