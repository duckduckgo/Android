package com.duckduckgo.app.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
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
class RealDuckDuckGoWebViewTest {

    val testee: RealDuckDuckGoWebView = RealDuckDuckGoWebView(InstrumentationRegistry.getInstrumentation().targetContext)

    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()

    @Before
    fun setUp() {
        testee.webViewCompatWrapper = mockWebViewCompatWrapper
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
}
