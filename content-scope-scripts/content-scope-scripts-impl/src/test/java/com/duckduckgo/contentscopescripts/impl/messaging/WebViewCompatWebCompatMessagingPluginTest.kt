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

package com.duckduckgo.contentscopescripts.impl.messaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewCompat.WebMessageListener
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.WebViewCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.WebViewCompatMessageHandler
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WebViewCompatWebCompatMessagingPluginTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val handlers: PluginPoint<WebViewCompatContentScopeJsMessageHandlersPlugin> = FakePluginPoint()
    private val globalHandlers: PluginPoint<GlobalContentScopeJsMessageHandlersPlugin> = FakeGlobalHandlersPluginPoint()
    private lateinit var testee: WebViewCompatWebCompatMessagingPlugin

    private class FakePluginPoint : PluginPoint<WebViewCompatContentScopeJsMessageHandlersPlugin> {
        override fun getPlugins(): Collection<WebViewCompatContentScopeJsMessageHandlersPlugin> {
            return listOf(FakePlugin())
        }

        inner class FakePlugin : WebViewCompatContentScopeJsMessageHandlersPlugin {
            override fun getJsMessageHandler(): WebViewCompatMessageHandler {
                return object : WebViewCompatMessageHandler {
                    override fun process(
                        jsMessage: JsMessage,
                        jsMessageCallback: JsMessageCallback?,
                    ) {
                        jsMessageCallback?.process(jsMessage.featureName, jsMessage.method, jsMessage.id, jsMessage.params)
                    }

                    override val featureName: String = "webCompat"
                    override val methods: List<String> = listOf("webShare", "permissionsQuery")
                }
            }
        }
    }

    private class FakeGlobalHandlersPluginPoint : PluginPoint<GlobalContentScopeJsMessageHandlersPlugin> {
        override fun getPlugins(): Collection<GlobalContentScopeJsMessageHandlersPlugin> {
            return listOf(FakeGlobalHandlerPlugin())
        }

        inner class FakeGlobalHandlerPlugin : GlobalContentScopeJsMessageHandlersPlugin {

            override fun getGlobalJsMessageHandler(): GlobalJsMessageHandler {
                return object : GlobalJsMessageHandler {

                    override fun process(
                        jsMessage: JsMessage,
                        jsMessageCallback: JsMessageCallback,
                    ) {
                        jsMessageCallback.process(jsMessage.featureName, jsMessage.method, jsMessage.id, jsMessage.params)
                    }

                    override val method: String = "addDebugFlag"
                }
            }
        }
    }

    @Before
    fun setUp() = runTest {
        whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(true)
        testee = WebViewCompatWebCompatMessagingPlugin(
            handlers = handlers,
            globalHandlers = globalHandlers,
            webViewCompatContentScopeScripts = webViewCompatContentScopeScripts,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when process and message can be handled then execute callback`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"contentScopeScripts","featureName":"webCompat","id":"myId","method":"webShare","params":{}}
        """.trimIndent()

        testee.process(message, callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when processing unknown message do nothing`() = runTest {
        givenInterfaceIsRegistered()

        testee.process("", callback)

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when feature does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"contentScopeScripts","featureName":"test","id":"myId","method":"webShare","params":{}}
        """.trimIndent()

        testee.process(message, callback)

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when id does not exist do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"contentScopeScripts","webCompat":"test","method":"webShare","params":{}}
        """.trimIndent()

        testee.process(message, callback)

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when processing addDebugFlag message then process message`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"contentScopeScripts","featureName":"debugFeature","id":"debugId","method":"addDebugFlag","params":{}}
        """.trimIndent()

        testee.process(message, callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when registering and adsjs is disabled then do not register`() = runTest {
        whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(false)

        var capturedObjectName: String? = null
        var capturedAllowedOriginRules: Set<String>? = null
        val registerer: suspend (objectName: String, allowedOriginRules: Set<String>, webMessageListener: WebMessageListener) -> Boolean =
            { objectName, allowedOriginRules, webMessageListener ->
                capturedObjectName = objectName
                capturedAllowedOriginRules = allowedOriginRules
                true
            }

        testee.register(callback, registerer)

        assertNull(capturedObjectName)
        assertNull(capturedAllowedOriginRules)
    }

    @Test
    fun `when registering and adsjs is enabled then register`() = runTest {
        whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(true)

        var capturedObjectName: String? = null
        var capturedAllowedOriginRules: Set<String>? = null
        val registerer: suspend (objectName: String, allowedOriginRules: Set<String>, webMessageListener: WebMessageListener) -> Boolean =
            { objectName, allowedOriginRules, webMessageListener ->
                capturedObjectName = objectName
                capturedAllowedOriginRules = allowedOriginRules
                true
            }

        testee.register(callback, registerer)

        assertEquals("contentScopeAdsjs", capturedObjectName)
        assertEquals(setOf("*"), capturedAllowedOriginRules)
    }

    @Test
    fun `when unregistering and adsjs is disabled then do not unregister`() = runTest {
        whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(false)
        var capturedObjectName: String? = null
        val unregisterer: suspend (objectName: String) -> Boolean =
            { objectName ->
                capturedObjectName = objectName
                true
            }

        testee.unregister(unregisterer)

        assertNull(capturedObjectName)
    }

    @Test
    fun `when unregistering and adsjs is enabled then unregister`() = runTest {
        whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(true)
        var capturedObjectName: String? = null
        val unregisterer: suspend (objectName: String) -> Boolean = { objectName ->
            capturedObjectName = objectName
            true
        }

        testee.unregister(unregisterer)

        assertEquals("contentScopeAdsjs", capturedObjectName)
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
        }
    }

    private fun givenInterfaceIsRegistered() = runTest {
        testee.register(callback) { _, _, _ -> true }
    }
}
