package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ContentScopeScriptsJsInjectorPluginTest {
    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val mockWebView: WebView = mock()
    private val fakePerfTracer = FakePerfTracer()

    private lateinit var contentScopeScriptsJsInjectorPlugin: ContentScopeScriptsJsInjectorPlugin

    @Before
    fun setUp() {
        contentScopeScriptsJsInjectorPlugin = ContentScopeScriptsJsInjectorPlugin(mockCoreContentScopeScripts, fakePerfTracer)
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenPopulateMessagingParameters() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(null, listOf())).thenReturn("")
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null, listOf())

        verify(mockCoreContentScopeScripts).getScript(null, listOf())
        verify(mockWebView).evaluateJavascript(any(), anyOrNull())
        assertEquals(listOf("ddg.contentScope.evaluateJavascript"), fakePerfTracer.syncSections)
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
}
