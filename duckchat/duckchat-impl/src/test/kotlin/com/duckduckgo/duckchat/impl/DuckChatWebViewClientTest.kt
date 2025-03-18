package com.duckduckgo.duckchat.impl

import android.webkit.WebView
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.common.utils.plugins.PluginPoint
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckChatWebViewClientTest {

    @Test
    fun whenOnPageStartedCalledThenJsPluginOnPageStartedInvoked() {
        val mockPlugin: JsInjectorPlugin = mock()
        val pluginPoint: PluginPoint<JsInjectorPlugin> = mock()
        whenever(pluginPoint.getPlugins()).thenReturn(listOf(mockPlugin))

        val duckChatWebViewClient = DuckChatWebViewClient(pluginPoint)
        val webView: WebView = mock()
        val testUrl = "https://example.com"

        duckChatWebViewClient.onPageStarted(webView, testUrl, null)

        verify(mockPlugin).onPageStarted(webView, testUrl, null)
    }
}
