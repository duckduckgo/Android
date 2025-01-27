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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MessageBridgeFeaturePluginTest {
    private lateinit var testee: MessageBridgeFeaturePlugin

    private val mockMessageBridgeRepository: MessageBridgeRepository = mock()

    @Before
    fun before() {
        testee = MessageBridgeFeaturePlugin(mockMessageBridgeRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchMessageBridgeThenReturnFalse() {
        MessageBridgeFeatureName.entries.filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesMessageBridgeThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesMessageBridgeThenUpdateAll() {
        testee.store(FEATURE_NAME_VALUE, JSON_STRING)
        val captor = argumentCaptor<MessageBridgeEntity>()
        verify(mockMessageBridgeRepository).updateAll(captor.capture())
        assertEquals(JSON_STRING, captor.firstValue.json)
    }

    companion object {
        private val FEATURE_NAME = MessageBridgeFeatureName.MessageBridge
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val JSON_STRING = "{\"key\":\"value\"}"
    }
}
