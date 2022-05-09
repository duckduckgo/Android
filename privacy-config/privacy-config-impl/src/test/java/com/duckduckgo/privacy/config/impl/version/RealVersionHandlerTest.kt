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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealVersionHandlerTest {

    lateinit var testee: VersionHandler

    @Before
    fun setup() {
        val mockContext: Context = mock()
        val mockAppBuildConfig: AppBuildConfig = mock()

        testee = RealVersionHandler(mockContext, mockAppBuildConfig)

        whenever(mockAppBuildConfig.versionCode).thenReturn(1234)
    }

    @Test
    fun whenMinSupportedVersionIsNullReturnTrue() {
        assertTrue(testee.isSupportedVersion(null))
    }

    @Test
    fun whenMinSupportedVersionIsGreaterThanAppVersionReturnFalse() {
        assertFalse(testee.isSupportedVersion(5678))
    }

    @Test
    fun whenMinSupportedVersionIsEqualToAppVersionReturnTrue() {
        assertTrue(testee.isSupportedVersion(1234))
    }

    @Test
    fun whenMinSupportedVersionIsSmallerThanAppVersionReturnTrue() {
        assertTrue(testee.isSupportedVersion(123))
    }
}
