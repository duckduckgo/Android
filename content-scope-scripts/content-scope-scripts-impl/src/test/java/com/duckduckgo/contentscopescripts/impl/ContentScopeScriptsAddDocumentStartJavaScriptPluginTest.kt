package com.duckduckgo.contentscopescripts.impl

import androidx.webkit.ScriptHandler
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ContentScopeScriptsAddDocumentStartJavaScriptPluginTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockWebViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val mockToggle = mock<Toggle>()
    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()

    private lateinit var testee: ContentScopeScriptsAddDocumentStartJavaScriptPlugin

    @Before
    fun setUp() = runTest {
        testee = ContentScopeScriptsAddDocumentStartJavaScriptPlugin(
            mockWebViewCompatContentScopeScripts,
            coroutineRule.testDispatcherProvider,
            mockWebViewCapabilityChecker,
        )
    }

    @Test
    fun whenFeatureIsEnabledAndCapabilitySupportedThenCallScriptInjectionWithCorrectParams() = runTest {
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(true)
        whenever(mockWebViewCompatContentScopeScripts.getScript(any())).thenReturn("script")
        var capturedScriptString: String? = null
        var capturedAllowedOriginRules: Set<String>? = null
        val scriptInjector: suspend (scriptString: String, allowedOriginRules: Set<String>) -> ScriptHandler? = { scriptString, allowedOriginRules ->
            capturedScriptString = scriptString
            capturedAllowedOriginRules = allowedOriginRules
            mock<ScriptHandler>()
        }

        testee.configureAddDocumentStartJavaScript(listOf(mockToggle), scriptInjector)

        assertEquals("script", capturedScriptString)
        assertEquals(setOf("*"), capturedAllowedOriginRules)
    }

    @Test
    fun whenFeatureIsDisabledAndCapabilitySupportedThenDoNotCallScriptInjection() = runTest {
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(false)
        whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(true)
        whenever(mockWebViewCompatContentScopeScripts.getScript(any())).thenReturn("script")

        var capturedScriptString: String? = null
        var capturedAllowedOriginRules: Set<String>? = null
        val scriptInjector: suspend (scriptString: String, allowedOriginRules: Set<String>) -> ScriptHandler? = { scriptString, allowedOriginRules ->
            capturedScriptString = scriptString
            capturedAllowedOriginRules = allowedOriginRules
            mock<ScriptHandler>()
        }

        testee.configureAddDocumentStartJavaScript(listOf(mockToggle), scriptInjector)

        assertEquals(null, capturedScriptString)
        assertEquals(null, capturedAllowedOriginRules)
    }

    @Test
    fun whenFeatureIsEnabledAndCapabilityNotSupportedThenDoNotCallScriptInjection() = runTest {
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(false)
        whenever(mockWebViewCompatContentScopeScripts.getScript(any())).thenReturn("script")

        var capturedScriptString: String? = null
        var capturedAllowedOriginRules: Set<String>? = null
        val scriptInjector: suspend (scriptString: String, allowedOriginRules: Set<String>) -> ScriptHandler? = { scriptString, allowedOriginRules ->
            capturedScriptString = scriptString
            capturedAllowedOriginRules = allowedOriginRules
            mock<ScriptHandler>()
        }

        testee.configureAddDocumentStartJavaScript(listOf(mockToggle), scriptInjector)

        assertEquals(null, capturedScriptString)
        assertEquals(null, capturedAllowedOriginRules)
    }
}
