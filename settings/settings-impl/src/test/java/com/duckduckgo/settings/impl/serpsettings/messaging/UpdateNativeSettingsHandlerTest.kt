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

package com.duckduckgo.settings.impl.serpsettings.messaging

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.settings.api.SettingsPageFeature
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class UpdateNativeSettingsHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val handler = UpdateNativeSettingsHandler(
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        appScope = coroutineTestRule.testScope,
        settingsPageFeature = mock<SettingsPageFeature>(),
    ).getJsMessageHandler()

    @Test
    fun `only allow duckduckgo dot com domains`() {
        val domains = handler.allowedDomains
        assertEquals(1, domains.size)
        assertEquals("duckduckgo.com", domains.first())
    }

    @Test
    fun `feature name is serpSettings`() {
        assertEquals("serpSettings", handler.featureName)
    }

    @Test
    fun `only contains updateNativeSettings method`() {
        val methods = handler.methods
        assertEquals(1, methods.size)
        assertEquals("updateNativeSettings", methods[0])
    }
}
