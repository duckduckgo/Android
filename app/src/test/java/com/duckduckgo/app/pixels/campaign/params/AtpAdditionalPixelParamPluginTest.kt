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

package com.duckduckgo.app.pixels.campaign.params

import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class AtpAdditionalPixelParamPluginTest {
    @Test
    fun whenUserIsOnboardedToAppTPThenPluginShouldReturnParamTrue() = runTest {
        val appTp: AppTrackingProtection = mock()
        whenever(appTp.isOnboarded()).thenReturn(true)
        val plugin = AtpEnabledAdditionalPixelParamPlugin(appTp)

        assertEquals("atpOnboarded" to "true", plugin.params())
    }

    @Test
    fun whenUserIsNotOnboardedToAppTPThenPluginShouldReturnParamFalse() = runTest {
        val appTp: AppTrackingProtection = mock()
        whenever(appTp.isOnboarded()).thenReturn(false)
        val plugin = AtpEnabledAdditionalPixelParamPlugin(appTp)

        assertEquals("atpOnboarded" to "false", plugin.params())
    }
}
