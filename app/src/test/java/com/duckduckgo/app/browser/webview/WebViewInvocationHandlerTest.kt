package com.duckduckgo.app.browser.webview

import android.print.PrintDocumentAdapter
import com.duckduckgo.app.browser.DuckDuckGoWebView
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

class WebViewInvocationHandlerTest {
    private val mockWebView: DuckDuckGoWebView = mock()
    private val proxy: DuckDuckGoWebView = createSafeWebViewProxy(mockWebView)
    private val mockPrintDocumentAdapter: PrintDocumentAdapter = mock()

    @Test
    fun whenWebViewNotDestroyedThenInvokeMethod() {
        whenever(mockWebView.isDestroyed).thenReturn(false)
        whenever(mockWebView.getUrl()).thenReturn(EXAMPLE_URL)

        assertEquals(EXAMPLE_URL, proxy.getUrl())
        verify(mockWebView).getUrl()
    }

    @Test
    fun whenWebViewDestroyedThenDoNotInvokeMethod() {
        whenever(mockWebView.isDestroyed).thenReturn(true)
        whenever(mockWebView.getUrl()).thenReturn(EXAMPLE_URL)

        assertNull(proxy.getUrl())
        verify(mockWebView, never()).getUrl()
    }

    @Test
    fun whenMethodHasArgumentsThenInvokeWithArguments() {
        whenever(mockWebView.isDestroyed).thenReturn(false)
        whenever(mockWebView.createPrintDocumentAdapter(EXAMPLE_URL)).thenReturn(mockPrintDocumentAdapter)

        assertEquals(mockPrintDocumentAdapter, proxy.createPrintDocumentAdapter(EXAMPLE_URL))
        verify(mockWebView).createPrintDocumentAdapter(EXAMPLE_URL)
    }

    @Test
    fun whenWebViewDestroyedThenMethodWithArgumentsNotInvoked() {
        whenever(mockWebView.isDestroyed).thenReturn(true)
        whenever(mockWebView.createPrintDocumentAdapter(EXAMPLE_URL)).thenReturn(mockPrintDocumentAdapter)

        assertNull(proxy.createPrintDocumentAdapter(EXAMPLE_URL))
        verify(mockWebView, never()).createPrintDocumentAdapter(EXAMPLE_URL)
    }

    companion object {
        private const val EXAMPLE_URL = "https://example.com"
    }
}
