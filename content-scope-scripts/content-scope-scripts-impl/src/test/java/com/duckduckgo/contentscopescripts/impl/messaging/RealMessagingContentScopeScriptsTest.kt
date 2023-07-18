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
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.contentscopescripts.api.MessageHandlerPlugin
import com.duckduckgo.contentscopescripts.impl.CoreContentScopeScripts
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealMessagingContentScopeScriptsTest {

    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val mockMessageHandlerPlugins: PluginPoint<MessageHandlerPlugin> = mock()
    private val mockWebView: WebView = mock()

    private lateinit var messagingContentScopeScripts: ContentScopeScripts

    @Before
    fun setUp() {
        messagingContentScopeScripts = RealMessagingContentScopeScripts(mockCoreContentScopeScripts, mockMessageHandlerPlugins)
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenPopulateMessagingParameters() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript()).thenReturn(coreContentScopeJs)
        messagingContentScopeScripts.injectContentScopeScripts(mockWebView)

        val scriptCatcher = argumentCaptor<String>()
        verify(mockWebView).evaluateJavascript(scriptCatcher.capture(), anyOrNull())

        assertTrue(contentScopeRegex.matches(scriptCatcher.firstValue))

        val matchResult = contentScopeRegex.find(scriptCatcher.firstValue)
        val messageSecret = matchResult!!.groupValues[1]
        val messageCallback = matchResult.groupValues[2]
        val messageInterface = matchResult.groupValues[3]
        assertTrue(messageSecret != messageCallback && messageSecret != messageInterface && messageCallback != messageInterface)
    }

    @Test
    fun whenDisabledAndInjectContentScopeScriptsThenDoNothing() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(false)
        messagingContentScopeScripts.injectContentScopeScripts(mockWebView)

        verifyNoInteractions(mockWebView)
    }

    @Test
    fun whenAddJsInterfaceThenSetNameToUUID() {
        messagingContentScopeScripts.addJsInterface(mockWebView)
        val nameCaptor = argumentCaptor<String>()
        verify(mockWebView).addJavascriptInterface(any(), nameCaptor.capture())

        val regex = Regex("[\\da-f]{32}")
        val interfaceName = nameCaptor.firstValue
        assertTrue(regex.matches(interfaceName))
    }

    @Test
    fun whenSendMessageThenEvaluateJS() {
        messagingContentScopeScripts.sendMessage(MESSAGE, mockWebView)
        val jsCaptor = argumentCaptor<String>()
        verify(mockWebView).evaluateJavascript(jsCaptor.capture(), anyOrNull())

        val js = jsCaptor.firstValue
        assertTrue(sendMessageJSRegex.matches(js))
    }

    companion object {
        const val coreContentScopeJs = "processConfig(" +
            "{\"features\":{" +
            "\"config1\":{\"state\":\"enabled\"}," +
            "\"config2\":{\"state\":\"disabled\"}}," +
            "\"unprotectedTemporary\":[{\"domain\":\"example.com\",\"reason\":\"reason\"},{\"domain\":\"foo.com\",\"reason\":\"reason2\"}]}, " +
            "[\"example.com\"], {\"versionNumber\":1234,\"platform\":{\"name\":\"android\"},\"sessionKey\":\"5678\"," +
            "\$ANDROID_MESSAGING_PARAMETERS\$})"

        val contentScopeRegex = Regex(
            "^javascript:processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\},\"sessionKey\":\"5678\"," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"messageInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )

        const val MESSAGE = "message"

        val sendMessageJSRegex =
            Regex("\\(function\\(\\) \\{\\s*window\\['([\\da-f]{32})'\\]\\('([\\da-f]{32})', ${MESSAGE}\\);\\s*\\}\\)\\(\\);")
    }
}
