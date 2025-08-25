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

package com.duckduckgo.pir.impl.dashboard.messaging

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirWebJsMessageHandler
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirDashboardWebMessagingInterfaceTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirDashboardWebMessagingInterface

    private val mockJsMessageHelper: JsMessageHelper = mock()
    private val mockMessageHandlers: PluginPoint<PirWebJsMessageHandler> = mock()
    private val mockWebView: WebView = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val mockMessageHandler: PirWebJsMessageHandler = mock()
    private val mockJsonObject: JSONObject = mock()

    @Before
    fun setUp() {
        testee = PirDashboardWebMessagingInterface(
            jsMessageHelper = mockJsMessageHelper,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            messageHandlers = mockMessageHandlers,
        )
    }

    @Test
    fun whenRegisterWithValidCallbackThenWebViewIsConfigured() {
        // When
        testee.register(mockWebView, mockJsMessageCallback)

        // Then
        verify(mockWebView).addJavascriptInterface(testee, "dbpui")
    }

    @Test(expected = Exception::class)
    fun whenRegisterWithNullCallbackThenThrowsException() {
        // When
        testee.register(mockWebView, null)
    }

    @Test
    fun whenProcessWithValidMessageAndSecretAndDomainThenDelegatesToHandler() = runTest {
        // Given
        val validSecret = PirDashboardWebConstants.SECRET
        val messageJson =
            """{"context":"dbpui","featureName":"testFeature","method":"testMethod","id":"123","params":{}}"""

        testee.register(mockWebView, mockJsMessageCallback)
        whenever(mockWebView.url).thenReturn("https://duckduckgo.com/test")
        whenever(mockMessageHandlers.getPlugins()).thenReturn(listOf(mockMessageHandler))
        whenever(mockMessageHandler.methods).thenReturn(listOf("testMethod"))
        whenever(mockMessageHandler.featureName).thenReturn("testFeature")

        // When
        testee.process(messageJson, validSecret)

        // Then
        val messageCaptor = argumentCaptor<JsMessage>()
        verify(mockMessageHandler).process(messageCaptor.capture(), any(), any())
        assertEquals("dbpui", messageCaptor.firstValue.context)
        assertEquals("testFeature", messageCaptor.firstValue.featureName)
        assertEquals("testMethod", messageCaptor.firstValue.method)
    }

    @Test
    fun whenProcessWithInvalidSecretThenDoesNotDelegateToHandler() = runTest {
        // Given
        val messageJson =
            """{"context":"dbpui","featureName":"testFeature","method":"testMethod","id":"123","params":{}}"""
        val invalidSecret = "invalid-secret"

        testee.register(mockWebView, mockJsMessageCallback)
        whenever(mockWebView.url).thenReturn("https://duckduckgo.com/test")
        whenever(mockMessageHandlers.getPlugins()).thenReturn(listOf(mockMessageHandler))

        // When
        testee.process(messageJson, invalidSecret)

        // Then
        verify(mockMessageHandler, never()).process(any(), any(), any())
    }

    @Test
    fun whenProcessWithInvalidContextThenDoesNotDelegateToHandler() = runTest {
        // Given
        val messageJson = """{"context":"invalidContext","featureName":"testFeature","method":"testMethod","id":"123","params":{}}"""
        val validSecret = PirDashboardWebConstants.SECRET

        testee.register(mockWebView, mockJsMessageCallback)
        whenever(mockWebView.url).thenReturn("https://duckduckgo.com/test")
        whenever(mockMessageHandlers.getPlugins()).thenReturn(listOf(mockMessageHandler))

        // When
        testee.process(messageJson, validSecret)

        // Then
        verify(mockMessageHandler, never()).process(any(), any(), any())
    }

    @Test
    fun whenProcessWithDisallowedDomainThenDoesNotDelegateToHandler() = runTest {
        // Given
        val messageJson =
            """{"context":"dbpui","featureName":"testFeature","method":"testMethod","id":"123","params":{}}"""
        val validSecret = PirDashboardWebConstants.SECRET

        testee.register(mockWebView, mockJsMessageCallback)
        whenever(mockWebView.url).thenReturn("https://malicious.com/test")
        whenever(mockMessageHandlers.getPlugins()).thenReturn(listOf(mockMessageHandler))

        // When
        testee.process(messageJson, validSecret)

        // Then
        verify(mockMessageHandler, never()).process(any(), any(), any())
    }

    @Test
    fun whenProcessWithInvalidJsonThenDoesNotThrowException() = runTest {
        // Given
        val invalidJson = "invalid json"
        val validSecret = PirDashboardWebConstants.SECRET

        testee.register(mockWebView, mockJsMessageCallback)

        // When - This should not throw an exception
        testee.process(invalidJson, validSecret)

        // Then
        verify(mockMessageHandler, never()).process(any(), any(), any())
    }

    @Test
    fun whenProcessWithNoMatchingHandlerThenDoesNotProcess() = runTest {
        // Given
        val messageJson =
            """{"context":"dbpui","featureName":"testFeature","method":"testMethod","id":"123","params":{}}"""
        val validSecret = PirDashboardWebConstants.SECRET

        testee.register(mockWebView, mockJsMessageCallback)
        whenever(mockWebView.url).thenReturn("https://duckduckgo.com/test")
        whenever(mockMessageHandlers.getPlugins()).thenReturn(listOf(mockMessageHandler))
        whenever(mockMessageHandler.methods).thenReturn(listOf("differentMethod"))
        whenever(mockMessageHandler.featureName).thenReturn("testFeature")

        // When
        testee.process(messageJson, validSecret)

        // Then
        verify(mockMessageHandler, never()).process(any(), any(), any())
    }

    @Test
    fun whenProcessWithMatchingMethodButDifferentFeatureThenDoesNotProcess() = runTest {
        // Given
        val messageJson =
            """{"context":"dbpui","featureName":"testFeature","method":"testMethod","id":"123","params":{}}"""
        val validSecret = PirDashboardWebConstants.SECRET

        testee.register(mockWebView, mockJsMessageCallback)
        whenever(mockWebView.url).thenReturn("https://duckduckgo.com/test")
        whenever(mockMessageHandlers.getPlugins()).thenReturn(listOf(mockMessageHandler))
        whenever(mockMessageHandler.methods).thenReturn(listOf("testMethod"))
        whenever(mockMessageHandler.featureName).thenReturn("differentFeature")

        // When
        testee.process(messageJson, validSecret)

        // Then
        verify(mockMessageHandler, never()).process(any(), any(), any())
    }

    @Test
    fun whenOnResponseThenSendsJsResponse() {
        // Given
        val callbackData = JsCallbackData(
            featureName = "testFeature",
            method = "testMethod",
            id = "123",
            params = mockJsonObject,
        )

        testee.register(mockWebView, mockJsMessageCallback)

        // When
        testee.onResponse(callbackData)

        // Then
        verify(mockJsMessageHelper).sendJsResponse(
            any<JsRequestResponse.Success>(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun whenOnResponseThenCreatesCorrectJsResponse() {
        // Given
        val callbackData = JsCallbackData(
            featureName = "testFeature",
            method = "testMethod",
            id = "123",
            params = mockJsonObject,
        )

        testee.register(mockWebView, mockJsMessageCallback)

        // When
        testee.onResponse(callbackData)

        // Then
        verify(mockJsMessageHelper).sendJsResponse(
            argThat { response ->
                response is JsRequestResponse.Success &&
                    response.context == "dbpui" &&
                    response.featureName == "testFeature" &&
                    response.method == "testMethod" &&
                    response.id == "123" &&
                    response.result == mockJsonObject
            },
            eq(PirDashboardWebConstants.MESSAGE_CALLBACK),
            eq(PirDashboardWebConstants.SECRET),
            eq(mockWebView),
        )
    }

    @Test
    fun whenSendSubscriptionEventThenSendsEvent() {
        // Given
        val subscriptionEventData = SubscriptionEventData(
            featureName = "testFeature",
            subscriptionName = "testSubscription",
            params = mockJsonObject,
        )

        testee.register(mockWebView, mockJsMessageCallback)

        // When
        testee.sendSubscriptionEvent(subscriptionEventData)

        // Then
        verify(mockJsMessageHelper).sendSubscriptionEvent(
            any<SubscriptionEvent>(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun whenSendSubscriptionEventThenCreatesCorrectEvent() {
        // Given
        val subscriptionEventData = SubscriptionEventData(
            featureName = "testFeature",
            subscriptionName = "testSubscription",
            params = mockJsonObject,
        )

        testee.register(mockWebView, mockJsMessageCallback)

        // When
        testee.sendSubscriptionEvent(subscriptionEventData)

        // Then
        verify(mockJsMessageHelper).sendSubscriptionEvent(
            argThat { event ->
                event.context == "dbpui" &&
                    event.featureName == "testFeature" &&
                    event.subscriptionName == "testSubscription" &&
                    event.params == mockJsonObject
            },
            eq(PirDashboardWebConstants.MESSAGE_CALLBACK),
            eq(PirDashboardWebConstants.SECRET),
            eq(mockWebView),
        )
    }

    @Test
    fun whenInstanceCreatedThenPropertiesAreSetCorrectly() {
        // Then
        assertEquals(PirDashboardWebConstants.SCRIPT_CONTEXT_NAME, testee.context)
        assertEquals(PirDashboardWebConstants.MESSAGE_CALLBACK, testee.callbackName)
        assertEquals(PirDashboardWebConstants.SECRET, testee.secret)
        assertEquals(listOf(PirDashboardWebConstants.ALLOWED_DOMAIN), testee.allowedDomains)
    }

    @Test
    fun whenProcessWithNullUrlThenDoesNotDelegateToHandler() = runTest {
        // Given
        val messageJson =
            """{"context":"dbpui","featureName":"testFeature","method":"testMethod","id":"123","params":{}}"""
        val validSecret = PirDashboardWebConstants.SECRET

        testee.register(mockWebView, mockJsMessageCallback)
        whenever(mockWebView.url).thenReturn(null)
        whenever(mockMessageHandlers.getPlugins()).thenReturn(listOf(mockMessageHandler))

        // When
        testee.process(messageJson, validSecret)

        // Then
        verify(mockMessageHandler, never()).process(any(), any(), any())
    }

    @Test
    fun whenProcessWithSubdomainOfAllowedDomainThenDelegatesToHandler() = runTest {
        // Given
        val messageJson =
            """{"context":"dbpui","featureName":"testFeature","method":"testMethod","id":"123","params":{}}"""
        val validSecret = PirDashboardWebConstants.SECRET

        testee.register(mockWebView, mockJsMessageCallback)
        whenever(mockWebView.url).thenReturn("https://sub.duckduckgo.com/test")
        whenever(mockMessageHandlers.getPlugins()).thenReturn(listOf(mockMessageHandler))
        whenever(mockMessageHandler.methods).thenReturn(listOf("testMethod"))
        whenever(mockMessageHandler.featureName).thenReturn("testFeature")

        // When
        testee.process(messageJson, validSecret)

        // Then
        val messageCaptor = argumentCaptor<JsMessage>()
        verify(mockMessageHandler).process(messageCaptor.capture(), any(), any())
        assertEquals("testFeature", messageCaptor.firstValue.featureName)
        assertEquals("testMethod", messageCaptor.firstValue.method)
    }

    private inline fun <reified T : Any> argThat(noinline predicate: (T) -> Boolean): T {
        return org.mockito.kotlin.argThat(predicate)
    }

    private fun <T> eq(value: T): T = org.mockito.kotlin.eq(value)
}
