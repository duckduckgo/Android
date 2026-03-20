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

package com.duckduckgo.duckchat.internal

import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeEntity
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InternalMessageBridgeContentScopeConfigPluginTest {

    private val mockMessageBridgeRepository: MessageBridgeRepository = mock()
    private val mockDuckAiHostProvider: InternalDuckAiHostProvider = mock()

    private val testee = InternalMessageBridgeContentScopeConfigPlugin(
        mockMessageBridgeRepository,
        mockDuckAiHostProvider,
    )

    @Test
    fun whenNoCustomUrlThenConfigIsUnmodified() {
        whenever(mockDuckAiHostProvider.getCustomUrl()).thenReturn(null)
        whenever(mockMessageBridgeRepository.messageBridgeEntity).thenReturn(
            MessageBridgeEntity(json = CONFIG),
        )

        assertEquals("\"messageBridge\":$CONFIG", testee.config())
    }

    @Test
    fun whenCustomUrlSetThenConfigIncludesCustomHost() {
        whenever(mockDuckAiHostProvider.getCustomUrl()).thenReturn("https://staging.duck.ai")
        whenever(mockDuckAiHostProvider.getHost()).thenReturn("staging.duck.ai")
        whenever(mockMessageBridgeRepository.messageBridgeEntity).thenReturn(
            MessageBridgeEntity(json = CONFIG_WITH_DUCK_AI),
        )

        val result = testee.config()

        assertEquals("\"messageBridge\":{\"domains\":[\"duck.ai\",\"staging.duck.ai\"]}", result)
    }

    @Test
    fun whenGetPreferencesThenReturnNull() {
        assertNull(testee.preferences())
    }

    companion object {
        const val CONFIG = "{\"key\":\"value\"}"
        const val CONFIG_WITH_DUCK_AI = "{\"domains\":[\"duck.ai\"]}"
    }
}
