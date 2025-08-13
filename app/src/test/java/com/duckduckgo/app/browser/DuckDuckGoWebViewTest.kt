package com.duckduckgo.app.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat.WebMessageListener
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.contentscopescripts.impl.WebViewCompatWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckDuckGoWebViewTest {

    val testee: DuckDuckGoWebView = DuckDuckGoWebView(InstrumentationRegistry.getInstrumentation().targetContext)

    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()
    private val mockWebMessageListener: WebMessageListener = mock()

    @Before
    fun setUp() {
        testee.webViewCompatWrapper = mockWebViewCompatWrapper
        testee.webViewCapabilityChecker = mockWebViewCapabilityChecker
    }

    @Test
    fun whenSafeAddDocumentStartJavaScriptWithFeatureEnabledThenAddScript() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)

        testee.safeAddDocumentStartJavaScript("script", setOf("*"))

        verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(testee, "script", setOf("*"))
    }

    @Test
    fun whenSafeAddDocumentStartJavaScriptWithFeatureDisabledThenDoNotAddScript() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)

        testee.safeAddDocumentStartJavaScript("script", setOf("*"))

        verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(testee, "script", setOf("*"))
    }

    @Test
    fun whenSafeAddWebMessageListenerWithFeatureEnabledThenAddListener() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(true)

        testee.safeAddWebMessageListener("test", setOf("*"), mockWebMessageListener)
        verify(mockWebViewCompatWrapper)
            .addWebMessageListener(testee, "test", setOf("*"), mockWebMessageListener)
    }

    @Test
    fun whenSafeAddWebMessageListenerWithFeatureDisabledThenDoNotAddListener() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(false)

        testee.safeAddWebMessageListener("test", setOf("*"), mockWebMessageListener)
        verify(mockWebViewCompatWrapper, never())
            .addWebMessageListener(testee, "test", setOf("*"), mockWebMessageListener)
    }

    @Test
    fun whenSafeRemoveWebMessageListenerWithFeatureEnabledThenRemoveListener() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(true)

        testee.safeRemoveWebMessageListener("test")

        verify(mockWebViewCompatWrapper).removeWebMessageListener(testee, "test")
    }

    @Test
    fun whenSafeRemoveWebMessageListenerWithFeatureDisabledThenDoNotRemoveListener() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(false)

        testee.safeRemoveWebMessageListener("test")
        verify(mockWebViewCompatWrapper, never()).removeWebMessageListener(testee, "test")
    }
}
