package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ContentScopeScriptsJsInjectorPluginTest {
    @get:Rule
    val schedulers = InstantSchedulersRule()

    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val mockWebViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val mockDocumentStartPlugin: AddDocumentStartJavaScriptPlugin = mock()
    private val mockDocumentStartPlugins: PluginPoint<AddDocumentStartJavaScriptPlugin> = mock()
    private val mockDispatcherProvider: DispatcherProvider = mock()
    private val mockWebView: WebView = mock()

    private lateinit var contentScopeScriptsJsInjectorPlugin: ContentScopeScriptsJsInjectorPlugin

    @Before
    fun setUp() {
        whenever(mockDispatcherProvider.io()).thenReturn(schedulers.testScheduler)
        whenever(mockDispatcherProvider.main()).thenReturn(schedulers.testScheduler)
        whenever(mockDocumentStartPlugins.getPlugins()).thenReturn(listOf(mockDocumentStartPlugin))
        contentScopeScriptsJsInjectorPlugin =
            ContentScopeScriptsJsInjectorPlugin(
                mockCoreContentScopeScripts,
                mockWebViewCompatContentScopeScripts,
                mockDocumentStartPlugins,
                mockDispatcherProvider,
            )
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenPopulateMessagingParameters() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(false)
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
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(false)
        whenever(mockCoreContentScopeScripts.getScript(true, listOf())).thenReturn("")
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, true, listOf())

        verify(mockCoreContentScopeScripts).getScript(true, listOf())
    }

    @Test
    fun whenDocumentStartEnabledThenRefreshDocumentStartScriptsInsteadOfEvaluateJavascript() =
        runTest {
            whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
            whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(true)

            contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null, listOf())

            verify(mockDocumentStartPlugin).addDocumentStartJavaScript(mockWebView)
            verify(mockWebView, never()).evaluateJavascript(any(), anyOrNull())
        }
}
