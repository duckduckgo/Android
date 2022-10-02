/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.trackers.FakeAppTrackerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingProtectionAppsRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val packageManager: PackageManager = mock()
    private val appTrackerRepository = FakeAppTrackerRepository()
    private val appTpFeatureConfig: AppTpFeatureConfig = mock()

    private lateinit var trackingProtectionAppsRepository: TrackingProtectionAppsRepository

    @Before
    fun setup() {
        whenever(appTpFeatureConfig.isEnabled(AppTpSetting.ProtectGames)).thenReturn(false)
        whenever(packageManager.getInstalledApplications(PackageManager.GET_META_DATA)).thenReturn(INSTALLED_APPS.asApplicationInfo())
        whenever(packageManager.getApplicationLabel(any())).thenReturn("App Name")
        appTrackerRepository.appExclusionList = EXCLUSION_LIST.toSet()
        appTrackerRepository.manualExclusionList = MANUAL_EXCLUSION_LIST.toMutableMap()
        appTrackerRepository.systemAppOverrides = SYSTEM_OVERRIDE_LIST.toSet()

        trackingProtectionAppsRepository =
            RealTrackingProtectionAppsRepository(
                packageManager, appTrackerRepository, coroutineRule.testDispatcherProvider, appTpFeatureConfig
            )
    }

    @Test
    fun whenGetExclusionAppListThenReturnExclusionList() = runTest {
        val exclusionList = trackingProtectionAppsRepository.getExclusionAppsList()

        assertEquals(
            listOf("com.example.app2", "com.example.app3", "com.example.app5", "com.example.game", "com.example.system", "com.duckduckgo.mobile"),
            exclusionList
        )
    }

    @Test
    fun whenIsProtectionEnabledCalledOnDisabledAppThenReturnFalse() = runTest {
        whenever(packageManager.getApplicationInfo("com.example.app2", 0))
            .thenReturn(ApplicationInfo().apply { packageName = "com.example.app2" })

        val isEnabled = trackingProtectionAppsRepository.isAppProtectionEnabled("com.example.app2")

        assertFalse(isEnabled)
    }

    @Test
    fun whenIsProtectionEnabledCalledOnEnabledAppThenReturnTrue() = runTest {
        whenever(packageManager.getApplicationInfo("com.example.app1", 0))
            .thenReturn(ApplicationInfo().apply { packageName = "com.example.app1" })

        val isEnabled = trackingProtectionAppsRepository.isAppProtectionEnabled("com.example.app1")

        assertTrue(isEnabled)
    }

    @Test
    fun whenIsProtectionEnabledCalledOnGameThenReturnFalse() = runTest {
        whenever(packageManager.getApplicationInfo("com.example.game", 0))
            .thenReturn(
                ApplicationInfo().apply {
                    packageName = "com.example.game"
                    category = ApplicationInfo.CATEGORY_GAME
                }
            )

        val isEnabled = trackingProtectionAppsRepository.isAppProtectionEnabled("com.example.game")

        assertFalse(isEnabled)
    }

    @Test
    fun whenIsProtectionEnabledCalledOnDdgAppThenReturnFalse() = runTest {
        whenever(packageManager.getApplicationInfo("com.duckduckgo.mobile", 0))
            .thenReturn(ApplicationInfo().apply { packageName = "com.duckduckgo.mobile" })

        val isEnabled = trackingProtectionAppsRepository.isAppProtectionEnabled("com.duckduckgo.mobile")

        assertFalse(isEnabled)
    }

    @Test
    fun whenIsProtectionEnabledCalledOnUnknownPackageThenReturnTrue() = runTest {
        whenever(packageManager.getApplicationInfo("com.example.unknown", 0))
            .thenReturn(ApplicationInfo().apply { packageName = "com.example.unknown" })

        val isEnabled = trackingProtectionAppsRepository.isAppProtectionEnabled("com.example.unknown")

        assertTrue(isEnabled)
    }

    @Test
    fun whenIsProtectionEnabledCalledAndNameNotFoundExceptionIsThrownThenReturnTrue() = runTest {
        whenever(packageManager.getApplicationInfo("com.example.unknown", 0))
            .thenThrow(NameNotFoundException())

        val isEnabled = trackingProtectionAppsRepository.isAppProtectionEnabled("com.example.unknown")

        assertTrue(isEnabled)
    }

    @Test
    fun whenManuallyEnabledAppThenRemoveFromExclusionList() = runTest {
        trackingProtectionAppsRepository.manuallyEnabledApp("com.example.app2")

        val exclusionList = trackingProtectionAppsRepository.getExclusionAppsList()

        assertEquals(listOf("com.example.app3", "com.example.app5", "com.example.game", "com.example.system", "com.duckduckgo.mobile"), exclusionList)
    }

    @Test
    fun whenManuallyExcludedAppsThenReturnExcludedApps() = runTest {
        trackingProtectionAppsRepository.manuallyExcludedApps().test {
            assertEquals(
                listOf("com.example.app1" to true, "com.example.app2" to false, "com.example.app3" to false),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenManuallyExcludeAppThenAddToExclusionList() = runTest {
        trackingProtectionAppsRepository.manuallyExcludeApp("com.example.app1")

        val exclusionList = trackingProtectionAppsRepository.getExclusionAppsList()

        assertEquals(
            listOf(
                "com.example.app1",
                "com.example.app2",
                "com.example.app3",
                "com.example.app5",
                "com.example.game",
                "com.example.system",
                "com.duckduckgo.mobile"
            ),
            exclusionList
        )
    }

    @Test
    fun whenRestoreDefaultProtectedListThenClearManualExclusionList() = runTest {
        trackingProtectionAppsRepository.restoreDefaultProtectedList()

        val exclusionList = trackingProtectionAppsRepository.getExclusionAppsList()

        assertEquals(
            listOf("com.example.app1", "com.example.app3", "com.example.app5", "com.example.game", "com.example.system", "com.duckduckgo.mobile"),
            exclusionList
        )
    }

    @Test
    fun whenGetAppsAndProtectionInfoThenReturnAppsWithProtectionInfo() = runTest {
        trackingProtectionAppsRepository.getAppsAndProtectionInfo().test {
            assertEquals(
                listOf(
                    "com.example.app1" to false,
                    "com.example.app2" to true,
                    "com.example.app3" to true,
                    "com.example.app4" to false,
                    "com.example.app5" to true,
                    "com.example.app6" to false,
                    "com.example.game" to true,
                    "com.example.system.overriden" to false,
                ),
                this.awaitItem().map { it.packageName to it.isExcluded }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProtectGamesFeatureEnabledThenRemoveGamesFromExclusionList() = runTest {
        whenever(appTpFeatureConfig.isEnabled(AppTpSetting.ProtectGames)).thenReturn(true)

        val exclusionList = trackingProtectionAppsRepository.getExclusionAppsList()

        assertEquals(listOf("com.example.app2", "com.example.app3", "com.example.app5", "com.example.system", "com.duckduckgo.mobile"), exclusionList)
    }

    @Test
    fun whenProtectGamesFeatureEnabledThenShowGamesAsProtectedInProtectionInfo() = runTest {
        whenever(appTpFeatureConfig.isEnabled(AppTpSetting.ProtectGames)).thenReturn(true)

        trackingProtectionAppsRepository.getAppsAndProtectionInfo().test {
            assertEquals(
                listOf(
                    "com.example.app1" to false,
                    "com.example.app2" to true,
                    "com.example.app3" to true,
                    "com.example.app4" to false,
                    "com.example.app5" to true,
                    "com.example.app6" to false,
                    "com.example.game" to false,
                    "com.example.system.overriden" to false,
                ),
                this.awaitItem().map { it.packageName to it.isExcluded }
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
        private val EXCLUSION_LIST = listOf(
            "com.example.app1",
            "com.example.app3",
            "com.example.app5",
        )
        private val MANUAL_EXCLUSION_LIST = mapOf(
            "com.example.app1" to true,
            "com.example.app2" to false,
            "com.example.app3" to false,
        )
        private val SYSTEM_OVERRIDE_LIST = listOf(
            "com.example.system.overriden",
        )
    }
}
