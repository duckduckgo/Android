package com.duckduckgo.contentscopescripts.impl

import android.webkit.ValueCallback
import android.webkit.WebView
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isNull
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
    }

    @Test
    fun whenTracingThenDispatchSectionOpensAndClosesOnTheEvaluateCallback() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(null, listOf())).thenReturn("")

        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null, listOf())

        // The section must still be open at this point: it closes only when the renderer replies, which
        // is what makes the slice cover execution rather than just the hand-off.
        assertEquals(1, fakePerfTracer.asyncEvents.size)
        val begin = fakePerfTracer.asyncEvents.single()
        assertEquals("ddg.contentScope.dispatchJavascript", begin.name)
        assertTrue(begin.begin)

        val callback = argumentCaptor<ValueCallback<String>>()
        verify(mockWebView).evaluateJavascript(any(), callback.capture())
        callback.firstValue.onReceiveValue("null")

        assertEquals(2, fakePerfTracer.asyncEvents.size)
        val end = fakePerfTracer.asyncEvents[1]
        assertEquals("ddg.contentScope.dispatchJavascript", end.name)
        assertFalse(end.begin)
        assertEquals(begin.cookie, end.cookie)
    }

    @Test
    fun whenNotTracingThenNoCallbackIsSuppliedSoTheRendererNeverSerialisesAReply() {
        val plugin = ContentScopeScriptsJsInjectorPlugin(mockCoreContentScopeScripts, FakePerfTracer(enabled = false))
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(null, listOf())).thenReturn("")

        plugin.onPageStarted(mockWebView, null, null, listOf())

        verify(mockWebView).evaluateJavascript(any(), isNull())
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
