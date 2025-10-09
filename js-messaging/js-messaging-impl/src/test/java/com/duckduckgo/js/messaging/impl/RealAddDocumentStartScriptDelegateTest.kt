package com.duckduckgo.js.messaging.impl

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScript
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptScriptStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealAddDocumentStartScriptDelegateTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()
    private val mockWebView: WebView = mock()
    private val mockScriptHandler: ScriptHandler = mock()
    private lateinit var testee: RealAddDocumentStartScriptDelegate
    private lateinit var plugin: AddDocumentStartJavaScript

    @Before
    fun setUp() =
        runTest {
            testee =
                RealAddDocumentStartScriptDelegate(
                    mockWebViewCapabilityChecker,
                    coroutineRule.testDispatcherProvider,
                    mockWebViewCompatWrapper,
                )
        }

    @Test
    fun whenFeatureEnabledAndCapabilitySupportedThenInjectScript() =
        runTest {
            val mockStrategy = createMockStrategy(canInject = true, scriptString = "test script")
            plugin = testee.createPlugin(mockStrategy)
            whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(true)
            whenever(mockWebViewCompatWrapper.addDocumentStartJavaScript(any(), any(), any())).thenReturn(mockScriptHandler)

            plugin.addDocumentStartJavaScript(mockWebView)

            verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(mockWebView, "test script", setOf("*"))
        }

    @Test
    fun whenFeatureDisabledThenDoNotInjectScript() =
        runTest {
            val mockStrategy = createMockStrategy(canInject = false, scriptString = "test script")
            plugin = testee.createPlugin(mockStrategy)
            whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(true)

            plugin.addDocumentStartJavaScript(mockWebView)

            verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(any(), any(), any())
        }

    @Test
    fun whenCapabilityNotSupportedThenDoNotInjectScript() =
        runTest {
            val mockStrategy = createMockStrategy(canInject = true, scriptString = "test script")
            plugin = testee.createPlugin(mockStrategy)
            whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(false)

            plugin.addDocumentStartJavaScript(mockWebView)

            verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(any(), any(), any())
        }

    @Test
    fun whenScriptStringSameAsCurrentThenDoNotReinject() =
        runTest {
            val mockStrategy = createMockStrategy(canInject = true, scriptString = "test script")
            plugin = testee.createPlugin(mockStrategy)
            whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(true)
            whenever(mockWebViewCompatWrapper.addDocumentStartJavaScript(any(), any(), any())).thenReturn(mockScriptHandler)

            plugin.addDocumentStartJavaScript(mockWebView)
            plugin.addDocumentStartJavaScript(mockWebView)

            verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(mockWebView, "test script", setOf("*"))
        }

    @Test
    fun whenScriptStringDifferentThenRemoveOldAndInjectNew() =
        runTest {
            val mockStrategy: AddDocumentStartJavaScriptScriptStrategy = mock()
            whenever(mockStrategy.canInject()).thenReturn(true)
            whenever(mockStrategy.getScriptString()).thenReturn("script 1")
            whenever(mockStrategy.allowedOriginRules).thenReturn(setOf("*"))
            plugin = testee.createPlugin(mockStrategy)
            whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(true)
            whenever(mockWebViewCompatWrapper.addDocumentStartJavaScript(any(), any(), any())).thenReturn(mockScriptHandler)

            plugin.addDocumentStartJavaScript(mockWebView)

            whenever(mockStrategy.getScriptString()).thenReturn("script 2")

            plugin.addDocumentStartJavaScript(mockWebView)

            verify(mockScriptHandler).remove()
            verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(mockWebView, "script 2", setOf("*"))
        }

    private fun createMockStrategy(
        canInject: Boolean,
        scriptString: String,
    ): AddDocumentStartJavaScriptScriptStrategy =
        object : AddDocumentStartJavaScriptScriptStrategy {
            override suspend fun canInject(): Boolean = canInject

            override suspend fun getScriptString(): String = scriptString

            override val allowedOriginRules: Set<String> = setOf("*")

            override val context: String
                get() = "test"
        }
}
