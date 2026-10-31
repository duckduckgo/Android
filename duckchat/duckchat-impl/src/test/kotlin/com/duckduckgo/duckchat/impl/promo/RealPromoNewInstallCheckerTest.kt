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

package com.duckduckgo.duckchat.impl.promo

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class RealPromoNewInstallCheckerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPackageManager: PackageManager = mock {
        on { getPackageInfo(PACKAGE_NAME, 0) } doReturn PackageInfo().apply { firstInstallTime = INSTALL_TIME }
    }
    private val mockContext: Context = mock {
        on { packageManager } doReturn mockPackageManager
        on { packageName } doReturn PACKAGE_NAME
    }
    private val mockAppBuildConfig: AppBuildConfig = mock {
        on { sdkInt } doReturn 30
        onBlocking { isAppReinstall() } doReturn false
    }
    private val currentTimeProvider = FakeCurrentTimeProvider(INSTALL_TIME)

    private val testee: PromoNewInstallChecker = RealPromoNewInstallChecker(
        context = mockContext,
        appBuildConfig = mockAppBuildConfig,
        currentTimeProvider = currentTimeProvider,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `when first install just happened, then it's a new install`() = runTest {
        givenFirstInstall(installedAgo = Duration.ZERO)

        assertTrue(testee.isNewInstall(INSTALL_WINDOW_DAYS))
    }

    @Test
    fun `when first install happened on the last day of the window, then it's a new install`() = runTest {
        givenFirstInstall(installedAgo = (INSTALL_WINDOW_DAYS + 1).days - 1.milliseconds)

        assertTrue(testee.isNewInstall(INSTALL_WINDOW_DAYS))
    }

    @Test
    fun `when first install happened after the window, then it's not a new install`() = runTest {
        givenFirstInstall(installedAgo = (INSTALL_WINDOW_DAYS + 1).days)

        assertFalse(testee.isNewInstall(INSTALL_WINDOW_DAYS))
    }

    @Test
    fun `when install time is a day or more in the future, then it's not a new install`() = runTest {
        givenFirstInstall(installedAgo = -(25.hours))

        assertFalse(testee.isNewInstall(INSTALL_WINDOW_DAYS))
    }

    @Test
    fun `when app was reinstalled, then it's not a new install`() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)

        assertFalse(testee.isNewInstall(INSTALL_WINDOW_DAYS))
    }

    @Test
    fun `when running below Android 11, then it's not a new install`() = runTest {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(29)

        assertFalse(testee.isNewInstall(INSTALL_WINDOW_DAYS))
        verify(mockAppBuildConfig, never()).isAppReinstall()
    }

    @Test
    fun `when checked more than once, then the install time is read once`() = runTest {
        testee.isNewInstall(INSTALL_WINDOW_DAYS)
        testee.isNewInstall(INSTALL_WINDOW_DAYS)

        verify(mockPackageManager, times(1)).getPackageInfo(PACKAGE_NAME, 0)
    }

    private fun givenFirstInstall(installedAgo: Duration) {
        currentTimeProvider.nowMillis = INSTALL_TIME + installedAgo.inWholeMilliseconds
    }

    private companion object {
        const val PACKAGE_NAME = "com.duckduckgo.mobile.android"
        const val INSTALL_TIME = 1_700_000_000_000L
        const val INSTALL_WINDOW_DAYS = 7
    }
}
