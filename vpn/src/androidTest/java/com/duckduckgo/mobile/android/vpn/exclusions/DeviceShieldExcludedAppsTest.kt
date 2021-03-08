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

package com.duckduckgo.mobile.android.vpn.exclusions

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.nhaarman.mockitokotlin2.whenever
import junit.framework.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class DeviceShieldExcludedAppsTest {
    @Mock
    private lateinit var packageManager: PackageManager

    @Mock
    private lateinit var applicationInfo: ApplicationInfo

    private lateinit var deviceShieldExcludedApps: DeviceShieldExcludedApps

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        whenever(packageManager.getApplicationInfo(anyString(), eq(0))).thenReturn(applicationInfo)
        whenever(packageManager.getApplicationLabel(applicationInfo)).thenReturn("foo app")

        deviceShieldExcludedApps = DeviceShieldExcludedAppsModule().provideDeviceShieldExcludedApps(
            InstrumentationRegistry.getInstrumentation().targetContext, packageManager)
    }

    @Test
    fun whenGetExcludedAppsAndNoInstalledAppsThenReturnEmptyList() {
        whenever(packageManager.getPackageInfo(anyString(), eq(0)))
            .thenThrow(RuntimeException())

        assertTrue(deviceShieldExcludedApps.getExcludedApps().isEmpty())
    }

    @Test
    fun whenGetExcludedAppsThenFilterOutDuckDuckGoApps() {
        val apps = deviceShieldExcludedApps.getExcludedApps().filter { it.packageName.startsWith("com.duckduckgo") }
        assertTrue(apps.isEmpty())
    }

    @Test
    fun whenGetExclusionListThenReturnAllExcludedPackages() {
        assertEquals(68, deviceShieldExcludedApps.getExclusionList().size)
    }

    @Test
    fun whenGetExclusionListThenContainsDuckDuckGoAppPackages() {
        val ddgPackages = deviceShieldExcludedApps.getExclusionList().filter { it.startsWith("com.duckduckgo") }
        assertEquals(4, ddgPackages.size)
    }
}