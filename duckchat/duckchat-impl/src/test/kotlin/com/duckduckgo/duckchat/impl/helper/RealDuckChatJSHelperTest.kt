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

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_CREATE_NEW_CHAT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_OPEN_HISTORY
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SELECT_FIRST_HISTORY_ITEM
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_FIRST_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_TAP_KEYBOARD_RETURN_KEY
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.METHOD_GET_PAGE_CONTEXT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.js.messaging.api.JsCallbackData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RealDuckChatJSHelperTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDuckChat: DuckChatInternal = mock()
    private val mockDataStore: DuckChatDataStore = mock()
    private val mockDuckChatPixels: DuckChatPixels = mock()
    private val mockFaviconManager: com.duckduckgo.app.browser.favicon.FaviconManager = mock()
    private val testee = RealDuckChatJSHelper(
        duckChat = mockDuckChat,
        duckChatPixels = mockDuckChatPixels,
        dataStore = mockDataStore,
        faviconManager = mockFaviconManager,
        appCoroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )
    private val viewModel =
        object {
            val updatedPageContext: String =
                """
                {
                    "title": "Example Title",
                    "url": "https://example.com",
                    "content": "Example content"
                }
                """.trimIndent()
        }

    @Test
    fun whenMethodIsUnknownThenReturnNull() = runTest {
        val featureName = "aiChat"
        val method = "unknownMethod"
        val id = "123"

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        assertNull(result)
    }

    @Test
    fun whenGetAIChatNativeHandoffDataAndIdIsNullThenReturnNull() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            null,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        assertNull(result)
    }

    @Test
    fun whenGetAIChatNativeHandoffDataAndDuckChatFeatureEnabledThenReturnJsCallbackDataWithDuckChatEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"
        val id = "123"

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(true)
        whenever(mockDataStore.fetchAndClearUserPreferences()).thenReturn("preferences")

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

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
    fun whenGetAIChatNativeHandoffDataAndDuckChatFeatureDisabledThenReturnJsCallbackDataWithDuckChatDisabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"
        val id = "123"

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(false)
        whenever(mockDataStore.fetchAndClearUserPreferences()).thenReturn("preferences")

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

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

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(true)
        whenever(mockDataStore.fetchAndClearUserPreferences()).thenReturn(null)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

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

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            null,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        assertNull(result)
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndDuckChatFeatureEnabledThenReturnJsCallbackDataWithDuckChatEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(true)
        whenever(mockDuckChat.isDuckChatFullScreenModeEnabled()).thenReturn(false)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsURLChatIDRestoration", false)
            put("supportsImageUpload", false)
            put("supportsStandaloneMigration", false)
            put("supportsAIChatFullMode", false)
            put("supportsAIChatContextualMode", false)
            put("supportsAIChatSync", false)
            put("supportsPageContext", false)
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun whenGetPageContextInitAndAutomaticEnabledAndUserEnabledThenReturnsNull() = runTest {
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)
        val result =
            testee.processJsCallbackMessage(
                featureName = DUCK_CHAT_FEATURE_NAME,
                method = METHOD_GET_PAGE_CONTEXT,
                id = "123",
                data = JSONObject().apply { put("reason", "init") },
                mode = Mode.CONTEXTUAL,
                pageContext = viewModel.updatedPageContext,
            )

        assertNull(result)
    }

    @Test
    fun whenGetPageContextInitAndAutomaticDisabledThenReturnsNull() = runTest {
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(false)

        val result =
            testee.processJsCallbackMessage(
                featureName = DUCK_CHAT_FEATURE_NAME,
                method = METHOD_GET_PAGE_CONTEXT,
                id = "123",
                data = JSONObject().apply { put("reason", "init") },
                mode = Mode.CONTEXTUAL,
                pageContext = viewModel.updatedPageContext,
            )

        assertNull(result)
    }

    @Test
    fun whenGetPageContextUserActionThenReturnsContextRegardlessOfAutoFlag() = runTest {
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(false)
        val tabId = "tab-1"
        val faviconBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        whenever(mockFaviconManager.loadFromDisk(tabId, "https://example.com")).thenReturn(faviconBitmap)

        val result =
            testee.processJsCallbackMessage(
                featureName = DUCK_CHAT_FEATURE_NAME,
                method = METHOD_GET_PAGE_CONTEXT,
                id = "123",
                data = JSONObject().apply { put("reason", "userAction") },
                mode = Mode.CONTEXTUAL,
                pageContext = viewModel.updatedPageContext,
                tabId = tabId,
            )

        assertNotNull(result)
        val context = result!!.params.getJSONObject("pageContext")
        assertEquals("Example Title", context.getString("title"))
        val faviconArray = context.getJSONArray("favicon")
        val faviconObject = faviconArray.getJSONObject(0)
        assertEquals("icon", faviconObject.getString("rel"))
        val faviconHref = faviconObject.getString("href")
        assertEquals(true, faviconHref.startsWith("data:image/png;base64,"))
        verify(mockDuckChatPixels).reportContextualPageContextManuallyAttachedFrontend()
    }

    @Test
    fun whenGetPageContextWithoutDataThenReturnsNull() = runTest {
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        val result =
            testee.processJsCallbackMessage(
                featureName = DUCK_CHAT_FEATURE_NAME,
                method = METHOD_GET_PAGE_CONTEXT,
                id = "123",
                data = JSONObject().apply { put("reason", "userAction") },
                mode = Mode.CONTEXTUAL,
                pageContext = "",
            )

        assertNull(result)
    }

    @Test
    fun whenGetPageContextAndIdIsNullThenReturnsNull() = runTest {
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        val result =
            testee.processJsCallbackMessage(
                featureName = DUCK_CHAT_FEATURE_NAME,
                method = METHOD_GET_PAGE_CONTEXT,
                id = null,
                data = JSONObject().apply { put("reason", "userAction") },
                mode = Mode.CONTEXTUAL,
                pageContext = viewModel.updatedPageContext,
            )

        assertNull(result)
    }

    @Test
    fun whenGetPageContextInitAutomaticEnabledButUserDisabledThenReturnsNull() = runTest {
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        val result =
            testee.processJsCallbackMessage(
                featureName = DUCK_CHAT_FEATURE_NAME,
                method = METHOD_GET_PAGE_CONTEXT,
                id = "123",
                data = JSONObject().apply { put("reason", "init") },
                mode = Mode.CONTEXTUAL,
                pageContext = viewModel.updatedPageContext,
            )

        assertNull(result)
    }

    @Test
    fun whenGetPageContextWithoutReasonThenDefaultsToUserAction() = runTest {
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        val result =
            testee.processJsCallbackMessage(
                featureName = DUCK_CHAT_FEATURE_NAME,
                method = METHOD_GET_PAGE_CONTEXT,
                id = "123",
                data = null,
                mode = Mode.CONTEXTUAL,
                pageContext = viewModel.updatedPageContext,
            )

        assertNotNull(result)
        val context = result!!.params.getJSONObject("pageContext")
        assertEquals("Example Title", context.getString("title"))
        assertEquals("https://example.com", context.getString("url"))
    }

    @Test
    fun whenGetPageContextWithUnknownReasonThenReturnsNull() = runTest {
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        val result =
            testee.processJsCallbackMessage(
                featureName = DUCK_CHAT_FEATURE_NAME,
                method = METHOD_GET_PAGE_CONTEXT,
                id = "123",
                data = JSONObject().apply { put("reason", "unexpected") },
                mode = Mode.CONTEXTUAL,
                pageContext = viewModel.updatedPageContext,
            )

        assertNull(result)
    }

    @Test
    fun whenTogglePageContextEnabledThenNoPixelReported() = runTest {
        val featureName = "aiChat"
        val method = "togglePageContextTelemetry"
        val id = "123"
        val data = JSONObject(mapOf("enabled" to true))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verifyNoInteractions(mockDuckChatPixels)
    }

    @Test
    fun whenTogglePageContextDisabledThenReportContextRemoved() = runTest {
        val featureName = "aiChat"
        val method = "togglePageContextTelemetry"
        val id = "123"
        val data = JSONObject(mapOf("enabled" to false))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChatPixels).reportContextualPageContextRemovedFrontend()
    }

    @Test
    fun whenTogglePageContextWithoutDataThenNoPixelReported() = runTest {
        val featureName = "aiChat"
        val method = "togglePageContextTelemetry"
        val id = "123"

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                null,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verifyNoInteractions(mockDuckChatPixels)
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndDuckChatFeatureDisabledThenReturnJsCallbackDataWithDuckChatDisabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(false)
        whenever(mockDuckChat.isDuckChatFullScreenModeEnabled()).thenReturn(false)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", false)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsURLChatIDRestoration", false)
            put("supportsImageUpload", false)
            put("supportsStandaloneMigration", false)
            put("supportsAIChatFullMode", false)
            put("supportsAIChatContextualMode", false)
            put("supportsAIChatSync", false)
            put("supportsPageContext", false)
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndDuckChatFeatureEnabledAndFullScreenModeEnabledAndModeFullThenReturnCorrectData() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(true)
        whenever(mockDuckChat.isDuckChatFullScreenModeEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsURLChatIDRestoration", true)
            put("supportsImageUpload", false)
            put("supportsStandaloneMigration", false)
            put("supportsAIChatFullMode", true)
            put("supportsAIChatContextualMode", false)
            put("supportsAIChatSync", false)
            put("supportsPageContext", false)
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun `when get AI chat page context for user action then return payload`() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatPageContext"
        val id = "123"
        val data = JSONObject(mapOf("reason" to "userAction"))

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            data,
            pageContext = viewModel.updatedPageContext,
        )

        val expectedPayload = JSONObject().apply {
            put("pageContext", JSONObject(viewModel.updatedPageContext))
        }

        assertNotNull(result)
        assertEquals(expectedPayload.toString(), result!!.params.toString())
    }

    @Test
    fun `when get AI chat page context for init without automatic attachment then return null`() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatPageContext"
        val id = "123"
        val data = JSONObject(mapOf("reason" to "init"))
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(false)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            data,
            pageContext = viewModel.updatedPageContext,
        )

        assertNull(result)
    }

    @Test
    fun `when get AI chat page context for init with automatic attachment then return null`() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatPageContext"
        val id = "123"
        val data = JSONObject(mapOf("reason" to "init"))
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            data,
            pageContext = viewModel.updatedPageContext,
        )

        assertNull(result)
    }

    @Test
    fun `when get AI chat page context for init with automatic attachment but user disabled context then return payload`() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatPageContext"
        val id = "123"
        val data = JSONObject(mapOf("reason" to "init"))
        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            data,
            pageContext = viewModel.updatedPageContext,
        )

        val expectedPayload = JSONObject().apply {
            put("pageContext", JSONObject(viewModel.updatedPageContext))
        }

        assertNull(result)
    }

    @Test
    fun `when get AI chat page context without context then return null`() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatPageContext"
        val id = "123"
        val data = JSONObject(mapOf("reason" to "userAction"))

        whenever(mockDuckChat.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            data,
            pageContext = "",
        )

        assertNull(result)
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndDuckChatFeatureAndContextualModeEnabledAndModeContextualThenCorrectData() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(true)
        whenever(mockDuckChat.isDuckChatContextualModeEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            Mode.CONTEXTUAL,
            viewModel.updatedPageContext,
        )

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsURLChatIDRestoration", false)
            put("supportsImageUpload", false)
            put("supportsStandaloneMigration", false)
            put("supportsAIChatFullMode", false)
            put("supportsAIChatContextualMode", true)
            put("supportsAIChatSync", false)
            put("supportsPageContext", true)
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAnStandaloneMigrationEnabledThenReturnJsCallbackDataWithCorrectData() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isStandaloneMigrationEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        val jsonPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", false)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsURLChatIDRestoration", false)
            put("supportsImageUpload", false)
            put("supportsStandaloneMigration", true)
            put("supportsAIChatFullMode", false)
            put("supportsAIChatContextualMode", false)
            put("supportsAIChatSync", false)
            put("supportsPageContext", false)
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

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDataStore).updateUserPreferences(payloadString)
        verify(mockDuckChat).openNewDuckChatSession()
    }

    @Test
    fun whenOpenAIChatAndDataIsNullThenUpdateStoreAndOpenDuckChat() = runTest {
        val featureName = "aiChat"
        val method = "openAIChat"
        val id = "123"

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                null,
                pageContext = viewModel.updatedPageContext,
            ),
        )
        verify(mockDataStore).updateUserPreferences(null)
        verify(mockDuckChat).openNewDuckChatSession()
    }

    @Test
    fun whenOpenAIChatAndPayloadIsNullThenUpdateStoreAndOpenDuckChat() = runTest {
        val featureName = "aiChat"
        val method = "openAIChat"
        val id = "123"
        val data = JSONObject(mapOf("aiChatPayload" to JSONObject.NULL))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )
        verify(mockDataStore).updateUserPreferences(null)
        verify(mockDuckChat).openNewDuckChatSession()
    }

    @Test
    fun whenStartStreamNewPromptResponseStateReceivedThenUpdateChatStateWithStartStreamNewPrompt() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "start_stream:new_prompt"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChat).updateChatState(ChatState.START_STREAM_NEW_PROMPT)
    }

    @Test
    fun whenLoadingResponseStateReceivedThenUpdateChatStateWithLoading() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "loading"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChat).updateChatState(ChatState.LOADING)
    }

    @Test
    fun whenStreamingResponseStateReceivedThenUpdateChatStateWithStreaming() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "streaming"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChat).updateChatState(ChatState.STREAMING)
    }

    @Test
    fun whenErrorResponseStateReceivedThenUpdateChatStateWithError() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "error"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChat).updateChatState(ChatState.ERROR)
    }

    @Test
    fun whenReadyResponseStateReceivedThenUpdateChatStateWithReady() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "ready"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChat).updateChatState(ChatState.READY)
    }

    @Test
    fun whenBlockedResponseStateReceivedThenUpdateChatStateWithBlocked() = runTest {
        val featureName = "aiChat"
        val method = "responseState"
        val id = "123"
        val data = JSONObject(mapOf("status" to "blocked"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChat).updateChatState(ChatState.BLOCKED)
    }

    @Test
    fun whenHideChatInputThenUpdateChatStateWithHide() = runTest {
        val featureName = "aiChat"
        val method = "hideChatInput"
        val id = "123"

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                null,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChat).updateChatState(ChatState.HIDE)
    }

    @Test
    fun whenShowChatInputThenUpdateChatStateWithShow() = runTest {
        val featureName = "aiChat"
        val method = "showChatInput"
        val id = "123"

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                null,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChat).updateChatState(ChatState.SHOW)
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndSupportsImageUploadThenReturnJsCallbackDataWithSupportsImageUploadEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(true)
        whenever(mockDuckChat.isImageUploadEnabled()).thenReturn(true)
        whenever(mockDuckChat.isDuckChatFullScreenModeEnabled()).thenReturn(false)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        val expectedPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsURLChatIDRestoration", false)
            put("supportsImageUpload", true)
            put("supportsStandaloneMigration", false)
            put("supportsAIChatFullMode", false)
            put("supportsAIChatContextualMode", false)
            put("supportsAIChatSync", false)
            put("supportsPageContext", false)
        }

        assertEquals(expectedPayload.toString(), result!!.params.toString())
    }

    @Test
    fun whenGetAIChatNativeConfigValuesAndChatSyncEnabledThenReturnJsCallbackDataWithSupportsAIChatSyncEnabled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeConfigValues"
        val id = "123"

        whenever(mockDuckChat.isDuckChatFeatureEnabled()).thenReturn(true)
        whenever(mockDuckChat.isDuckChatFullScreenModeEnabled()).thenReturn(false)
        whenever(mockDuckChat.isChatSyncFeatureEnabled()).thenReturn(true)

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            null,
            pageContext = viewModel.updatedPageContext,
        )

        val expectedPayload = JSONObject().apply {
            put("platform", "android")
            put("isAIChatHandoffEnabled", true)
            put("supportsClosingAIChat", true)
            put("supportsOpeningSettings", true)
            put("supportsNativeChatInput", false)
            put("supportsURLChatIDRestoration", false)
            put("supportsImageUpload", false)
            put("supportsStandaloneMigration", false)
            put("supportsAIChatFullMode", false)
            put("supportsAIChatContextualMode", false)
            put("supportsAIChatSync", true)
            put("supportsPageContext", false)
        }

        assertEquals(expectedPayload.toString(), result!!.params.toString())
    }

    @Test
    fun whenReportMetricWithoutDataThenPixelNotSent() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                null,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verifyNoInteractions(mockDuckChatPixels)
    }

    @Test
    fun whenReportMetricWithDataThenPixelSentAndCollectMetric() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"
        val data = JSONObject(mapOf("metricName" to "userDidSubmitPrompt"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChatPixels).sendReportMetricPixel(USER_DID_SUBMIT_PROMPT)
    }

    @Test
    fun whenReportMetricWithFirstPromptThenPixelSentAndCollectMetric() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"
        val data = JSONObject(mapOf("metricName" to "userDidSubmitFirstPrompt"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChatPixels).sendReportMetricPixel(USER_DID_SUBMIT_FIRST_PROMPT)
    }

    @Test
    fun whenReportMetricWithOpenHistoryThenPixelSent() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"
        val data = JSONObject(mapOf("metricName" to "userDidOpenHistory"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChatPixels).sendReportMetricPixel(USER_DID_OPEN_HISTORY)
    }

    @Test
    fun whenReportMetricWithSelectFirstHistoryItemThenPixelSent() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"
        val data = JSONObject(mapOf("metricName" to "userDidSelectFirstHistoryItem"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChatPixels).sendReportMetricPixel(USER_DID_SELECT_FIRST_HISTORY_ITEM)
    }

    @Test
    fun whenReportMetricWithCreateNewChatThenPixelSent() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"
        val data = JSONObject(mapOf("metricName" to "userDidCreateNewChat"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChatPixels).sendReportMetricPixel(USER_DID_CREATE_NEW_CHAT)
    }

    @Test
    fun whenReportMetricWithKeyboardReturnKeyThenPixelSent() = runTest {
        val featureName = "aiChat"
        val method = "reportMetric"
        val id = "123"
        val data = JSONObject(mapOf("metricName" to "userDidTapKeyboardReturnKey"))

        assertNull(
            testee.processJsCallbackMessage(
                featureName,
                method,
                id,
                data,
                pageContext = viewModel.updatedPageContext,
            ),
        )

        verify(mockDuckChatPixels).sendReportMetricPixel(USER_DID_TAP_KEYBOARD_RETURN_KEY)
    }

    @Test
    fun whenOpenKeyboardThenResponseSent() = runTest {
        val featureName = "aiChat"
        val method = "openKeyboard"
        val id = "123"
        val data = JSONObject(mapOf("selector" to "user-prompt"))

        val result = testee.processJsCallbackMessage(
            featureName,
            method,
            id,
            data,
            pageContext = viewModel.updatedPageContext,
        )

        val expectedPayload = JSONObject().apply {
            put("selector", "document.getElementsByName(''user-prompt'')[0]?.focus();")
            put("success", true)
            put("error", "")
        }

        assertEquals(expectedPayload.toString(), result!!.params.toString())
    }

    @Test
    fun whenNativeActionNewChatRequestedThenSubscriptionDataSent() = runTest {
        val result = testee.onNativeAction(NativeAction.NEW_CHAT)

        assertEquals("submitNewChatAction", result.subscriptionName)
        assertEquals(DUCK_CHAT_FEATURE_NAME, result.featureName)
    }

    @Test
    fun whenNativeActionHistoryRequestedThenSubscriptionDataSent() = runTest {
        val result = testee.onNativeAction(NativeAction.SIDEBAR)

        assertEquals("submitToggleSidebarAction", result.subscriptionName)
        assertEquals(DUCK_CHAT_FEATURE_NAME, result.featureName)
    }

    @Test
    fun whenNativeActionSettingsRequestedThenSubscriptionDataSent() = runTest {
        val result = testee.onNativeAction(NativeAction.DUCK_AI_SETTINGS)

        assertEquals("submitOpenSettingsAction", result.subscriptionName)
        assertEquals(DUCK_CHAT_FEATURE_NAME, result.featureName)
    }

    @Test
    fun whenGetAIChatNativeHandoffDataThenReportOpenIsCalled() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"

        testee.processJsCallbackMessage(
            featureName,
            method,
            "123",
            null,
            pageContext = viewModel.updatedPageContext,
        )

        coroutineRule.testScope.testScheduler.advanceTimeBy(500)
        coroutineRule.testScope.advanceUntilIdle()

        verify(mockDuckChatPixels, times(1)).reportOpen()
    }

    @Test
    fun whenGetAIChatNativeHandoffDataCalledTwiceThenReportOpenIsCalledOnlyOnce() = runTest {
        val featureName = "aiChat"
        val method = "getAIChatNativeHandoffData"

        testee.processJsCallbackMessage(
            featureName,
            method,
            "123",
            null,
            pageContext = viewModel.updatedPageContext,
        )
        testee.processJsCallbackMessage(
            featureName,
            method,
            "123",
            null,
            pageContext = viewModel.updatedPageContext,
        )

        coroutineRule.testScope.testScheduler.advanceTimeBy(500)
        coroutineRule.testScope.advanceUntilIdle()

        verify(mockDuckChatPixels, times(1)).reportOpen()
    }
}
