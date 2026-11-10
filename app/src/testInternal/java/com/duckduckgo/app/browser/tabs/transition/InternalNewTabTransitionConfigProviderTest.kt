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
package com.duckduckgo.app.browser.tabs.transition

import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class InternalNewTabTransitionConfigProviderTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val tabManagerFeatureFlags = FakeFeatureToggleFactory.create(TabManagerFeatureFlags::class.java)
    private val tunedConfig = NewTabTransitionConfig.DEFAULT.copy(tabManagerOrigin = NewTabOrigin.RIGHT_OF_CENTER, slowMotion = true)
    private val store = object : NewTabTransitionDevStore {
        override var config = tunedConfig
    }

    private val testee = InternalNewTabTransitionConfigProvider(tabManagerFeatureFlags, store, coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenFlagDisabledThenEnabledConfigIsNull() = runTest {
        tabManagerFeatureFlags.newTabTransitions().setRawStoredState(State(enable = false))

        assertNull(testee.enabledConfig())
    }

    @Test
    fun whenFlagEnabledThenEnabledConfigIsTheStoredConfig() = runTest {
        tabManagerFeatureFlags.newTabTransitions().setRawStoredState(State(enable = true))

        assertEquals(tunedConfig, testee.enabledConfig())
    }
}
