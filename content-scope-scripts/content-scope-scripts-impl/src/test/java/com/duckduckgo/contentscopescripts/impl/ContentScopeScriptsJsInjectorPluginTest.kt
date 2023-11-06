package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import com.duckduckgo.js.messaging.api.JsMessaging
import java.util.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ContentScopeScriptsJsInjectorPluginTest {

    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockWebView: WebView = mock()

    private lateinit var contentScopeScriptsJsInjectorPlugin: ContentScopeScriptsJsInjectorPlugin

    @Before
    fun setUp() {
        whenever(mockJsMessaging.context).thenReturn(getSecret())
        whenever(mockJsMessaging.secret).thenReturn(getSecret())
        whenever(mockJsMessaging.callbackName).thenReturn(getSecret())
        contentScopeScriptsJsInjectorPlugin = ContentScopeScriptsJsInjectorPlugin(mockCoreContentScopeScripts, mockJsMessaging)
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenPopulateMessagingParameters() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript()).thenReturn(coreContentScopeJs)
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null)

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
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null)

        verifyNoInteractions(mockWebView)
    }

    private fun getSecret(): String = UUID.randomUUID().toString().replace("-", "")

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
    }
}
