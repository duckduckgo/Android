/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.buildconfig

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.install.ReinstallDetector
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.experiments.api.VariantManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAppBuildConfigTest {

    private val reinstallDetector: ReinstallDetector = mock()

    private val deviceMigration: DeviceMigration = mock()
    private val packageManager: PackageManager = mock()
    private val context: Context = mock {
        on { packageName } doReturn "com.duckduckgo.test"
        on { packageManager } doReturn packageManager
    }

    private val testee = RealAppBuildConfig(
        variantManager = lazy { mock<VariantManager>() },
        reinstallDetector = lazy { reinstallDetector },
        deviceMigration = lazy { deviceMigration },
        context = context,
    )

    private fun givenPackageTimes(first: Long, last: Long) {
        val info = PackageInfo().apply {
            firstInstallTime = first
            lastUpdateTime = last
        }
        whenever(packageManager.getPackageInfo("com.duckduckgo.test", 0)).thenReturn(info)
    }

    @Test
    fun whenVersionNameThenReturnsBuildConfigVersionNameWithoutSuffix() {
        assertEquals(BuildConfig.VERSION_NAME, testee.versionName)
    }

    @Test
    fun whenIsAppReinstallThenAnswerComesFromReinstallDetector() = runTest {
        whenever(reinstallDetector.isAppReinstall()).thenReturn(true)

        assertTrue(testee.isAppReinstall())
    }

    @Test
    fun whenNotMigratedThenUsesPackageComparison() {
        givenPackageTimes(first = 1L, last = 1L)
        whenever(deviceMigration.sourceDeviceIsNewInstall()).thenReturn(null)

        assertTrue(testee.isNewInstall())
    }

    @Test
    fun whenMigratedFromEstablishedInstallThenNotNew() {
        givenPackageTimes(first = 1L, last = 1L)
        whenever(deviceMigration.sourceDeviceIsNewInstall()).thenReturn(false)

        assertFalse(testee.isNewInstall())
    }

    @Test
    fun whenMigratedFromNewInstallThenNew() {
        givenPackageTimes(first = 1L, last = 1L)
        whenever(deviceMigration.sourceDeviceIsNewInstall()).thenReturn(true)

        assertTrue(testee.isNewInstall())
    }

    @Test
    fun whenMigratedAndUpdatedSinceThenNotNew() {
        givenPackageTimes(first = 1L, last = 2L)
        whenever(deviceMigration.sourceDeviceIsNewInstall()).thenReturn(true)

        assertFalse(testee.isNewInstall())
    }
}
