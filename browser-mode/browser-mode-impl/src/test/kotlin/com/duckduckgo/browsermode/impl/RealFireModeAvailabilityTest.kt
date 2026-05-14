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

package com.duckduckgo.browsermode.impl

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.MultiProfile
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking

@SuppressLint("DenyListedApi")
class RealFireModeAvailabilityTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val fireModeFeature: FireModeFeature = FakeFeatureToggleFactory.create(FireModeFeature::class.java)
    private val webViewCapabilityChecker: WebViewCapabilityChecker = mock()

    private val testee = RealFireModeAvailability(fireModeFeature, webViewCapabilityChecker, coroutineRule.testDispatcherProvider)

    @Test
    fun `is unavailable when fireTabs flag is disabled`() = runTest {
        fireModeFeature.fireTabs().setRawStoredState(Toggle.State(enable = false))
        webViewCapabilityChecker.stub { onBlocking { isSupported(MultiProfile) }.thenReturn(true) }

        assertFalse(testee.isAvailable())
    }

    @Test
    fun `is unavailable when WebView does not support MultiProfile`() = runTest {
        fireModeFeature.fireTabs().setRawStoredState(Toggle.State(enable = true))
        webViewCapabilityChecker.stub { onBlocking { isSupported(MultiProfile) }.thenReturn(false) }

        assertFalse(testee.isAvailable())
    }

    @Test
    fun `is available when fireTabs is enabled and WebView supports MultiProfile`() = runTest {
        fireModeFeature.fireTabs().setRawStoredState(Toggle.State(enable = true))
        webViewCapabilityChecker.stub { onBlocking { isSupported(MultiProfile) }.thenReturn(true) }

        assertTrue(testee.isAvailable())
    }

    @Test
    fun `multi-profile capability check is cached across calls`() = runTest {
        fireModeFeature.fireTabs().setRawStoredState(Toggle.State(enable = true))
        webViewCapabilityChecker.stub { onBlocking { isSupported(MultiProfile) }.thenReturn(true) }

        repeat(5) { testee.isAvailable() }

        verifyBlocking(webViewCapabilityChecker, times(1)) { isSupported(MultiProfile) }
    }
}
