package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import androidx.webkit.WebViewFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ContentScopeScriptsJsInjectorPluginTest {
    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val contentScopeScriptsFeature = FakeFeatureToggleFactory.create(ContentScopeScriptsFeature::class.java)
    private val mockWebView: WebView = mock()

    private lateinit var webViewFeatureMock: MockedStatic<WebViewFeature>
    private lateinit var contentScopeScriptsJsInjectorPlugin: ContentScopeScriptsJsInjectorPlugin

    @Before
    fun setUp() {
        webViewFeatureMock = mockStatic(WebViewFeature::class.java)
        contentScopeScriptsJsInjectorPlugin = ContentScopeScriptsJsInjectorPlugin(
            mockCoreContentScopeScripts,
            contentScopeScriptsFeature,
        )
    }

    @After
    fun tearDown() {
        webViewFeatureMock.close()
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
    fun whenDocumentStartAndWebMessagingEnabledThenDoNotInjectLegacyContentScopeScripts() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        webViewFeatureMock.`when`<Boolean> { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }.thenReturn(true)
        contentScopeScriptsFeature.useNewWebCompatApis().setRawStoredState(State(enable = true))
        contentScopeScriptsFeature.useWebMessageListener().setRawStoredState(State(enable = true))

        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null, listOf())

        verify(mockCoreContentScopeScripts, never()).getScript(anyOrNull(), any())
        verify(mockWebView, never()).evaluateJavascript(any(), anyOrNull())
    }

    @Test
    fun whenDocumentStartIsNotSupportedThenInjectLegacyContentScopeScripts() {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(null, listOf())).thenReturn("")
        webViewFeatureMock.`when`<Boolean> { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }.thenReturn(false)
        contentScopeScriptsFeature.useNewWebCompatApis().setRawStoredState(State(enable = true))
        contentScopeScriptsFeature.useWebMessageListener().setRawStoredState(State(enable = true))

        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null, listOf())

        verify(mockCoreContentScopeScripts).getScript(null, listOf())
        verify(mockWebView).evaluateJavascript(any(), anyOrNull())
    }
}
