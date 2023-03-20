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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StateMessageHandlerPluginTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: StateMessageHandlerPlugin

    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockResponseListener: ResponseListener = mock()

    @Before
    fun before() {
        testee = StateMessageHandlerPlugin(mockAppBuildConfig, TestScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenMessageIsNotSupportedThenDoNothing() {
        testee.process("unsupportedType", "", mockResponseListener)
        verifyNoInteractions(mockResponseListener)
    }

    @Test
    fun whenMessageIsSupportedThenReturnResponse() {
        testee.process("getClickToLoadState", "", mockResponseListener)
        val messageCaptor = argumentCaptor<String>()
        verify(mockResponseListener).onResponse(messageCaptor.capture())
        assertEquals(
            messageCaptor.firstValue,
            "{\"feature\":\"clickToLoad\",\"messageType\":\"response\",\"response\":\"{ \\\"devMode\\\": false, " +
                "\\\"youtubePreviewsEnabled\\\": false }\",\"responseMessageType\":\"getClickToLoadState\",\"type\":\"update\"}",
        )
    }

    @Test
    fun whenMessageIsSupportedIsNotDebugThenReturnResponseWithDevModeSetToTrue() {
        whenever(mockAppBuildConfig.isDebug).thenReturn(true)
        testee.process("getClickToLoadState", "", mockResponseListener)
        val messageCaptor = argumentCaptor<String>()
        verify(mockResponseListener).onResponse(messageCaptor.capture())
        assertEquals(
            messageCaptor.firstValue,
            "{\"feature\":\"clickToLoad\",\"messageType\":\"response\",\"response\":\"{ \\\"devMode\\\": true, " +
                "\\\"youtubePreviewsEnabled\\\": false }\",\"responseMessageType\":\"getClickToLoadState\",\"type\":\"update\"}",
        )
    }
}
