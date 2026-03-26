/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MinSdkToggleTargetMatcherTest {
    private val appBuildConfig: AppBuildConfig = mock()
    private val matcher = MinSdkToggleTargetMatcher(appBuildConfig)

    @Test
    fun whenTargetHasNullMinSdkVersionThenReturnTrue() {
        whenever(appBuildConfig.sdkInt).thenReturn(23)
        val target = NULL_TARGET.copy(minSdkVersion = null)

        val result = matcher.matchesTargetProperty(target)

        assertTrue(result)
    }

    @Test
    fun whenDeviceSdkEqualToMinSdkVersionThenReturnTrue() {
        whenever(appBuildConfig.sdkInt).thenReturn(23)
        val target = NULL_TARGET.copy(minSdkVersion = 23)

        val result = matcher.matchesTargetProperty(target)

        assertTrue(result)
    }

    @Test
    fun whenDeviceSdkGreaterThanMinSdkVersionThenReturnTrue() {
        whenever(appBuildConfig.sdkInt).thenReturn(30)
        val target = NULL_TARGET.copy(minSdkVersion = 23)

        val result = matcher.matchesTargetProperty(target)

        assertTrue(result)
    }

    @Test
    fun whenDeviceSdkLessThanMinSdkVersionThenReturnFalse() {
        whenever(appBuildConfig.sdkInt).thenReturn(21)
        val target = NULL_TARGET.copy(minSdkVersion = 23)

        val result = matcher.matchesTargetProperty(target)

        assertFalse(result)
    }

    companion object {
        private val NULL_TARGET = Toggle.State.Target(null, null, null, null, null, null, null)
    }
}
