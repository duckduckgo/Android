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

package com.duckduckgo.contentscopescripts.impl.messaging

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.ContentScopeScriptsFeature
import com.duckduckgo.contentscopescripts.impl.CoreContentScopeScripts
import com.duckduckgo.contentscopescripts.impl.messaging.ContentScopeScriptsMessagingPixelName.INBOUND_QUEUE_FULL
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.js.messaging.api.JsErrorDetails
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.JsRequestResponse
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ContentScopeScriptsJsMessagingTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockWebView: WebView = mock()
    private val jsMessageHelper: JsMessageHelper = mock()
    private val coreContentScopeScripts: CoreContentScopeScripts = mock()
    private val handlers: PluginPoint<ContentScopeJsMessageHandlersPlugin> = FakePluginPoint()
    private val contentScopeScriptsFeature = FakeFeatureToggleFactory.create(ContentScopeScriptsFeature::class.java)
    private val pixel: Pixel = mock()
    private lateinit var contentScopeScriptsJsMessaging: ContentScopeScriptsJsMessaging

    private class FakePluginPoint : PluginPoint<ContentScopeJsMessageHandlersPlugin> {
        override fun getPlugins(): Collection<ContentScopeJsMessageHandlersPlugin> = listOf(FakePlugin())

        inner class FakePlugin : ContentScopeJsMessageHandlersPlugin {
            override fun getJsMessageHandler(): JsMessageHandler =
                object : JsMessageHandler {
                    override fun process(
                        jsMessage: JsMessage,
                        jsMessaging: JsMessaging,
                        jsMessageCallback: JsMessageCallback?,
                    ) {
                        jsMessageCallback?.process(jsMessage.featureName, jsMessage.method, jsMessage.id, jsMessage.params)
                    }

                    override val allowedDomains: List<String> = listOf("example.com")
                    override val featureName: String = "webCompat"
                    override val methods: List<String> = listOf("webShare", "permissionsQuery")
                }
        }
    }

    @Before
    fun setUp() {
        whenever(coreContentScopeScripts.secret).thenReturn("secret")
        whenever(coreContentScopeScripts.javascriptInterface).thenReturn("javascriptInterface")
        whenever(coreContentScopeScripts.callbackName).thenReturn("callbackName")
        contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = true))
        contentScopeScriptsJsMessaging = givenMessaging(handlers)
    }

    private fun givenMessaging(handlers: PluginPoint<ContentScopeJsMessageHandlersPlugin>) =
        ContentScopeScriptsJsMessaging(
            jsMessageHelper,
            coroutineRule.testDispatcherProvider,
            coreContentScopeScripts,
            handlers,
            contentScopeScriptsFeature,
            pixel,
            coroutineRule.testScope,
        )

    @Test
    fun `when process and message can be handled then execute callback`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(1, callback.counter)
        }

    @Test
    fun `when processing unknown message do nothing`() =
        runTest {
            givenInterfaceIsRegistered()

            contentScopeScriptsJsMessaging.process("", contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when processing unknown secret do nothing`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, "test")

            assertEquals(0, callback.counter)
        }

    @Test
    fun `if interface is not registered do nothing`() =
        runTest {
            whenever(mockWebView.url).thenReturn("https://example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when registering interface then add javascript interface is called`() {
        contentScopeScriptsJsMessaging.register(mockWebView, callback)

        verify(mockWebView).addJavascriptInterface(any(), anyOrNull())
    }

    @Test
    fun `when url is not allowed do nothing`() =
        runTest {
            givenInterfaceIsRegistered()
            whenever(mockWebView.url).thenReturn("https://nowAllowed.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when feature does not match do nothing`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"test","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when no handler matches and message has id then send method not found error`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"unknownMethod","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
            verify(jsMessageHelper).sendJsResponse(
                JsRequestResponse.Error(
                    context = "contentScopeScripts",
                    featureName = "webCompat",
                    method = "unknownMethod",
                    id = "myId",
                    error = JsErrorDetails(code = -32601, message = "Method not found"),
                ),
                "callbackName",
                "secret",
                mockWebView,
            )
        }

    @Test
    fun `when no handler matches and message has no id then do not send error response`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","method":"unknownMethod","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
            verify(jsMessageHelper, never()).sendJsResponse(any(), any(), any(), any())
        }

    @Test
    fun `when id does not exist do nothing`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","webCompat":"test","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when processing addDebugFlag message with valid secret then process message`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"debugFeature","id":"debugId","method":"addDebugFlag","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(1, callback.counter)
        }

    @Test
    fun `when processing addDebugFlag message then do not send method not found error`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"debugFeature","id":"debugId","method":"addDebugFlag","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            // addDebugFlag is answered by the callback alone, so it must not also be treated as an unrouted method.
            assertEquals(1, callback.counter)
            verify(jsMessageHelper, never()).sendJsResponse(any(), any(), any(), any())
        }

    @Test
    fun `when processing addDebugFlag message with wrong secret then do nothing`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"debugFeature","id":"debugId","method":"addDebugFlag","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, "wrongSecret")

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when processing message with subdomain of allowed domain then process message`() =
        runTest {
            contentScopeScriptsJsMessaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://subdomain.example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(1, callback.counter)
        }

    @Test
    fun `when processing message with null url and handler has allowed domains then do nothing`() =
        runTest {
            contentScopeScriptsJsMessaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn(null)

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when processing webEvents webEvent message then nativeData with webViewId is injected`() =
        runTest {
            val messaging = givenMessaging(WebEventsPluginPoint())
            messaging.register(mockWebView, webEventsCallback)
            whenever(mockWebView.url).thenReturn("https://example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webEvents","id":"e1","method":"webEvent","params":{"type":"adwall.detected"}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            assertEquals(1, webEventsCallback.counter)
            val params = webEventsCallback.lastData!!
            val nativeData = params.getJSONObject("nativeData")
            assertEquals(System.identityHashCode(mockWebView).toString(), nativeData.getString("webViewId"))
        }

    @Test
    fun `when processing non-webEvent message then nativeData is not injected`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(1, callback.counter)
            val params = callback.lastData
            assertFalse(params?.has("nativeData") ?: false)
        }

    @Test
    fun `when handler has no allowed domains then webView url is not read`() =
        runTest {
            val messaging = givenMessaging(WebEventsPluginPoint())
            messaging.register(mockWebView, webEventsCallback)

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webEvents","id":"e1","method":"webEvent","params":{"type":"adwall.detected"}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            assertEquals(1, webEventsCallback.counter)
            verify(mockWebView, never()).url
        }

    @Test
    fun `when multiple messages are received then all are handled in arrival order`() =
        runTest {
            givenInterfaceIsRegistered()

            listOf("first", "second", "third").forEach { id ->
                contentScopeScriptsJsMessaging.process(
                    """
                    {"context":"contentScopeScripts","featureName":"webCompat","id":"$id","method":"webShare","params":{}}
                    """.trimIndent(),
                    contentScopeScriptsJsMessaging.secret,
                )
            }

            assertEquals(3, callback.counter)
            assertEquals(listOf("first", "second", "third"), callback.ids)
        }

    @Test
    fun `when message has no method then do nothing`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when message has no params then it is handled with empty params`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare"}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(1, callback.counter)
            assertEquals(0, callback.lastData?.length())
        }

    @Test
    fun `when message id is null then it is handled without an id`() =
        runTest {
            givenInterfaceIsRegistered()

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":null,"method":"webShare","params":{}}
                """.trimIndent()

            contentScopeScriptsJsMessaging.process(message, contentScopeScriptsJsMessaging.secret)

            assertEquals(1, callback.counter)
            assertEquals(emptyList<String>(), callback.ids)
        }

    @Test
    fun `when first handler for a method is not allowed on this domain then a later one still handles it`() =
        runTest {
            val messaging = givenMessaging(TwoHandlersPluginPoint())
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            assertEquals(1, callback.counter)
            assertEquals(listOf("open"), callback.handledBy)
        }

    @Test
    fun `when optimization is disabled and message can be handled then execute callback`() =
        runTest {
            contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = false))
            val messaging = givenMessaging(handlers)
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            assertEquals(1, callback.counter)
        }

    @Test
    fun `when optimization is disabled and url is not allowed do nothing`() =
        runTest {
            contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = false))
            val messaging = givenMessaging(handlers)
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://notAllowed.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when optimization is disabled and no handler matches and message has id then send method not found error`() =
        runTest {
            contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = false))
            val messaging = givenMessaging(handlers)
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"unknownMethod","params":{}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            assertEquals(0, callback.counter)
            verify(jsMessageHelper).sendJsResponse(
                JsRequestResponse.Error(
                    context = "contentScopeScripts",
                    featureName = "webCompat",
                    method = "unknownMethod",
                    id = "myId",
                    error = JsErrorDetails(code = -32601, message = "Method not found"),
                ),
                "callbackName",
                "secret",
                mockWebView,
            )
        }

    @Test
    fun `when optimization is disabled and no handler matches and message has no id then do not send error response`() =
        runTest {
            contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = false))
            val messaging = givenMessaging(handlers)
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webCompat","method":"unknownMethod","params":{}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            assertEquals(0, callback.counter)
            verify(jsMessageHelper, never()).sendJsResponse(any(), any(), any(), any())
        }

    @Test
    fun `when optimization is disabled and processing addDebugFlag message then do not send method not found error`() =
        runTest {
            contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = false))
            val messaging = givenMessaging(handlers)
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"debugFeature","id":"debugId","method":"addDebugFlag","params":{}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            // addDebugFlag is answered by the callback alone, so it must not also be treated as an unrouted method.
            assertEquals(1, callback.counter)
            verify(jsMessageHelper, never()).sendJsResponse(any(), any(), any(), any())
        }

    @Test
    fun `when optimization is disabled then the url is read for every message`() =
        runTest {
            contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = false))
            val messaging = givenMessaging(WebEventsPluginPoint())
            messaging.register(mockWebView, webEventsCallback)
            whenever(mockWebView.url).thenReturn("https://example.com")

            val message =
                """
                {"context":"contentScopeScripts","featureName":"webEvents","id":"e1","method":"webEvent","params":{"type":"adwall.detected"}}
                """.trimIndent()

            messaging.process(message, messaging.secret)

            assertEquals(1, webEventsCallback.counter)
            // The legacy path reads the url regardless of the handler's allowed domains.
            verify(mockWebView, atLeastOnce()).url
        }

    @Test
    fun `when the flag is disabled after registering then the value sampled at registration is still used`() =
        runTest {
            val messaging = givenMessaging(WebEventsPluginPoint())
            messaging.register(mockWebView, webEventsCallback)
            contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = false))

            messaging.process(webEventMessage("e1"), messaging.secret)

            assertEquals(1, webEventsCallback.counter)
            // Still the optimized path: the legacy one would have read the url even for a handler with no allowed domains.
            verify(mockWebView, never()).url
        }

    // The tests above run on an UnconfinedTestDispatcher, where the consumer drains each message before the next one is
    // offered, so the queue never holds more than one. These use a StandardTestDispatcher instead: nothing runs until
    // advanceUntilIdle(), so messages genuinely queue and the consumer's ordering and recovery paths are exercised.
    @Test
    fun `when messages queue up behind the consumer then they are handled in arrival order`() =
        runTest {
            val messaging = givenQueuedMessaging(handlers)
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://example.com")

            listOf("first", "second", "third").forEach { messaging.process(webShareMessage(it), messaging.secret) }
            advanceUntilIdle()

            assertEquals(listOf("first", "second", "third"), callback.ids)
        }

    @Test
    fun `when registering again then messages queued for the previous webView are dropped`() =
        runTest {
            val messaging = givenQueuedMessaging(handlers)
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://example.com")
            messaging.process(webShareMessage("stale"), messaging.secret)

            messaging.register(mockWebView, callback)
            advanceUntilIdle()

            assertEquals(0, callback.counter)
        }

    @Test
    fun `when the inbound queue is full then further messages are dropped`() =
        runTest {
            val messaging = givenQueuedMessaging(WebEventsPluginPoint())
            messaging.register(mockWebView, webEventsCallback)

            repeat(MAX_QUEUED_MESSAGES + 8) { messaging.process(webEventMessage("e$it"), messaging.secret) }
            advanceUntilIdle()

            assertEquals(MAX_QUEUED_MESSAGES, webEventsCallback.counter)
        }

    @Test
    fun `when the inbound queue overflows then one pixel is fired for the episode`() =
        runTest {
            val messaging = givenQueuedMessaging(WebEventsPluginPoint())
            messaging.register(mockWebView, webEventsCallback)

            repeat(MAX_QUEUED_MESSAGES + 8) { messaging.process(webEventMessage("e$it"), messaging.secret) }

            verify(pixel, times(1)).fire(INBOUND_QUEUE_FULL, type = Daily())
        }

    @Test
    fun `when the queue drains then a later overflow is reported again`() =
        runTest {
            val messaging = givenQueuedMessaging(WebEventsPluginPoint())
            messaging.register(mockWebView, webEventsCallback)

            repeat(MAX_QUEUED_MESSAGES + 1) { messaging.process(webEventMessage("first$it"), messaging.secret) }
            advanceUntilIdle()
            repeat(MAX_QUEUED_MESSAGES + 1) { messaging.process(webEventMessage("second$it"), messaging.secret) }

            verify(pixel, times(2)).fire(INBOUND_QUEUE_FULL, type = Daily())
        }

    @Test
    fun `when the queue never overflows then no pixel is fired`() =
        runTest {
            val messaging = givenQueuedMessaging(WebEventsPluginPoint())
            messaging.register(mockWebView, webEventsCallback)

            repeat(MAX_QUEUED_MESSAGES) { messaging.process(webEventMessage("e$it"), messaging.secret) }
            advanceUntilIdle()

            verify(pixel, never()).fire(INBOUND_QUEUE_FULL, type = Daily())
        }

    @Test
    fun `when registering again then a message queued for the previous registration reaches neither callback`() =
        runTest {
            val messaging = givenQueuedMessaging(handlers)
            messaging.register(mockWebView, callback)
            whenever(mockWebView.url).thenReturn("https://example.com")
            messaging.process(webShareMessage("stale"), messaging.secret)

            messaging.register(mock(), webEventsCallback)
            advanceUntilIdle()

            assertEquals(0, callback.counter)
            assertEquals(0, webEventsCallback.counter)
        }

    @Test
    fun `when a handler throws an error then later messages are still handled`() =
        runTest {
            val messaging = givenQueuedMessaging(ThrowingPluginPoint())
            messaging.register(mockWebView, callback)

            messaging.process(webShareMessage("boom"), messaging.secret)
            advanceUntilIdle()
            messaging.process(webShareMessage("after"), messaging.secret)
            advanceUntilIdle()

            assertEquals(listOf("after"), callback.ids)
        }

    private fun webShareMessage(id: String) =
        """
        {"context":"contentScopeScripts","featureName":"webCompat","id":"$id","method":"webShare","params":{}}
        """.trimIndent()

    private fun webEventMessage(id: String) =
        """
        {"context":"contentScopeScripts","featureName":"webEvents","id":"$id","method":"webEvent","params":{"type":"t"}}
        """.trimIndent()

    private fun TestScope.givenQueuedMessaging(
        handlers: PluginPoint<ContentScopeJsMessageHandlersPlugin>,
    ): ContentScopeScriptsJsMessaging {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatcherProvider =
            object : DispatcherProvider {
                override fun io(): CoroutineDispatcher = dispatcher

                override fun main(): CoroutineDispatcher = dispatcher

                override fun computation(): CoroutineDispatcher = dispatcher

                override fun unconfined(): CoroutineDispatcher = dispatcher
            }
        // Deliberately not the TestScope: one of these tests has a handler throw an Error, and an uncaught throwable in a
        // child of the TestScope fails the test before the consumer's own recovery can be observed.
        val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, _ -> })
        return ContentScopeScriptsJsMessaging(
            jsMessageHelper,
            dispatcherProvider,
            coreContentScopeScripts,
            handlers,
            contentScopeScriptsFeature,
            pixel,
            scope,
        )
    }

    private class ThrowingPluginPoint : PluginPoint<ContentScopeJsMessageHandlersPlugin> {
        override fun getPlugins(): Collection<ContentScopeJsMessageHandlersPlugin> = listOf(Plugin())

        class Plugin : ContentScopeJsMessageHandlersPlugin {
            override fun getJsMessageHandler(): JsMessageHandler =
                object : JsMessageHandler {
                    override fun process(
                        jsMessage: JsMessage,
                        jsMessaging: JsMessaging,
                        jsMessageCallback: JsMessageCallback?,
                    ) {
                        if (jsMessage.id == "boom") throw AssertionError("boom")
                        jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id, jsMessage.params)
                    }

                    override val allowedDomains: List<String> = emptyList()
                    override val featureName: String = "webCompat"
                    override val methods: List<String> = listOf("webShare")
                }
        }
    }

    private val callback =
        object : JsMessageCallback() {
            var counter = 0
            var lastData: JSONObject? = null
            val ids = mutableListOf<String>()
            val handledBy = mutableListOf<String>()

            override fun process(
                featureName: String,
                method: String,
                id: String?,
                data: JSONObject?,
            ) {
                counter++
                lastData = data
                id?.let { ids.add(it) }
                handledBy.add(featureName)
            }
        }

    private val webEventsCallback =
        object : JsMessageCallback() {
            var counter = 0
            var lastData: JSONObject? = null

            override fun process(
                featureName: String,
                method: String,
                id: String?,
                data: JSONObject?,
            ) {
                counter++
                lastData = data
            }
        }

    private class TwoHandlersPluginPoint : PluginPoint<ContentScopeJsMessageHandlersPlugin> {
        override fun getPlugins(): Collection<ContentScopeJsMessageHandlersPlugin> =
            listOf(Plugin(listOf("other.com"), "restricted"), Plugin(emptyList(), "open"))

        class Plugin(
            private val domains: List<String>,
            private val name: String,
        ) : ContentScopeJsMessageHandlersPlugin {
            override fun getJsMessageHandler(): JsMessageHandler =
                object : JsMessageHandler {
                    override fun process(
                        jsMessage: JsMessage,
                        jsMessaging: JsMessaging,
                        jsMessageCallback: JsMessageCallback?,
                    ) {
                        jsMessageCallback?.process(name, jsMessage.method, jsMessage.id, jsMessage.params)
                    }

                    override val allowedDomains: List<String> = domains
                    override val featureName: String = "webCompat"
                    override val methods: List<String> = listOf("webShare")
                }
        }
    }

    private class WebEventsPluginPoint : PluginPoint<ContentScopeJsMessageHandlersPlugin> {
        override fun getPlugins(): Collection<ContentScopeJsMessageHandlersPlugin> = listOf(WebEventsPlugin())

        inner class WebEventsPlugin : ContentScopeJsMessageHandlersPlugin {
            override fun getJsMessageHandler(): JsMessageHandler =
                object : JsMessageHandler {
                    override fun process(
                        jsMessage: JsMessage,
                        jsMessaging: JsMessaging,
                        jsMessageCallback: JsMessageCallback?,
                    ) {
                        jsMessageCallback?.process(jsMessage.featureName, jsMessage.method, jsMessage.id, jsMessage.params)
                    }

                    override val allowedDomains: List<String> = emptyList()
                    override val featureName: String = "webEvents"
                    override val methods: List<String> = listOf("webEvent")
                }
        }
    }

    private fun givenInterfaceIsRegistered() {
        contentScopeScriptsJsMessaging.register(mockWebView, callback)
        whenever(mockWebView.url).thenReturn("https://example.com")
    }
}
