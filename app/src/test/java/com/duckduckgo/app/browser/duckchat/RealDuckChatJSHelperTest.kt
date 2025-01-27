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

package com.duckduckgo.app.browser.duckchat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.commands.Command.SendResponseToJs
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
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
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDuckChatJSHelperTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDuckChat: DuckChat = mock()
    private val mockPreferencesStore: DuckChatPreferencesStore = mock()

    private val testee = RealDuckChatJSHelper(
        duckChat = mockDuckChat,
        preferencesStore = mockPreferencesStore,
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
    fun whenGetAIChatNativeHandoffDataAndDuckChatEnabledThenReturnSendResponseToJsWithDuckChatEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        whenever(mockPreferencesStore.fetchAndClearUserPreferences()).thenReturn("preferences")

        val result = testee.processJsCallbackMessage(featureName, method, id, null) as SendResponseToJs

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("aiChatPayload", "preferences")
        }

        val expected = SendResponseToJs(JsCallbackData(jsonPayload, featureName, method, id))

        assertEquals(expected.data.id, result.data.id)
        assertEquals(expected.data.method, result.data.method)
        assertEquals(expected.data.featureName, result.data.featureName)
        assertEquals(expected.data.params.toString(), result.data.params.toString())
    }

    @Test
    fun whenGetAIChatNativeHandoffDataAndDuckChatDisabledThenReturnSendResponseToJsWithDuckChatDisabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(false)
        whenever(mockPreferencesStore.fetchAndClearUserPreferences()).thenReturn("preferences")

        val result = testee.processJsCallbackMessage(featureName, method, id, null) as SendResponseToJs

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", false)
            put("aiChatPayload", "preferences")
        }

        val expected = SendResponseToJs(JsCallbackData(jsonPayload, featureName, method, id))

        assertEquals(expected.data.id, result.data.id)
        assertEquals(expected.data.method, result.data.method)
        assertEquals(expected.data.featureName, result.data.featureName)
        assertEquals(expected.data.params.toString(), result.data.params.toString())
    }

    @Test
    fun whenGetAIChatNativeHandoffDataAndPreferencesNullThenReturnSendResponseToJsWithPreferencesNull() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        whenever(mockPreferencesStore.fetchAndClearUserPreferences()).thenReturn(null)

        val result = testee.processJsCallbackMessage(featureName, method, id, null) as SendResponseToJs

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("aiChatPayload", null)
        }

        val expected = SendResponseToJs(JsCallbackData(jsonPayload, featureName, method, id))

        assertEquals(expected.data.id, result.data.id)
        assertEquals(expected.data.method, result.data.method)
        assertEquals(expected.data.featureName, result.data.featureName)
        assertEquals(expected.data.params.toString(), result.data.params.toString())
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndIdIsNullThenReturnNull() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"

        val result = testee.processJsCallbackMessage(featureName, method, null, null)

        assertNull(result)
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndDuckChatEnabledThenReturnSendResponseToJsWithDuckChatEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        whenever(mockPreferencesStore.fetchAndClearUserPreferences()).thenReturn("preferences")

        val result = testee.processJsCallbackMessage(featureName, method, id, null) as SendResponseToJs

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
        }

        val expected = SendResponseToJs(JsCallbackData(jsonPayload, featureName, method, id))

        assertEquals(expected.data.id, result.data.id)
        assertEquals(expected.data.method, result.data.method)
        assertEquals(expected.data.featureName, result.data.featureName)
        assertEquals(expected.data.params.toString(), result.data.params.toString())
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndDuckChatDisabledThenReturnSendResponseToJsWithDuckChatDisabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(false)
        whenever(mockPreferencesStore.fetchAndClearUserPreferences()).thenReturn("preferences")

        val result = testee.processJsCallbackMessage(featureName, method, id, null) as SendResponseToJs

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", false)
        }

        val expected = SendResponseToJs(JsCallbackData(jsonPayload, featureName, method, id))

        assertEquals(expected.data.id, result.data.id)
        assertEquals(expected.data.method, result.data.method)
        assertEquals(expected.data.featureName, result.data.featureName)
        assertEquals(expected.data.params.toString(), result.data.params.toString())
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndPreferencesNullThenReturnSendResponseToJsWithPreferencesNull() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        whenever(mockPreferencesStore.fetchAndClearUserPreferences()).thenReturn(null)

        val result = testee.processJsCallbackMessage(featureName, method, id, null) as SendResponseToJs

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("aiChatPayload", null)
        }

        val expected = SendResponseToJs(JsCallbackData(jsonPayload, featureName, method, id))

        assertEquals(expected.data.id, result.data.id)
        assertEquals(expected.data.method, result.data.method)
        assertEquals(expected.data.featureName, result.data.featureName)
        assertEquals(expected.data.params.toString(), result.data.params.toString())
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

        verify(mockPreferencesStore).updateUserPreferences(payloadString)
        verify(mockDuckChat).openDuckChat()
    }

    @Test
    fun whenOpenAIChatAndDataIsNullThenUpdateStoreAndOpenDuckChat() = runTest {
        val featureName = "aiChat"
        val method = "openAIChat"
        val id = "123"

        assertNull(testee.processJsCallbackMessage(featureName, method, id, null))
        verify(mockPreferencesStore).updateUserPreferences(null)
        verify(mockDuckChat).openDuckChat()
    }

    @Test
    fun whenOpenAIChatAndPayloadIsNullThenUpdateStoreAndOpenDuckChat() = runTest {
        val featureName = "aiChat"
        val method = "openAIChat"
        val id = "123"
        val data = JSONObject(mapOf("aiChatPayload" to JSONObject.NULL))

        assertNull(testee.processJsCallbackMessage(featureName, method, id, data))
        verify(mockPreferencesStore).updateUserPreferences(null)
        verify(mockDuckChat).openDuckChat()
    }
}
