package com.duckduckgo.app.browser.webview

import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealMaliciousSiteBlockerWebViewIntegrationTest {

    private lateinit var testee: RealMaliciousSiteBlockerWebViewIntegration
    private val maliciousSiteProtection: MaliciousSiteProtection = mock(MaliciousSiteProtection::class.java)
    private val maliciousUri = "http://malicious.com".toUri()
    private val exampleUri = "http://example.com".toUri()

    @Before
    fun setup() {
        testee = RealMaliciousSiteBlockerWebViewIntegration(maliciousSiteProtection)
        whenever(maliciousSiteProtection.isFeatureEnabled).thenReturn(true)
    }

    @Test
    fun `shouldOverrideUrlLoading returns false when feature is disabled`() = runTest {
        whenever(maliciousSiteProtection.isFeatureEnabled).thenReturn(false)

        val result = testee.shouldOverrideUrlLoading(exampleUri, null, true) {}
        assertFalse(result)
    }

    @Test
    fun `shouldInterceptRequest returns null when feature is disabled`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(exampleUri)
        whenever(maliciousSiteProtection.isFeatureEnabled).thenReturn(false)

        val result = testee.shouldIntercept(request, null) {}
        assertNull(result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns false when url is already processed`() = runTest {
        testee.processedUrls.add(exampleUri.toString())

        val result = testee.shouldOverrideUrlLoading(exampleUri, exampleUri, true) {}
        assertFalse(result)
    }

    @Test
    fun `shouldInterceptRequest returns result when feature is enabled, is malicious, and is mainframe`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(true)
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(true)

        val result = testee.shouldIntercept(request, maliciousUri) {}
        assertNotNull(result)
    }

    @Test
    fun `shouldInterceptRequest returns result when feature is enabled, is malicious, and is iframe`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(true)
        whenever(request.requestHeaders).thenReturn(mapOf("Sec-Fetch-Dest" to "iframe"))
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(true)

        val result = testee.shouldIntercept(request, maliciousUri) {}
        assertNotNull(result)
    }

    @Test
    fun `shouldInterceptRequest returns null when feature is enabled, is malicious, and is not mainframe nor iframe`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(false)
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(true)

        val result = testee.shouldIntercept(request, maliciousUri) {}
        assertNull(result)
    }

    @Test
    fun `shouldOverride returns false when feature is enabled, is malicious, and is not mainframe`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(true)

        val result = testee.shouldOverrideUrlLoading(maliciousUri, maliciousUri, false) {}
        assertFalse(result)
    }

    @Test
    fun `shouldOverride returns true when feature is enabled, is malicious, and is mainframe`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(true)

        val result = testee.shouldOverrideUrlLoading(maliciousUri, maliciousUri, true) {}
        assertTrue(result)
    }

    @Test
    fun `shouldOverride returns false when feature is enabled, is malicious, and not mainframe nor iframe`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(true)

        val result = testee.shouldOverrideUrlLoading(maliciousUri, maliciousUri, false) {}
        assertFalse(result)
    }

    @Test
    fun `shouldOverride returns true when feature is enabled, is malicious, and is mainframe but webView has different host`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(true)

        val result = testee.shouldOverrideUrlLoading(maliciousUri, exampleUri, true) {}
        assertFalse(result)
    }

    @Test
    fun `onPageLoadStarted clears processedUrls`() = runTest {
        testee.processedUrls.add(exampleUri.toString())
        testee.onPageLoadStarted()
        assertTrue(testee.processedUrls.isEmpty())
    }
}
