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

package com.duckduckgo.networkprotection.impl.exclusion

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.exclusion.AppCategory
import com.duckduckgo.mobile.android.vpn.exclusion.AppCategoryDetector
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NetPExclusionListTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var packageManager: PackageManager

    @Mock
    private lateinit var netPExclusionListRepository: NetPExclusionListRepository

    @Mock
    private lateinit var appCategoryDetector: AppCategoryDetector

    private lateinit var testee: NetPExclusionList

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(packageManager.getApplicationLabel(any())).thenReturn("App Name")
        whenever(packageManager.getInstalledApplications(PackageManager.GET_META_DATA)).thenReturn(INSTALLED_APPS.asApplicationInfo())
        INSTALLED_APPS.forEach {
            if (it == "com.example.game") {
                whenever(appCategoryDetector.getAppCategory(it)).thenReturn(AppCategory.Game)
            } else {
                whenever(appCategoryDetector.getAppCategory(it)).thenReturn(AppCategory.Undefined)
            }
        }
        whenever(netPExclusionListRepository.getManualAppExclusionList()).thenReturn(MANUAL_EXCLUSION_LIST)
        whenever(netPExclusionListRepository.getManualAppExclusionListFlow()).thenReturn(flowOf(MANUAL_EXCLUSION_LIST))

        testee = NetPExclusionList(packageManager, coroutineRule.testDispatcherProvider, netPExclusionListRepository, appCategoryDetector)
    }

    @Test
    fun whenGetExclusionAppsListThenReturnPackagesInRepositoryWithIsProtectedFalse() = runTest {
        val exclusionList = testee.getExclusionAppsList()

        assertEquals(
            listOf("com.example.app2", "com.example.app3"),
            exclusionList,
        )
    }

    @Test
    fun whenIsAppInExclusionListCalledOnDisabledAppThenReturnFalse() = runTest {
        assertTrue(testee.isAppInExclusionList("com.example.app2"))
    }

    @Test
    fun whenIsInExclusionListCalledOnGameThenReturnFalse() = runTest {
        whenever(packageManager.getApplicationInfo("com.example.game", 0))
            .thenReturn(
                ApplicationInfo().apply {
                    packageName = "com.example.game"
                    category = ApplicationInfo.CATEGORY_GAME
                },
            )

        assertFalse(testee.isAppInExclusionList("com.example.game"))
    }

    @Test
    fun whenIsInExclusionListCalledOnDdgAppThenReturnFalse() = runTest {
        assertFalse(testee.isAppInExclusionList("com.duckduckgo.mobile"))
    }

    @Test
    fun whenIsInExclusionListCalledOnUnknownPackageThenReturnTrue() = runTest {
        assertFalse(testee.isAppInExclusionList("com.example.unknown"))
    }

    @Test
    fun whenIsInExclusionListCalledAndNameNotFoundExceptionIsThrownThenReturnTrue() = runTest {
        whenever(packageManager.getApplicationInfo("com.example.unknown", 0))
            .thenThrow(NameNotFoundException())

        assertFalse(testee.isAppInExclusionList("com.example.unknown"))
    }

    @Test
    fun whenManuallyEnabledAppThenDelegateToRepository() = runTest {
        testee.manuallyEnabledApp("com.example.app2")

        verify(netPExclusionListRepository).manuallyEnableApp("com.example.app2")
    }

    @Test
    fun whenManuallyExcludedAppsThenReturnExcludedApps() = runTest {
        testee.manuallyExcludedApps().test {
            assertEquals(
                listOf("com.example.app1" to true, "com.example.app2" to false, "com.example.app3" to false),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenManuallyExcludeAppThenAddToExclusionList() = runTest {
        testee.manuallyExcludeApp("com.example.app1")

        verify(netPExclusionListRepository).manuallyExcludeApp("com.example.app1")
    }

    @Test
    fun whenRestoreDefaultProtectedListThenClearManualExclusionList() = runTest {
        testee.restoreDefaultProtectedList()

        verify(netPExclusionListRepository).restoreDefaultProtectedList()
    }

    @Test
    fun whenGetAppsAndProtectionInfoThenReturnAppsWithProtectionInfo() = runTest {
        testee.getAppsAndProtectionInfo().test {
            assertEquals(
                listOf(
                    "com.example.app1" to false,
                    "com.example.app2" to true,
                    "com.example.app3" to true,
                    "com.example.app4" to false,
                    "com.example.app5" to false,
                    "com.example.app6" to false,
                    "com.example.game" to false,
                    "com.example.system" to false,
                    "com.example.system.overriden" to false,
                    "com.duckduckgo.mobile" to false,
                ),
                this.awaitItem().map { it.packageName to it.isExcluded },
            )
            cancelAndIgnoreRemainingEvents()
        }
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

    companion object {
        private val INSTALLED_APPS = listOf(
            "com.example.app1",
            "com.example.app2",
            "com.example.app3",
            "com.example.app4",
            "com.example.app5",
            "com.example.app6",
            "com.example.game", // should be automatically be added to exclusion list
            "com.example.system", // should be automatically be added to exclusion list
            "com.example.system.overriden",
            "com.duckduckgo.mobile", // should be automatically be added to exclusion list
        )
        private val MANUAL_EXCLUSION_LIST = listOf(
            NetPManuallyExcludedApp("com.example.app1", true),
            NetPManuallyExcludedApp("com.example.app2", false),
            NetPManuallyExcludedApp("com.example.app3", false),
        )
    }
}
