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

package com.duckduckgo.clicktoload.impl.handlers

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.contentscopescripts.api.ResponseListener
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class UnblockMessageHandlerPluginTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: UnblockMessageHandlerPlugin

    private val mockResponseListener: ResponseListener = mock()

    @Before
    fun before() {
        testee = UnblockMessageHandlerPlugin(TestScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenMessageIsNotSupportedThenDoNothing() {
        testee.process("unsupportedType", "", mockResponseListener)
        verifyNoInteractions(mockResponseListener)
    }

    @Test
    fun whenMessageIsSupportedThenReturnResponse() {
        testee.process("unblockClickToLoadContent", "", mockResponseListener)
        val messageCaptor = argumentCaptor<String>()
        verify(mockResponseListener).onResponse(messageCaptor.capture())
        assertEquals(
            "{\"feature\":\"clickToLoad\",\"messageType\":\"response\",\"response\":\"[]\"," +
                "\"responseMessageType\":\"unblockClickToLoadContent\",\"type\":\"update\"}",
            messageCaptor.firstValue,
        )
    }
}
