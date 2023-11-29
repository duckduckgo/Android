package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import com.duckduckgo.app.global.model.Site
import java.util.*
import org.junit.Assert.*
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

    private lateinit var contentScopeScriptsJsInjectorPlugin: ContentScopeScriptsJsInjectorPlugin

    @Before
    fun setUp() {
        contentScopeScriptsJsInjectorPlugin = ContentScopeScriptsJsInjectorPlugin(mockCoreContentScopeScripts)
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenPopulateMessagingParameters() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(null)).thenReturn("")
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        verify(mockCoreContentScopeScripts).getScript(null)
        verify(mockWebView).evaluateJavascript(any(), anyOrNull())
    }

    @Test
    fun whenDisabledAndInjectContentScopeScriptsThenDoNothing() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(false)
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        verifyNoInteractions(mockWebView)
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenUseSite() {
        val site: Site = mock()
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(site)).thenReturn("")
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, site)

        verify(mockCoreContentScopeScripts).getScript(site)
    }
}
