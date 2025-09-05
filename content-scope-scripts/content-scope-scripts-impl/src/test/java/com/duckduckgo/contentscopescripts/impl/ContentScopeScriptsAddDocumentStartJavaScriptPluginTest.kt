package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContentScopeScriptsAddDocumentStartJavaScriptPluginTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockWebViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()
    private val mockWebView: WebView = mock()
    private val mockActiveContentScopeExperiments: ContentScopeExperiments = mock()

    private lateinit var testee: ContentScopeScriptsAddDocumentStartJavaScriptPlugin

    @Before
    fun setUp() = runTest {
        whenever(mockActiveContentScopeExperiments.getActiveExperiments()).thenReturn(listOf())
        testee = ContentScopeScriptsAddDocumentStartJavaScriptPlugin(
            mockWebViewCompatContentScopeScripts,
            coroutineRule.testDispatcherProvider,
            mockWebViewCapabilityChecker,
            mockWebViewCompatWrapper,
            mockActiveContentScopeExperiments,
            coroutineRule.testScope,
        )
    }

    @Test
    fun whenFeatureIsEnabledAndCapabilitySupportedThenCallScriptInjectionWithCorrectParams() = runTest {
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(true)
        whenever(mockWebViewCompatContentScopeScripts.getScript(any())).thenReturn("script")

        testee.addDocumentStartJavaScript(mockWebView)

        verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(mockWebView, "script", setOf("*"))
    }

    @Test
    fun whenFeatureIsDisabledAndCapabilitySupportedThenDoNotCallScriptInjection() = runTest {
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(false)
        whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(true)
        whenever(mockWebViewCompatContentScopeScripts.getScript(any())).thenReturn("script")

        testee.addDocumentStartJavaScript(mockWebView)

        verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(any(), any(), any())
    }

    @Test
    fun whenFeatureIsEnabledAndCapabilityNotSupportedThenDoNotCallScriptInjection() = runTest {
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(false)
        whenever(mockWebViewCompatContentScopeScripts.getScript(any())).thenReturn("script")

        testee.addDocumentStartJavaScript(mockWebView)

        verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(any(), any(), any())
    }
}
