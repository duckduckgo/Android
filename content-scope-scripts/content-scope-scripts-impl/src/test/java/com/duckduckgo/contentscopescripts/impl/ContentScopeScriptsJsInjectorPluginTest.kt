package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ContentScopeScriptsJsInjectorPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val mockWebView: WebView = mock()
    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()

    private lateinit var contentScopeScriptsJsInjectorPlugin: ContentScopeScriptsJsInjectorPlugin

    @Before
    fun setUp() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)
        contentScopeScriptsJsInjectorPlugin = ContentScopeScriptsJsInjectorPlugin(
            mockCoreContentScopeScripts,
            mockWebViewCapabilityChecker,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenPopulateMessagingParameters() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(null, listOf())).thenReturn("")
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null, listOf())

        verify(mockCoreContentScopeScripts).getScript(null, listOf())
        verify(mockWebView).evaluateJavascript(any(), anyOrNull())
    }

    @Test
    fun whenDisabledAndInjectContentScopeScriptsThenDoNothing() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(false)
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null, listOf())

        verifyNoInteractions(mockWebView)
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenUseParams() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(true, listOf())).thenReturn("")
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, true, listOf())

        verify(mockCoreContentScopeScripts).getScript(true, listOf())
    }

    @Test
    fun whenModernPathAvailableThenDoNotInject() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)
        val plugin = ContentScopeScriptsJsInjectorPlugin(
            mockCoreContentScopeScripts,
            mockWebViewCapabilityChecker,
            coroutineRule.testDispatcherProvider,
        )
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)

        plugin.onPageStarted(mockWebView, null, null, listOf())

        verifyNoInteractions(mockWebView)
    }
}
