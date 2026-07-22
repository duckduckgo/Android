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

package com.duckduckgo.app.accessibility

import app.cash.turbine.test
import com.duckduckgo.app.accessibility.data.AccessibilitySettings
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityRefreshTriggerPluginTest {

    private val settingsFlow = MutableStateFlow(AccessibilitySettings(overrideSystemFontSize = false, fontSize = 100f, forceZoom = false))
    private val accessibilitySettings: AccessibilitySettingsDataStore = mock {
        on { settingsFlow() } doReturn settingsFlow
    }
    private val plugin = AccessibilityRefreshTriggerPlugin(accessibilitySettings)

    @Test
    fun whenSubscribedAndNoChangeThenDoesNotEmitForCurrentSettings() = runTest {
        plugin.observeRefreshRequests().test {
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun whenFontSizeChangesThenEmits() = runTest {
        plugin.observeRefreshRequests().test {
            settingsFlow.value = settingsFlow.value.copy(fontSize = 140f)

            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenForceZoomChangesThenEmits() = runTest {
        plugin.observeRefreshRequests().test {
            settingsFlow.value = settingsFlow.value.copy(forceZoom = true)

            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSettingsRepeatSameValueThenDoesNotEmit() = runTest {
        plugin.observeRefreshRequests().test {
            settingsFlow.value = settingsFlow.value.copy()

            expectNoEvents()
            cancel()
        }
    }
}
