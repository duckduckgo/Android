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

package com.duckduckgo.pir.impl.scripts

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.impl.di.PirModule
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.JsActionFailed
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExecuteScriptResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealBrokerActionProcessorTest {
    private lateinit var testee: RealBrokerActionProcessor
    private val mockJsMessaging: JsMessaging = mock()
    private val moshi = PirModule().providePirMoshi(Moshi.Builder().build())
    private val mockWebView: WebView = mock()
    private val mockActionResultListener: BrokerActionProcessor.ActionResultListener = mock()

    private var capturedCallback: JsMessageCallback? = null

    @Before
    fun setUp() {
        testee = RealBrokerActionProcessor(mockJsMessaging, moshi)
        whenever(mockJsMessaging.register(any(), any())).thenAnswer { invocation ->
            capturedCallback = invocation.getArgument(1)
            null
        }
    }

    private fun registerAndGetCallback(): JsMessageCallback {
        testee.register(mockWebView, mockActionResultListener)
        return capturedCallback!!
    }

    @Test
    fun whenRegisterThenJsMessagingIsRegistered() {
        testee.register(mockWebView, mockActionResultListener)

        verify(mockJsMessaging).register(any(), any())
    }

    @Test
    fun whenPushActionThenSendsSubscriptionEvent() = runTest {
        val action = BrokerAction.Navigate(id = "action-1", url = "https://example.com")
        val profileQuery = com.duckduckgo.pir.impl.models.ProfileQuery(
            id = 1L,
            firstName = "John",
            lastName = "Doe",
            city = "TestCity",
            state = "TS",
            addresses = listOf(
                com.duckduckgo.pir.impl.models.Address(city = "TestCity", state = "TS"),
            ),
            birthYear = 1990,
            fullName = "John Doe",
            age = 34,
            deprecated = false,
        )
        val requestData = UserProfile(
            userProfile = profileQuery,
            extractedProfile = null,
        )

        testee.pushAction(action, requestData)

        val eventCaptor = argumentCaptor<SubscriptionEventData>()
        verify(mockJsMessaging).sendSubscriptionEvent(eventCaptor.capture())

        assertEquals(PIRScriptConstants.SCRIPT_FEATURE_NAME, eventCaptor.firstValue.featureName)
        assertEquals(PIRScriptConstants.SUBSCRIBED_METHOD_NAME_RECEIVED, eventCaptor.firstValue.subscriptionName)
        assertNotNull(eventCaptor.firstValue.params)
    }

    @Test
    fun whenPushExtractActionThenProfileSelectorsAreForwardedVerbatim() = runTest {
        val actionJson = """
            {
                "actionType": "extract",
                "id": "extract-1",
                "selector": ".search-item",
                "profile": {
                    "name": {
                        "selector": ".title"
                    },
                    "addressCityStateList": {
                        "selector": ".//li",
                        "findElements": true,
                        "city": {
                            "selector": ".city"
                        },
                        "state": {
                            "selector": ".state"
                        }
                    },
                    "profileUrl": {
                        "selector": "a",
                        "attribute": "href"
                    },
                    "shoeSize": {
                        "selector": ".shoe"
                    }
                }
            }
        """.trimIndent()
        val action = moshi.adapter(BrokerAction::class.java).fromJson(actionJson)!!

        testee.pushAction(action, UserProfile())

        val eventCaptor = argumentCaptor<SubscriptionEventData>()
        verify(mockJsMessaging).sendSubscriptionEvent(eventCaptor.capture())

        val pushedProfile = eventCaptor.firstValue.params
            .getJSONObject("state")
            .getJSONObject("action")
            .getJSONObject("profile")
        assertEquals(".title", pushedProfile.getJSONObject("name").getString("selector"))
        assertEquals(".shoe", pushedProfile.getJSONObject("shoeSize").getString("selector"))
        assertEquals("href", pushedProfile.getJSONObject("profileUrl").getString("attribute"))
        pushedProfile.getJSONObject("addressCityStateList").also {
            assertEquals(".//li", it.getString("selector"))
            assertTrue(it.getBoolean("findElements"))
            assertEquals(".city", it.getJSONObject("city").getString("selector"))
            assertEquals(".state", it.getJSONObject("state").getString("selector"))
        }
    }

    @Test
    fun whenProcessJsCallbackWithExecuteScriptSuccessThenCallsOnSuccess() = runTest {
        val callback = registerAndGetCallback()

        val successJson = """
            {
                "result": {
                    "success": {
                        "actionID": "action-1",
                        "actionType": "executeScript",
                        "response": null
                    }
                }
            }
        """.trimIndent()

        callback.process(
            featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
            method = PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED,
            id = null,
            data = JSONObject(successJson),
        )

        val successCaptor = argumentCaptor<PirSuccessResponse>()
        verify(mockActionResultListener).onSuccess(successCaptor.capture())
        assertTrue(successCaptor.firstValue is ExecuteScriptResponse)
        assertEquals("action-1", successCaptor.firstValue.actionID)
        assertEquals("executeScript", successCaptor.firstValue.actionType)
    }

    @Test
    fun whenProcessJsCallbackWithSuccessThenCallsOnSuccess() = runTest {
        val callback = registerAndGetCallback()

        val successJson = """
            {
                "result": {
                    "success": {
                        "actionID": "action-1",
                        "actionType": "navigate",
                        "response": {
                            "url": "https://example.com"
                        }
                    }
                }
            }
        """.trimIndent()

        callback.process(
            featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
            method = PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED,
            id = null,
            data = JSONObject(successJson),
        )

        val successCaptor = argumentCaptor<PirSuccessResponse>()
        verify(mockActionResultListener).onSuccess(successCaptor.capture())
        assertEquals("action-1", successCaptor.firstValue.actionID)
        assertEquals("navigate", successCaptor.firstValue.actionType)
    }

    @Test
    fun whenProcessJsCallbackWithErrorThenCallsOnError() = runTest {
        val callback = registerAndGetCallback()

        val errorJson = """
            {
                "result": {
                    "error": {
                        "actionID": "action-1",
                        "message": "Test error message"
                    }
                }
            }
        """.trimIndent()

        callback.process(
            featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
            method = PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED,
            id = null,
            data = JSONObject(errorJson),
        )

        val errorCaptor = argumentCaptor<PirError>()
        verify(mockActionResultListener).onError(errorCaptor.capture())
        val error = errorCaptor.firstValue as JsActionFailed
        assertEquals("action-1", error.actionID)
        assertEquals("Test error message", error.message)
    }

    @Test
    fun whenProcessJsCallbackWithNoActionFoundThenCallsOnErrorWithNoActionFound() = runTest {
        val callback = registerAndGetCallback()

        val errorJson = """
            {
                "error": "No action found."
            }
        """.trimIndent()

        callback.process(
            featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
            method = PIRScriptConstants.RECEIVED_METHOD_NAME_ERROR,
            id = null,
            data = JSONObject(errorJson),
        )

        val errorCaptor = argumentCaptor<PirError>()
        verify(mockActionResultListener).onError(errorCaptor.capture())
        assertEquals(PirError.JsError.NoActionFound, errorCaptor.firstValue)
    }

    @Test
    fun whenProcessJsCallbackWithInvalidJsonThenCallsOnErrorWithParsingFailed() = runTest {
        val callback = registerAndGetCallback()

        val invalidJson = """
            {
                "result": {}
            }
        """.trimIndent()

        callback.process(
            featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
            method = PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED,
            id = null,
            data = JSONObject(invalidJson),
        )

        val errorCaptor = argumentCaptor<PirError>()
        verify(mockActionResultListener).onError(errorCaptor.capture())
        assertEquals(PirError.JsError.ParsingErrorObjectFailed, errorCaptor.firstValue)
    }

    @Test
    fun whenProcessJsCallbackWithUnknownMethodThenCallsOnErrorWithParsingFailed() = runTest {
        val callback = registerAndGetCallback()

        callback.process(
            featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
            method = "unknown_method",
            id = null,
            data = null,
        )

        val errorCaptor = argumentCaptor<PirError>()
        verify(mockActionResultListener).onError(errorCaptor.capture())
        assertEquals(PirError.JsError.ParsingErrorObjectFailed, errorCaptor.firstValue)
    }

    @Test
    fun whenProcessJsCallbackWithNullDataThenCallsOnErrorWithParsingFailed() = runTest {
        val callback = registerAndGetCallback()

        callback.process(
            featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
            method = PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED,
            id = null,
            data = null,
        )

        val errorCaptor = argumentCaptor<PirError>()
        verify(mockActionResultListener).onError(errorCaptor.capture())
        assertEquals(PirError.JsError.ParsingErrorObjectFailed, errorCaptor.firstValue)
    }
}
