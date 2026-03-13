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

package com.duckduckgo.eventhub.impl.webevents

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WebEventsContentScopeConfigPluginTest {

    private val dataStore: WebEventsDataStore = mock()

    private val plugin = WebEventsContentScopeConfigPlugin(webEventsDataStore = dataStore)

    @Test
    fun `config returns correctly formatted JSON with feature name and config`() {
        whenever(dataStore.getWebEventsConfigJson()).thenReturn("""{"enabled":true}""")

        val result = plugin.config()

        assertTrue(result.contains("\"webEvents\""))
        assertTrue(result.contains("{\"enabled\":true}"))
    }

    @Test
    fun `config uses webEvents as feature name`() {
        whenever(dataStore.getWebEventsConfigJson()).thenReturn("{}")

        val result = plugin.config()

        assertTrue(result.startsWith("\"webEvents\":"))
    }

    @Test
    fun `preferences returns null`() {
        assertNull(plugin.preferences())
    }
}
