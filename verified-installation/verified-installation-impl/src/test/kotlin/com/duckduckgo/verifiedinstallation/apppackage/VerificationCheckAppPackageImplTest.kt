/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.verifiedinstallation.apppackage

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VerificationCheckAppPackageImplTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val testee = VerificationCheckAppPackageImpl(appBuildConfig)

    @Test
    fun whenPackageIsDebugVersionThenIsNotVerified() {
        whenever(appBuildConfig.applicationId).thenReturn("com.duckduckgo.mobile.android.debug")
        assertFalse(testee.isProductionPackage())
    }

    @Test
    fun whenPackageIsProductionVersionThenIsVerified() {
        whenever(appBuildConfig.applicationId).thenReturn("com.duckduckgo.mobile.android")
        assertTrue(testee.isProductionPackage())
    }

    @Test
    fun whenPackageIsUnrelatedToUsThenIsNotVerified() {
        whenever(appBuildConfig.applicationId).thenReturn("com.random.app")
        assertFalse(testee.isProductionPackage())
    }
}
