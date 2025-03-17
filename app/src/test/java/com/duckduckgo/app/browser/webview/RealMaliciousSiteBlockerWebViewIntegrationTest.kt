package com.duckduckgo.app.browser.webview

import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.webview.ExemptedUrlsHolder.ExemptedUrl
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.Ignored
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.MaliciousSite
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.Safe
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.ProcessedUrlStatus
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.ConfirmedResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.WaitForConfirmation
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Malicious
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealMaliciousSiteBlockerWebViewIntegrationTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val maliciousSiteProtection: MaliciousSiteProtection = mock(MaliciousSiteProtection::class.java)
    private val mockSettingsDataStore: SettingsDataStore = mock(SettingsDataStore::class.java)
    private val mockExemptedUrlsHolder = mock(ExemptedUrlsHolder::class.java)
    private val mockPixel: Pixel = mock()
    private val fakeAndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val maliciousUri = "http://malicious.com".toUri()
    private val exampleUri = "http://example.com".toUri()
    private val testee = RealMaliciousSiteBlockerWebViewIntegration(
        maliciousSiteProtection,
        androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
        mockSettingsDataStore,
        dispatchers = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
        isMainProcess = true,
        exemptedUrlsHolder = mockExemptedUrlsHolder,
        pixel = mockPixel,
    )

    @Before
    fun setup() {
        updateFeatureEnabled(true)
        whenever(mockSettingsDataStore.maliciousSiteProtectionEnabled).thenReturn(true)
        whenever(mockExemptedUrlsHolder.exemptedMaliciousUrls).thenReturn(emptySet())
    }

    @Test
    fun `shouldOverrideUrlLoading returns ignored when feature is disabled`() = runTest {
        updateFeatureEnabled(false)

        val result = testee.shouldOverrideUrlLoading(exampleUri, true) {}
        assertEquals(Ignored, result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns ignored when setting is disabled by user`() = runTest {
        whenever(mockSettingsDataStore.maliciousSiteProtectionEnabled).thenReturn(false)

        val result = testee.shouldOverrideUrlLoading(exampleUri, true) {}
        assertEquals(Ignored, result)
    }

    @Test
    fun `shouldInterceptRequest returns ignored when feature is disabled`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(exampleUri)
        whenever(request.isForMainFrame).thenReturn(false)
        updateFeatureEnabled(false)

        val result = testee.shouldIntercept(request, null) {}
        assertEquals(Ignored, result)
    }

    @Test
    fun `shouldInterceptRequest returns ignored when setting is disabled by user`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(exampleUri)
        whenever(mockSettingsDataStore.maliciousSiteProtectionEnabled).thenReturn(false)
        whenever(request.isForMainFrame).thenReturn(false)

        val result = testee.shouldIntercept(request, null) {}
        assertEquals(Ignored, result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns safe when url is already processed and safe`() = runTest {
        testee.processedUrls[exampleUri] = ProcessedUrlStatus(MaliciousStatus.Safe, false)

        val result = testee.shouldOverrideUrlLoading(exampleUri, true) {}
        assertEquals(Safe(true), result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns malicious when url is already processed and malicious`() = runTest {
        testee.processedUrls[exampleUri] = ProcessedUrlStatus(Malicious(MALWARE), false)

        val result = testee.shouldOverrideUrlLoading(exampleUri, true) {}
        assertEquals(MaliciousSite(exampleUri, MALWARE, exempted = false, clientSideHit = false), result)
    }

    @Test
    fun `shouldInterceptRequest returns result when feature is enabled, setting is enabled, is malicious, and is mainframe`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(true)
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldIntercept(request, maliciousUri) {}
        assertEquals(MaliciousSite(maliciousUri, MALWARE, exempted = false, clientSideHit = true), result)
    }

    @Test
    fun `shouldInterceptRequest returns safe when url is already processed and safe`() = runTest {
        testee.processedUrls[exampleUri] = ProcessedUrlStatus(MaliciousStatus.Safe, false)
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(exampleUri)
        whenever(request.isForMainFrame).thenReturn(true)
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldIntercept(request, maliciousUri) {}

        assertEquals(Safe(true), result)
    }

    @Test
    fun `shouldInterceptRequest returns malicious when url is already processed and malicious`() = runTest {
        testee.processedUrls[exampleUri] = ProcessedUrlStatus(Malicious(MALWARE), false)
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(exampleUri)
        whenever(request.isForMainFrame).thenReturn(true)
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldIntercept(request, maliciousUri) {}

        assertEquals(MaliciousSite(exampleUri, MALWARE, exempted = false, clientSideHit = false), result)
    }

    @Test
    fun `shouldInterceptRequest returns result when feature is enabled, setting is enabled, is malicious, and is iframe`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(false)
        whenever(request.requestHeaders).thenReturn(mapOf("Sec-Fetch-Dest" to "iframe", "Referer" to maliciousUri.toString()))
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldIntercept(request, maliciousUri) {}
        verify(mockPixel).fire(AppPixelName.MALICIOUS_SITE_DETECTED_IN_IFRAME, mapOf("category" to MALWARE.name.lowercase()))
        assertEquals(MaliciousSite(maliciousUri, MALWARE, exempted = false, clientSideHit = true), result)
    }

    @Test
    fun `shouldInterceptRequest returns ignored when feature is enabled, setting is enabled, is malicious, & is not mainframe or iframe`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(false)
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldIntercept(request, maliciousUri) {}
        assertEquals(Ignored, result)
    }

    @Test
    fun `shouldOverride returns ignored when feature is enabled, setting is enabled, is malicious, and is not mainframe`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldOverrideUrlLoading(maliciousUri, false) {}
        assertEquals(Ignored, result)
    }

    @Test
    fun `shouldOverride returns malicious when feature is enabled, setting is enabled, is malicious, and is mainframe`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldOverrideUrlLoading(maliciousUri, true) {}
        assertEquals(MaliciousSite(maliciousUri, MALWARE, exempted = false, clientSideHit = true), result)
    }

    @Test
    fun `shouldOverride returns ignored when feature is enabled, setting is enabled, is malicious, and not mainframe nor iframe`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldOverrideUrlLoading(maliciousUri, false) {}
        assertEquals(Ignored, result)
    }

    @Test
    fun `shouldIntercept returns safe when feature & setting enabled, is malicious, and is not mainframe but webView has different host`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(false)

        val result = testee.shouldIntercept(request, maliciousUri) {}
        assertEquals(Ignored, result)
    }

    @Test
    fun `onPageLoadStarted with different URL clears processedUrls`() = runTest {
        testee.processedUrls[exampleUri] = ProcessedUrlStatus(MaliciousStatus.Safe, false)
        testee.onPageLoadStarted("http://another.com")
        assertTrue(testee.processedUrls.isEmpty())
    }

    @Test
    fun `onPageLoadStarted with same URL does not clear processedUrls`() = runTest {
        testee.processedUrls[exampleUri] = ProcessedUrlStatus(MaliciousStatus.Safe, false)
        testee.onPageLoadStarted(exampleUri.toString())
        assertFalse(testee.processedUrls.isEmpty())
    }

    @Test
    fun `if a new page load triggering is malicious is started, isMalicious callback result should be ignored for the first page`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(true)

        val callbackChannel = Channel<Unit>()
        val firstCallbackDeferred = CompletableDeferred<Boolean>()
        val secondCallbackDeferred = CompletableDeferred<Boolean>()

        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<(Malicious) -> Unit>(1)

            launch {
                callbackChannel.receive()
                callback(Malicious(MALWARE))
            }
            WaitForConfirmation
        }

        testee.shouldOverrideUrlLoading(maliciousUri, true) { isMalicious ->
            firstCallbackDeferred.complete(isMalicious is Malicious)
        }

        testee.shouldOverrideUrlLoading(exampleUri, true) { isMalicious ->
            secondCallbackDeferred.complete(isMalicious is Malicious)
        }

        callbackChannel.send(Unit)
        callbackChannel.send(Unit)

        val firstCallbackResult = firstCallbackDeferred.await()
        val secondCallbackResult = secondCallbackDeferred.await()

        assertTrue(testee.processedUrls[maliciousUri]?.status is Malicious)
        assertEquals(false, firstCallbackResult)
        assertEquals(true, secondCallbackResult)
    }

    @Test
    fun `isMalicious callback result should be processed if no new page loads triggering isMalicious have started`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(true)

        val callbackChannel = Channel<Unit>()
        val firstCallbackDeferred = CompletableDeferred<Boolean>()
        val secondCallbackDeferred = CompletableDeferred<Boolean>()

        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<(MaliciousStatus) -> Unit>(1)

            launch {
                callbackChannel.receive()
                callback(Malicious(MALWARE))
            }
            WaitForConfirmation
        }

        testee.shouldOverrideUrlLoading(maliciousUri, true) { isMalicious ->
            firstCallbackDeferred.complete(isMalicious is Malicious)
        }

        callbackChannel.send(Unit)

        testee.shouldOverrideUrlLoading(exampleUri, true) { isMalicious ->
            secondCallbackDeferred.complete(isMalicious is Malicious)
        }

        callbackChannel.send(Unit)

        val firstCallbackResult = firstCallbackDeferred.await()
        val secondCallbackResult = secondCallbackDeferred.await()

        assertEquals(true, firstCallbackResult)
        assertEquals(true, secondCallbackResult)
    }

    @Test
    fun `onSiteExempted adds url to exemptedUrlsHolder`() = runTest {
        val url = "http://example.com".toUri()
        val feed = MALWARE

        testee.onSiteExempted(url, feed)

        verify(mockExemptedUrlsHolder).addExemptedMaliciousUrl(ExemptedUrl(url, feed))
    }

    @Test
    fun `shouldIntercept returns malicious with exempted when feature is enabled and site is exempted`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(true)
        whenever(mockExemptedUrlsHolder.exemptedMaliciousUrls).thenReturn(setOf(ExemptedUrl(maliciousUri, MALWARE)))

        val result = testee.shouldIntercept(request, maliciousUri) {}
        assertEquals(MaliciousSite(maliciousUri, MALWARE, exempted = true, clientSideHit = true), result)
    }

    @Test
    fun `shouldOverride returns malicious with exempted when feature is enabled and site is exempted`() = runTest {
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))
        whenever(mockExemptedUrlsHolder.exemptedMaliciousUrls).thenReturn(setOf(ExemptedUrl(maliciousUri, MALWARE)))

        val result = testee.shouldOverrideUrlLoading(maliciousUri, true) {}
        assertEquals(MaliciousSite(maliciousUri, MALWARE, exempted = true, clientSideHit = true), result)
    }

    @Test
    fun `shouldOverride called several times with same iframe URL without onPageStarted only fires pixel once`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn(maliciousUri)
        whenever(request.isForMainFrame).thenReturn(false)
        whenever(request.requestHeaders).thenReturn(mapOf("Sec-Fetch-Dest" to "iframe", "Referer" to maliciousUri.toString()))
        whenever(maliciousSiteProtection.isMalicious(any(), any())).thenReturn(ConfirmedResult(Malicious(MALWARE)))

        val result = testee.shouldIntercept(request, maliciousUri) {}
        assertEquals(MaliciousSite(maliciousUri, MALWARE, exempted = false, clientSideHit = true), result)

        val result2 = testee.shouldIntercept(request, maliciousUri) {}
        verify(mockPixel, times(1)).fire(AppPixelName.MALICIOUS_SITE_DETECTED_IN_IFRAME, mapOf("category" to MALWARE.name.lowercase()))
        verify(maliciousSiteProtection, times(1)).isMalicious(eq(maliciousUri), any())

        assertEquals(MaliciousSite(maliciousUri, MALWARE, exempted = false, clientSideHit = true), result2)
    }

    private fun updateFeatureEnabled(enabled: Boolean) {
        fakeAndroidBrowserConfigFeature.enableMaliciousSiteProtection().setRawStoredState(State(enabled))
        testee.onPrivacyConfigDownloaded()
    }
}
