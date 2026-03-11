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

package com.duckduckgo.contentscopescripts.impl.features.messagebridge

import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeEntity
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeRepository
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MessageBridgeContentScopeConfigPluginTest {

    private lateinit var testee: MessageBridgeContentScopeConfigPlugin

    private val mockMessageBridgeRepository: MessageBridgeRepository = mock()
    private val mockDuckAiHostProvider: DuckAiHostProvider = mock()

    @Before
    fun before() {
        testee = MessageBridgeContentScopeConfigPlugin(mockMessageBridgeRepository, mockDuckAiHostProvider)
    }

    @Test
    fun whenGetConfigThenReturnCorrectlyFormattedJson() {
        whenever(mockMessageBridgeRepository.messageBridgeEntity).thenReturn(
            MessageBridgeEntity(json = CONFIG),
        )
        assertEquals("\"messageBridge\":$CONFIG", testee.config())
    }

    @Test
    fun whenGetPreferencesThenReturnNull() {
        assertNull(testee.preferences())
    }

    @Test
    fun whenCustomHostExistsThenItIsAddedOnlyToDomainArray() {
        val customHost = "custom.duck.ai"
        whenever(mockDuckAiHostProvider.getCustomHost()).thenReturn(customHost)
        whenever(mockMessageBridgeRepository.messageBridgeEntity).thenReturn(
            MessageBridgeEntity(json = DOMAIN_CONFIG),
        )

        val result = JSONObject("{${testee.config()}}").getJSONObject("messageBridge")
        val domains = result
            .getJSONObject("settings")
            .getJSONArray("domains")
            .getJSONObject(0)
            .getJSONArray("domain")

        assertEquals("duckduckgo.com", domains.getString(0))
        assertEquals("duck.ai", domains.getString(1))
        assertEquals(customHost, domains.getString(2))
        assertEquals("duck.ai", result.getJSONObject("settings").getString("fallback"))
    }

    @Test
    fun whenCustomHostContainsEscapingCharactersThenJsonRemainsValid() {
        val customHost = "custom\"host\\name"
        whenever(mockDuckAiHostProvider.getCustomHost()).thenReturn(customHost)
        whenever(mockMessageBridgeRepository.messageBridgeEntity).thenReturn(
            MessageBridgeEntity(json = SIMPLE_DOMAIN_CONFIG),
        )

        val result = JSONObject("{${testee.config()}}").getJSONObject("messageBridge")
        val domains = result.getJSONArray("domains")

        assertEquals("duck.ai", domains.getString(0))
        assertEquals(customHost, domains.getString(1))
    }

    companion object {
        const val CONFIG = "{\"key\":\"value\"}"
        const val DOMAIN_CONFIG = """{"settings":{"domains":[{"domain":["duckduckgo.com","duck.ai"]}],"fallback":"duck.ai"}}"""
        const val SIMPLE_DOMAIN_CONFIG = """{"domains":["duck.ai"]}"""
    }
}
