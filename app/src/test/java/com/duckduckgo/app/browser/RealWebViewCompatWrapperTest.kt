package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        whenever(mockDuckDuckGoWebView.isAttachedToWindow).thenReturn(true)
        testee =
            RealWebViewCompatWrapper(
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                webViewCapabilityChecker = mockWebViewCapabilityChecker,
            )
    }

    @Test
    fun whenAddDocumentStartJavaScriptWithFeatureEnabledThenAddScript() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)

            testee.addDocumentStartJavaScript(mockDuckDuckGoWebView, "script", setOf("*"))

            verify(mockDuckDuckGoWebView).safeAddDocumentStartJavaScript("script", setOf("*"))
        }

    @Test
    fun whenAddDocumentStartJavaScriptWithWebViewDetachedThenDoNotAddScript() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)
            whenever(mockDuckDuckGoWebView.isAttachedToWindow).thenReturn(false)

            testee.addDocumentStartJavaScript(mockDuckDuckGoWebView, "script", setOf("*"))

            verify(mockDuckDuckGoWebView, never()).safeAddDocumentStartJavaScript("script", setOf("*"))
        }

    @Test
    fun whenAddDocumentStartJavaScriptWithCoroutineCancelledThenDoNotAddScript() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)

            coroutineRule.testScope
                .launch {
                    delay(50)
                    testee.addDocumentStartJavaScript(mockDuckDuckGoWebView, "script", setOf("*"))
                }.apply {
                    cancel()
                }

            verify(mockDuckDuckGoWebView, never()).safeAddDocumentStartJavaScript("script", setOf("*"))
        }

    @Test
    fun whenAddDocumentStartJavaScriptWithFeatureDisabledThenDoNotAddScript() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)

            testee.addDocumentStartJavaScript(mockDuckDuckGoWebView, "script", setOf("*"))

            verify(mockDuckDuckGoWebView, never()).safeAddDocumentStartJavaScript("script", setOf("*"))
        }

    @Test
    fun whenAddMessageListenerWithFeatureEnabledThenAddListener() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)

            testee.addWebMessageListener(mockDuckDuckGoWebView, "script", setOf("*")) { _, _, _, _, _ -> }

            verify(mockDuckDuckGoWebView).safeAddWebMessageListener(eq("script"), eq(setOf("*")), any())
        }

    @Test
    fun whenAddMessageListenerWithFeatureDisabledThenDoNotAddListener() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(false)

            testee.addWebMessageListener(mockDuckDuckGoWebView, "script", setOf("*")) { _, _, _, _, _ -> }

            verify(mockDuckDuckGoWebView, never()).safeAddWebMessageListener(eq("script"), eq(setOf("*")), any())
        }

    @Test
    fun whenAddMessageListenerWithWebViewDettchedThenDoNotAddListener() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)
            whenever(mockDuckDuckGoWebView.isAttachedToWindow).thenReturn(false)

            testee.addWebMessageListener(mockDuckDuckGoWebView, "script", setOf("*")) { _, _, _, _, _ -> }

            verify(mockDuckDuckGoWebView, never()).safeAddWebMessageListener(eq("script"), eq(setOf("*")), any())
        }

    @Test
    fun whenAddWebMessageListenerWithCoroutineCancelledThenDoNotAddListener() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)
            whenever(mockDuckDuckGoWebView.isAttachedToWindow).thenReturn(true)

            coroutineRule.testScope
                .launch {
                    delay(50)
                    testee.addWebMessageListener(mockDuckDuckGoWebView, "script", setOf("*")) { _, _, _, _, _ -> }
                }.apply {
                    cancel()
                }

            verify(mockDuckDuckGoWebView, never()).safeAddWebMessageListener(eq("script"), eq(setOf("*")), any())
        }

    @Test
    fun whenRemoveMessageListenerWithFeatureEnabledThenRemoveListener() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)

            testee.removeWebMessageListener(mockDuckDuckGoWebView, "script")

            verify(mockDuckDuckGoWebView).safeRemoveWebMessageListener(eq("script"))
        }

    @Test
    fun whenRemoveMessageListenerWithFeatureDisabledThenDoNotRemoveListener() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(false)

            testee.removeWebMessageListener(mockDuckDuckGoWebView, "script")

            verify(mockDuckDuckGoWebView, never()).safeRemoveWebMessageListener(eq("script"))
        }

    @Test
    fun whenRemoveMessageListenerWithWebViewDetachedThenDoNotRemoveListener() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)
            whenever(mockDuckDuckGoWebView.isAttachedToWindow).thenReturn(false)

            testee.removeWebMessageListener(mockDuckDuckGoWebView, "script")

            verify(mockDuckDuckGoWebView, never()).safeRemoveWebMessageListener(eq("script"))
        }

    @Test
    fun whenRemoveWebMessageListenerWithCoroutineCancelledThenDoNotRemoveListener() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)
            whenever(mockDuckDuckGoWebView.isAttachedToWindow).thenReturn(true)

            coroutineRule.testScope
                .launch {
                    delay(50)
                    testee.removeWebMessageListener(mockDuckDuckGoWebView, "script")
                }.apply {
                    cancel()
                }

            verify(mockDuckDuckGoWebView, never()).safeRemoveWebMessageListener(eq("script"))
        }
}
