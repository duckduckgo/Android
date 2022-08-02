/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AutoconsentInterfaceTest {
    private val mockWebView: WebView = mock()
    private val mockAutoconsentCallback: AutoconsentCallback = mock()
    private val pluginPoint = FakePluginPoint()

    lateinit var testee: AutoconsentInterface

    @Before
    fun setup() {
        testee = AutoconsentInterface(pluginPoint, mockWebView, mockAutoconsentCallback)
    }

    @Test
    fun whenMessagedParsedIfTypeMatchesThenCallProcess() {
        val message = """{"type":"fake"}"""

        testee.process(message)

        assertEquals(1, pluginPoint.plugin.count)
    }

    @Test
    fun whenMessagedParsedIfTypeDoesNotMatcheThenDoNotCallProcess() {
        val message = """{"type":"noMatchingType"}"""

        testee.process(message)

        assertEquals(0, pluginPoint.plugin.count)
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
        webView: WebView,
        autoconsentCallback: AutoconsentCallback
    ) {
        count++
    }

    override val type: String = "fake"
}
