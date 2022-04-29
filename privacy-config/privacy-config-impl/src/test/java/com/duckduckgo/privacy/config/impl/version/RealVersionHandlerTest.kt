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

package com.duckduckgo.privacy.config.impl.version

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealVersionHandlerTest {

    lateinit var testee: VersionHandler
    private val packageInfo = PackageInfo()

    @Before
    fun setup() {
        val mockContext: Context = mock()
        val mockPackageManager: PackageManager = mock()

        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockContext.packageName).thenReturn("com.package.name")
        whenever(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(packageInfo)

        testee = RealVersionHandler(mockContext)
    }

    @Test
    fun whenVersionIsNullReturnFalse() {
        assertTrue(testee.isSupportedVersion(null))
    }

    @Test
    fun whenAppVersionLengthIsShorterThan3ReturnFalse() {
        packageInfo.versionName = "1.2"
        assertFalse(testee.isSupportedVersion("1.2.3"))
    }

    @Test
    fun whenMinSupportedVersionLengthIsShorterThan3ReturnFalse() {
        packageInfo.versionName = "1.2.3"
        assertFalse(testee.isSupportedVersion("1.2"))
    }

    @Test
    fun whenAppVersionIsLargerThanMinSupportedVersionThenReturnTrue() {
        packageInfo.versionName = "2.0.0"
        assertTrue(testee.isSupportedVersion("1.0.0"))

        packageInfo.versionName = "2.2.0"
        assertTrue(testee.isSupportedVersion("2.1.0"))

        packageInfo.versionName = "2.2.2"
        assertTrue(testee.isSupportedVersion("2.2.1"))
    }

    @Test
    fun whenAppVersionIsSmallerThanMinSupportedVersionThenReturnFalse() {
        packageInfo.versionName = "1.0.0"
        assertFalse(testee.isSupportedVersion("2.0.0"))

        packageInfo.versionName = "2.1.0"
        assertFalse(testee.isSupportedVersion("2.2.0"))

        packageInfo.versionName = "2.2.1"
        assertFalse(testee.isSupportedVersion("2.2.2"))
    }

    @Test
    fun whenAppVersionIsEqualToMinSupportedVersionThenReturnTrue() {
        packageInfo.versionName = "1.0.0"
        assertTrue(testee.isSupportedVersion("1.0.0"))

        packageInfo.versionName = "1.1.0"
        assertTrue(testee.isSupportedVersion("1.1.0"))

        packageInfo.versionName = "1.1.1"
        assertTrue(testee.isSupportedVersion("1.1.1"))
    }

    @Test
    fun whenThrowsNumberFormatExceptionThenReturnFalse() {
        packageInfo.versionName = "1.0.0"

        assertFalse(testee.isSupportedVersion("1.0.0a"))
    }

    @Test
    fun whenAppVersionIsNullThenReturnFalse() {
        packageInfo.versionName = null

        assertFalse(testee.isSupportedVersion("1.0.0"))
    }
}
