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

package com.duckduckgo.duckchat.impl.helper

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_PROMPT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.js.messaging.api.JsCallbackData
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDuckChatJSHelperTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDuckChat: DuckChatInternal = mock()
    private val mockDataStore: DuckChatDataStore = mock()
    private val mockDuckChatPixels: DuckChatPixels = mock()

    private val testee = RealDuckChatJSHelper(
        duckChat = mockDuckChat,
        dataStore = mockDataStore,
        duckChatPixels = mockDuckChatPixels,
    )

    @Test
    fun whenMethodIsUnknownThenReturnNull() = runTest {
        val featureName = "aiChat"
        val method = "unknownMethod"
        val id = "123"

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        assertNull(result)
    }

    @Test
    fun whenGetAIChatNativeHandoffDataAndIdIsNullThenReturnNull() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"

        val result = testee.processJsCallbackMessage(featureName, method, null, null)

        assertNull(result)
    }

    @Test
    fun whenGetAIChatNativeHandoffDataAndDuckChatEnabledThenReturnJsCallbackDataWithDuckChatEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        whenever(mockDataStore.fetchAndClearUserPreferences()).thenReturn("preferences")

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("aiChatPayload", "preferences")
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun whenGetAIChatNativeHandoffDataAndDuckChatDisabledThenReturnJsCallbackDataWithDuckChatDisabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(false)
        whenever(mockDataStore.fetchAndClearUserPreferences()).thenReturn("preferences")

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", false)
            put("aiChatPayload", "preferences")
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun whenGetAIChatNativeHandoffDataAndPreferencesNullThenReturnJsCallbackDataWithPreferencesNull() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        whenever(mockDataStore.fetchAndClearUserPreferences()).thenReturn(null)

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("aiChatPayload", null)
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndIdIsNullThenReturnNull() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"

        val result = testee.processJsCallbackMessage(featureName, method, null, null)

        assertNull(result)
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndDuckChatEnabledThenReturnJsCallbackDataWithDuckChatEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsImageUpload", false)
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndDuckChatDisabledThenReturnJsCallbackDataWithDuckChatDisabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(false)

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", false)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsImageUpload", false)
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun whenOpenAIChatAndHasPayloadThenUpdateStoreAndOpenDuckChat() = runTest {
        val featureName = "aiChat"
        val method = "openAIChat"
        val id = "123"
        val payload = JSONObject(mapOf("key" to "value"))
        val payloadString = payload.toString()
        val data = JSONObject(mapOf("aiChatPayload" to payloadString))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))

        verify(mockDataStore).updateUserPreferences(payloadString)
        verify(mockDuckChat).openNewDuckChatSession()
    }

    @Test
    fun whenOpenAIChatAndDataIsNullThenUpdateStoreAndOpenDuckChat() = runTest {
        val featureName = "aiChat"
        val method = "openAIChat"
        val id = "123"

        assertNull(testee.processJsCallbackMessage(featureName, method, id, null))
        verify(mockDataStore).updateUserPreferences(null)
        verify(mockDuckChat).openNewDuckChatSession()
    }

    @Test
    fun whenOpenAIChatAndPayloadIsNullThenUpdateStoreAndOpenDuckChat() = runTest {
        val featureName = "aiChat"
        val method = "openAIChat"
        val id = "123"
        val data = JSONObject(mapOf("aiChatPayload" to JSONObject.NULL))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))
        verify(mockDataStore).updateUserPreferences(null)
        verify(mockDuckChat).openNewDuckChatSession()
    }

    @Test
    fun whenStartStreamNewPromptResponseStateReceivedThenUpdateChatStateWithStartStreamNewPrompt() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "start_stream:new_prompt"))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))

        verify(mockDuckChat).updateChatState(ChatState.START_STREAM_NEW_PROMPT)
    }

    @Test
    fun whenLoadingResponseStateReceivedThenUpdateChatStateWithLoading() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "loading"))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))

        verify(mockDuckChat).updateChatState(ChatState.LOADING)
    }

    @Test
    fun whenStreamingResponseStateReceivedThenUpdateChatStateWithStreaming() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "streaming"))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))

        verify(mockDuckChat).updateChatState(ChatState.STREAMING)
    }

    @Test
    fun whenErrorResponseStateReceivedThenUpdateChatStateWithError() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "error"))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))

        verify(mockDuckChat).updateChatState(ChatState.ERROR)
    }

    @Test
    fun whenReadyResponseStateReceivedThenUpdateChatStateWithReady() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "ready"))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))

        verify(mockDuckChat).updateChatState(ChatState.READY)
    }

    @Test
    fun whenBlockedResponseStateReceivedThenUpdateChatStateWithBlocked() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "blocked"))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))

        verify(mockDuckChat).updateChatState(ChatState.BLOCKED)
    }

    @Test
    fun whenHideChatInputThenUpdateChatStateWithHide() = runTest {
        val featureName = "aiChat"
        val method = "hideChatInput"
        val id = "123"

        assertNull(testee.processJsCallbackMessage(featureName, method, id, null))

        verify(mockDuckChat).updateChatState(ChatState.HIDE)
    }

    @Test
    fun whenShowChatInputThenUpdateChatStateWithShow() = runTest {
        val featureName = "aiChat"
        val method = "showChatInput"
        val id = "123"

        assertNull(testee.processJsCallbackMessage(featureName, method, id, null))

        verify(mockDuckChat).updateChatState(ChatState.SHOW)
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndSupportsImageUploadThenReturnJsCallbackDataWithSupportsImageUploadEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        whenever(mockDuckChat.isImageUploadEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val expectedPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsImageUpload", true)
        }

        assertEquals(expectedPayload.toString(), result!!.params.toString())
    }

    @Test
    fun whenReportMetricWithoutDataThenPixelNotSent() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"

        assertNull(testee.processJsCallbackMessage(featureName, method, id, null))

        verifyNoInteractions(mockDuckChatPixels)
    }

    @Test
    fun whenReportMetricWithDataThenPixelSent() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"
        val data = JSONObject(mapOf("metricName" to "userDidSubmitPrompt"))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))

        verify(mockDuckChatPixels).sendReportMetricPixel(USER_DID_SUBMIT_PROMPT)
    }
}
