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

package com.duckduckgo.duckchat.store.impl.bridge

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealMessageBridgeTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val messageBridgeFeature: MessageBridgeFeature = mock()
    private val selfToggle: Toggle = mock()
    private lateinit var bridge: RealMessageBridge

    @Before
    fun setup() {
        whenever(messageBridgeFeature.self()).thenReturn(selfToggle)
        bridge = RealMessageBridge(messageBridgeFeature, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun `returns false when settings is null`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(null)
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns false when settings json is malformed`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn("not valid json {{{")
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns true when top-level value is enabled and no patches`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """{ "duckAiNativeStorage": "enabled" }""",
        )
        assertTrue(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns false when top-level value is disabled and no patches`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """{ "duckAiNativeStorage": "disabled" }""",
        )
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns false when duckAiNativeStorage key is absent`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn("""{ "aiChat": "enabled" }""")
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns true when top-level disabled but duck-ai domain patch enables it`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "disabled",
              "domains": [
                {
                  "domain": ["duck.ai"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "enabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns false when top-level enabled but duck-ai domain patch disables it`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "enabled",
              "domains": [
                {
                  "domain": ["duck.ai"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "disabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `ignores patches for other keys in duck-ai domain entry`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "enabled",
              "domains": [
                {
                  "domain": ["duck.ai"],
                  "patchSettings": [
                    { "op": "replace", "path": "/aiChat", "value": "disabled" },
                    { "op": "replace", "path": "/serpSettings", "value": "disabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns false when multiple duck-ai domain entries each patch duckAiNativeStorage`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "disabled",
              "domains": [
                {
                  "domain": ["duck.ai"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "enabled" }
                  ]
                },
                {
                  "domain": ["duck.ai", "duckduckgo.com"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "enabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns false when same duck-ai domain entry has two patches for duckAiNativeStorage`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "disabled",
              "domains": [
                {
                  "domain": ["duck.ai"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "enabled" },
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "disabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns true when duck-ai domain patch enables and other domain patch disables`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "disabled",
              "domains": [
                {
                  "domain": ["duck.ai"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "enabled" }
                  ]
                },
                {
                  "domain": ["duckduckgo.com"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "disabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns false when duck-ai domain patch disables and other domain patch enables`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "enabled",
              "domains": [
                {
                  "domain": ["duck.ai"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "disabled" }
                  ]
                },
                {
                  "domain": ["duckduckgo.com"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "enabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `ignores patch from non-duck-ai domain entry`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "disabled",
              "domains": [
                {
                  "domain": ["duckduckgo.com"],
                  "patchSettings": [
                    { "op": "replace", "path": "/duckAiNativeStorage", "value": "enabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertFalse(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns true when duck-ai domain entry has no patchSettings for duckAiNativeStorage`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "enabled",
              "domains": [
                {
                  "domain": ["duck.ai"],
                  "patchSettings": [
                    { "op": "replace", "path": "/aiChat", "value": "enabled" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(bridge.isDuckAiNativeStorageFeatureEnabled())
    }

    @Test
    fun `returns true when domains array is empty`() = runTest {
        whenever(selfToggle.getSettings()).thenReturn(
            """
            {
              "duckAiNativeStorage": "enabled",
              "domains": []
            }
            """.trimIndent(),
        )
        assertTrue(bridge.isDuckAiNativeStorageFeatureEnabled())
    }
}
