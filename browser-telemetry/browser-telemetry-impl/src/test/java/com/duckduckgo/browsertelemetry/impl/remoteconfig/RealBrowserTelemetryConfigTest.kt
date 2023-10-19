/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.browsertelemetry.impl.remoteconfig

import com.duckduckgo.browsertelemetry.api.BrowserTelemetryConfig
import com.duckduckgo.browsertelemetry.impl.RealBrowserTelemetryConfig
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealBrowserTelemetryConfigTest {

    @Mock
    private lateinit var mockBrowserTelemetryFeature: BrowserTelemetryFeature

    private lateinit var testee: BrowserTelemetryConfig

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealBrowserTelemetryConfig(mockBrowserTelemetryFeature)
    }

    @Test
    fun whenBrowserTelemetryNotEnabledAndAppLaunchNotEnabledThenShouldCollectOnAppLaunchIsFalse() {
        setupRemoteConfig(browserTelemetryEnabled = false, appLaunchEnabled = false)

        val result = testee.shouldCollectOnAppLaunch()

        assertFalse(result)
    }

    @Test
    fun whenBrowserTelemetryNotEnabledAndAppLaunchEnabledThenShouldCollectOnAppLaunchIsFalse() {
        setupRemoteConfig(browserTelemetryEnabled = false, appLaunchEnabled = true)

        val result = testee.shouldCollectOnAppLaunch()

        assertFalse(result)
    }

    @Test
    fun whenBrowserTelemetryEnabledAndAppLaunchNotEnabledThenShouldCollectOnAppLaunchIsFalse() {
        setupRemoteConfig(browserTelemetryEnabled = true, appLaunchEnabled = false)

        val result = testee.shouldCollectOnAppLaunch()

        assertFalse(result)
    }

    @Test
    fun whenBrowserTelemetryEnabledAndAppLaunchEnabledThenShouldCollectOnAppLaunchIsFalse() {
        setupRemoteConfig(browserTelemetryEnabled = true, appLaunchEnabled = true)

        val result = testee.shouldCollectOnAppLaunch()

        assertTrue(result)
    }

    private fun setupRemoteConfig(browserTelemetryEnabled: Boolean, appLaunchEnabled: Boolean) {
        whenever(mockBrowserTelemetryFeature.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return browserTelemetryEnabled
                }

                override fun setEnabled(state: Toggle.State) {
                    TODO("Not yet implemented")
                }

                override fun getRawStoredState(): Toggle.State? {
                    TODO("Not yet implemented")
                }
            },
        )

        whenever(mockBrowserTelemetryFeature.appLaunch()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return appLaunchEnabled
                }

                override fun setEnabled(state: Toggle.State) {
                    TODO("Not yet implemented")
                }

                override fun getRawStoredState(): Toggle.State? {
                    TODO("Not yet implemented")
                }
            },
        )
    }
}
