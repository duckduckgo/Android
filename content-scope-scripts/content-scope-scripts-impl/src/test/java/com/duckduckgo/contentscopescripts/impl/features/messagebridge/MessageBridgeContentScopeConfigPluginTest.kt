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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MessageBridgeContentScopeConfigPluginTest {

    private lateinit var testee: MessageBridgeContentScopeConfigPlugin

    private val mockMessageBridgeRepository: MessageBridgeRepository = mock()

    @Before
    fun before() {
        testee = MessageBridgeContentScopeConfigPlugin(mockMessageBridgeRepository)
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

    companion object {
        const val CONFIG = "{\"key\":\"value\"}"
    }
}
