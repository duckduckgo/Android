package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

class RealWebViewCompatWrapperTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockDuckDuckGoWebView: DuckDuckGoWebView = mock()

    private lateinit var testee: RealWebViewCompatWrapper

    @Before
    fun setUp() {
        testee = RealWebViewCompatWrapper(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            webViewCapabilityChecker = mockWebViewCapabilityChecker,
        )
    }

    @Test
    fun whenAddDocumentStartJavaScriptWithFeatureEnabledThenAddScript() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(eq(DocumentStartJavaScript), any())).thenReturn(true)

        testee.addDocumentStartJavaScript(mockDuckDuckGoWebView, "script", setOf("*"))

        verify(mockDuckDuckGoWebView).safeAddDocumentStartJavaScript("script", setOf("*"))
    }

    @Test
    fun whenAddDocumentStartJavaScriptWithFeatureDisabledThenDoNotAddScript() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(eq(DocumentStartJavaScript), any())).thenReturn(false)

        testee.addDocumentStartJavaScript(mockDuckDuckGoWebView, "script", setOf("*"))

        verify(mockDuckDuckGoWebView, never()).safeAddDocumentStartJavaScript("script", setOf("*"))
    }

    @Test
    fun whenAddMessageListenerWithFeatureEnabledThenAddListener() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(eq(WebMessageListener), any())).thenReturn(true)

        testee.addWebMessageListener(mockDuckDuckGoWebView, "script", setOf("*")) { _, _, _, _, _ -> }

        verify(mockDuckDuckGoWebView).safeAddWebMessageListener(eq("script"), eq(setOf("*")), any())
    }

    @Test
    fun whenAddMessageListenerWithFeatureDisabledThenDoNotAddListener() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(eq(WebMessageListener), any())).thenReturn(false)

        testee.addWebMessageListener(mockDuckDuckGoWebView, "script", setOf("*")) { _, _, _, _, _ -> }

        verify(mockDuckDuckGoWebView, never()).safeAddWebMessageListener(eq("script"), eq(setOf("*")), any())
    }

    @Test
    fun whenRemoveMessageListenerWithFeatureEnabledThenRemoveListener() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(eq(WebMessageListener), any())).thenReturn(true)

        testee.removeWebMessageListener(mockDuckDuckGoWebView, "script")

        verify(mockDuckDuckGoWebView).safeRemoveWebMessageListener("script")
    }

    @Test
    fun whenRemoveMessageListenerWithFeatureDisabledThenDoNotRemoveListener() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(eq(WebMessageListener), any())).thenReturn(false)

        testee.removeWebMessageListener(mockDuckDuckGoWebView, "script")

        verify(mockDuckDuckGoWebView, never()).safeRemoveWebMessageListener("script")
    }
}
