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
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.contentscopescripts.api.MessageHandlerPlugin
import com.duckduckgo.contentscopescripts.api.ResponseListener
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class MessagingInterfaceTest {
    private val mockWebView: WebView = mock()
    private val mockContentScopeScripts: ContentScopeScripts = mock()
    private val pluginPoint = FakePluginPoint()

    private lateinit var messagingInterface: MessagingInterface

    @Before
    fun setup() {
        messagingInterface = MessagingInterface(pluginPoint, mockWebView, messageSecret, mockContentScopeScripts)
    }

    @Test
    fun whenMessagedParsedIfTypeMatchesThenCallProcess() {
        val message = """{"type":"fake"}"""
        messagingInterface.process(message, messageSecret)
        assertEquals(1, pluginPoint.plugin.count)
    }

    @Test
    fun whenSecretDoesNotMatchThenDoNotCallProcess() {
        val message = """{"type":"fake"}"""
        messagingInterface.process(message, "secret5678")
        assertEquals(0, pluginPoint.plugin.count)
    }

    @Test
    fun whenMessagedParsedIfTypeDoesNotMatchThenDoNotCallProcess() {
        val message = """{"type":"noMatchingType"}"""
        messagingInterface.process(message, messageSecret)
        assertEquals(0, pluginPoint.plugin.count)
    }

    @Test
    fun whenOnResponseThenSendMessageToContentScopeScripts() {
        val message = "a response"
        messagingInterface.onResponse(message)
        verify(mockContentScopeScripts).sendMessage(message, mockWebView)
    }

    companion object {
        const val messageSecret = "secret1234"
    }
}

class FakePluginPoint : PluginPoint<MessageHandlerPlugin> {
    val plugin = FakeMessageHandlerPlugin()
    override fun getPlugins(): Collection<MessageHandlerPlugin> {
        return listOf(plugin)
    }
}

class FakeMessageHandlerPlugin : MessageHandlerPlugin {
    var count = 0

    override fun process(
        messageType: String,
        jsonString: String,
        responseListener: ResponseListener,
    ) {
        count++
    }

    override val supportedTypes: List<String> = listOf("fake")
}
