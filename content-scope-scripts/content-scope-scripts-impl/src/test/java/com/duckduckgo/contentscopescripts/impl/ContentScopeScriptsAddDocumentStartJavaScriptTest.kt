package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScript
import com.duckduckgo.js.messaging.api.AddDocumentStartScriptDelegate
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContentScopeScriptsAddDocumentStartJavaScriptTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockWebViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val mockWebView: WebView = mock()
    private val mockActiveContentScopeExperiments: ContentScopeExperiments = mock()
    private val mockAddDocumentStartScriptDelegate: AddDocumentStartScriptDelegate = mock()
    private val mockAddDocumentStartJavaScript: AddDocumentStartJavaScript = mock()

    private lateinit var testee: ContentScopeScriptsAddDocumentStartJavaScript

    @Before
    fun setUp() = runTest {
        whenever(mockActiveContentScopeExperiments.getActiveExperiments()).thenReturn(listOf())
        whenever(mockAddDocumentStartScriptDelegate.createPlugin(any())).thenReturn(mockAddDocumentStartJavaScript)
        testee = ContentScopeScriptsAddDocumentStartJavaScript(
            mockWebViewCompatContentScopeScripts,
            mockActiveContentScopeExperiments,
            mockAddDocumentStartScriptDelegate,
        )
    }

    @Test
    fun whenAddDocumentStartJavaScriptCalledThenDelegateToCreatedPlugin() = runTest {
        testee.addDocumentStartJavaScript(mockWebView)

        verify(mockAddDocumentStartJavaScript).addDocumentStartJavaScript(mockWebView)
    }

    @Test
    fun whenConstructedThenCreatePluginWithCorrectStrategy() = runTest {
        verify(mockAddDocumentStartScriptDelegate).createPlugin(any())
    }
}
