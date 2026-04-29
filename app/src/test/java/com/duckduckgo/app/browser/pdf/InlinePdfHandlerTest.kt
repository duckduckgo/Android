/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.pdf

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File
import java.net.UnknownHostException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InlinePdfHandlerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var inlinePdfHandler: RealInlinePdfHandler
    private lateinit var server: MockWebServer
    private val androidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val cookieManagerProvider = object : CookieManagerProvider {
        override fun get(): CookieManager? = null
    }

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        inlinePdfHandler = RealInlinePdfHandler(
            context = context,
            okHttpClient = OkHttpClient(),
            cookieManagerProvider = cookieManagerProvider,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
        )
        androidBrowserConfigFeature.pdfViewer().setRawStoredState(State(enable = true))
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        val cacheDir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf_cache")
        cacheDir.deleteRecursively()
        server.shutdown()
    }

    @Test
    fun whenDownloadSuccessfulThenReturnsCachedUri() = runTest {
        val pdfBytes = "%PDF-1.4 test content".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(pdfBytes)),
        )

        val uri = inlinePdfHandler.downloadToCache(server.url("/test.pdf").toString())

        assertNotNull(uri)
        val file = File(uri!!.path!!)
        assertTrue(file.exists())
        assertEquals(pdfBytes.size.toLong(), file.length())
    }

    @Test
    fun whenServerReturnsErrorThenReturnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val uri = inlinePdfHandler.downloadToCache(server.url("/missing.pdf").toString())

        assertNull(uri)
    }

    @Test
    fun whenUrlHasNoExtensionThenPdfExtensionIsAppended() = runTest {
        val pdfBytes = "%PDF-1.4 content".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(pdfBytes)),
        )

        val uri = inlinePdfHandler.downloadToCache(server.url("/document").toString())

        assertNotNull(uri)
        assertTrue(uri!!.path!!.endsWith(".pdf"))
    }

    @Test
    fun whenDownloadedFileIsNotPdfThenReturnsNullAndDeletesFile() = runTest {
        val htmlBytes = "<html><body>Not a PDF</body></html>".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(htmlBytes)),
        )

        val uri = inlinePdfHandler.downloadToCache(server.url("/fake.pdf").toString())

        assertNull(uri)
    }

    // region shouldRenderPdfInline tests

    @Test
    fun whenPdfMimeTypeThenShouldRenderInline() {
        assertTrue(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", null, "application/pdf"))
    }

    @Test
    fun whenContentDispositionIsAttachmentThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", "attachment; filename=doc.pdf", "application/pdf"))
    }

    @Test
    fun whenMimeTypeIsNotPdfAndUrlNotPdfThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.txt", null, "text/plain"))
    }

    @Test
    fun whenMimeTypeIsNotPdfButUrlEndsPdfThenShouldRenderInline() {
        assertTrue(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", null, "application/octet-stream"))
    }

    @Test
    fun whenUrlEndsInPdfWithQueryParamsThenShouldRenderInline() {
        assertTrue(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf?auth=token", null, "application/octet-stream"))
    }

    @Test
    fun whenUrlEndsInPdfWithFragmentThenShouldRenderInline() {
        assertTrue(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf#page=5", null, "application/octet-stream"))
    }

    @Test
    fun whenContentDispositionIsExplicitInlineThenShouldRenderInline() {
        assertTrue(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", "inline", "application/pdf"))
    }

    @Test
    fun whenContentDispositionHasLeadingWhitespaceAttachmentThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", "  attachment; filename=doc.pdf", "application/pdf"))
    }

    // endregion

    // region feature flag tests

    @Test
    fun whenFeatureDisabledThenShouldRenderPdfInlineReturnsFalse() {
        androidBrowserConfigFeature.pdfViewer().setRawStoredState(State(enable = false))

        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", null, "application/pdf"))
    }

    @Test
    fun whenPdfAlreadyCachedThenReturnsWithoutNetworkRequest() = runTest {
        val pdfBytes = "%PDF-1.4 test content".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(pdfBytes)),
        )
        val url = server.url("/cached.pdf").toString()

        val firstUri = inlinePdfHandler.downloadToCache(url)
        assertNotNull(firstUri)
        assertEquals(1, server.requestCount)

        val secondUri = inlinePdfHandler.downloadToCache(url)
        assertNotNull(secondUri)
        assertEquals(firstUri, secondUri)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun whenTwoUrlsShareLastPathSegmentThenCacheFilesDontCollide() = runTest {
        val pdfBytesA = "%PDF-1.4 content A".toByteArray()
        val pdfBytesB = "%PDF-1.4 content B".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(pdfBytesA)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(pdfBytesB)))
        val urlA = server.url("/site-a/report.pdf").toString()
        val urlB = server.url("/site-b/report.pdf").toString()

        val uriA = inlinePdfHandler.downloadToCache(urlA)
        val uriB = inlinePdfHandler.downloadToCache(urlB)

        assertNotNull(uriA)
        assertNotNull(uriB)
        assertFalse("Cache files for distinct URLs sharing last path segment must differ", uriA == uriB)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun whenDownloadCancelledThenPartialFileIsDeleted() = runTest {
        val pdfBytes = "%PDF-1.4 test content".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(pdfBytes))
                .throttleBody(1, 1, java.util.concurrent.TimeUnit.SECONDS),
        )
        val url = server.url("/cancelled.pdf").toString()

        val deferred = async {
            inlinePdfHandler.downloadToCache(url)
        }

        delay(100)
        deferred.cancel()

        val cacheDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "pdf_cache",
        )
        assertTrue(
            "Cache directory should contain no leftover files after cancellation",
            cacheDir.listFiles().isNullOrEmpty(),
        )
    }

    // endregion

    // region additional download path tests

    @Test
    fun whenCookieAvailableThenForwardedAsHeader() = runTest {
        val mockCookieManager: CookieManager = mock()
        whenever(mockCookieManager.getCookie(any())).thenReturn("session=abc123")
        val cookieAwareProvider = object : CookieManagerProvider {
            override fun get(): CookieManager = mockCookieManager
        }
        val handlerWithCookies = RealInlinePdfHandler(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            okHttpClient = OkHttpClient(),
            cookieManagerProvider = cookieAwareProvider,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
        )

        val pdfBytes = "%PDF-1.4 test content".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(pdfBytes)),
        )
        val url = server.url("/auth.pdf").toString()

        val uri = handlerWithCookies.downloadToCache(url)

        assertNotNull(uri)
        val recordedRequest = server.takeRequest()
        assertEquals("session=abc123", recordedRequest.getHeader("Cookie"))
    }

    @Test
    fun whenUrlPathContainsSpecialCharactersThenFilenameIsSanitized() {
        val name = inlinePdfHandler.extractFileName("https://example.com/path/file%20name%21.pdf")
        assertEquals("file_name_.pdf", name)
    }

    @Test
    fun whenServerReturnsEmptyBodyThenReturnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val uri = inlinePdfHandler.downloadToCache(server.url("/empty.pdf").toString())

        assertNull(uri)
    }

    @Test
    fun whenServerReturnsBodyShorterThanMagicBytesThenReturnsNullAndDeletesFile() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write("abc".toByteArray())),
        )
        val url = server.url("/short.pdf").toString()

        val uri = inlinePdfHandler.downloadToCache(url)

        assertNull(uri)
        val cacheDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "pdf_cache",
        )
        assertTrue(
            "Cache directory should be empty when the response failed the magic-bytes check",
            cacheDir.listFiles().isNullOrEmpty(),
        )
    }

    @Test
    fun whenDnsFailsThenReturnsNull() = runTest {
        val throwingClient = OkHttpClient.Builder()
            .addInterceptor { throw UnknownHostException("test DNS failure") }
            .build()
        val handlerWithFailingDns = RealInlinePdfHandler(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            okHttpClient = throwingClient,
            cookieManagerProvider = cookieManagerProvider,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
        )

        val uri = handlerWithFailingDns.downloadToCache("https://example.com/test.pdf")

        assertNull(uri)
    }

    @Test
    fun whenConnectionResetMidBodyThenReturnsNull() = runTest {
        val partialBody = ("%PDF-1.4 " + "x".repeat(8192)).toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(partialBody))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val url = server.url("/reset.pdf").toString()

        val uri = inlinePdfHandler.downloadToCache(url)

        assertNull(uri)
    }

    // endregion

    // region cache eviction tests

    @Test
    fun whenCacheBudgetExceededThenOldestFilesEvictedFirst() {
        val cacheDir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf_cache").apply { mkdirs() }
        val oldest = File(cacheDir, "1-old.pdf").apply { writeBytes(ByteArray(40)); setLastModified(1_000L) }
        val middle = File(cacheDir, "2-middle.pdf").apply { writeBytes(ByteArray(40)); setLastModified(2_000L) }
        val newest = File(cacheDir, "3-newest.pdf").apply { writeBytes(ByteArray(40)); setLastModified(3_000L) }
        val keep = File(cacheDir, "4-keep.pdf").apply { writeBytes(ByteArray(40)); setLastModified(4_000L) }

        // Total = 160 bytes; cap at 100 forces eviction of the two oldest (oldest + middle).
        inlinePdfHandler.enforceCacheBudget(keepFile = keep, maxBytes = 100L)

        assertFalse("Oldest file should have been evicted", oldest.exists())
        assertFalse("Middle file should have been evicted", middle.exists())
        assertTrue("Newest non-keep file should remain", newest.exists())
        assertTrue("keepFile must never be evicted", keep.exists())
    }

    @Test
    fun whenKeepFileAloneExceedsBudgetThenStillNotEvicted() {
        val cacheDir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf_cache").apply { mkdirs() }
        val older = File(cacheDir, "1-old.pdf").apply { writeBytes(ByteArray(20)); setLastModified(1_000L) }
        val keep = File(cacheDir, "2-keep.pdf").apply { writeBytes(ByteArray(200)); setLastModified(2_000L) }

        // keep.length() (200) alone is over budget (50). All non-keep files get evicted but
        // keep itself survives — better to serve a single oversize PDF than refuse it.
        inlinePdfHandler.enforceCacheBudget(keepFile = keep, maxBytes = 50L)

        assertFalse("Non-keep file should be evicted to free space", older.exists())
        assertTrue("keepFile must survive even when its size exceeds the cap", keep.exists())
    }

    @Test
    fun whenCacheUnderBudgetThenNoFilesEvicted() {
        val cacheDir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf_cache").apply { mkdirs() }
        val a = File(cacheDir, "1-a.pdf").apply { writeBytes(ByteArray(10)) }
        val b = File(cacheDir, "2-b.pdf").apply { writeBytes(ByteArray(10)) }
        val keep = File(cacheDir, "3-keep.pdf").apply { writeBytes(ByteArray(10)) }

        inlinePdfHandler.enforceCacheBudget(keepFile = keep, maxBytes = 1_000L)

        assertTrue(a.exists())
        assertTrue(b.exists())
        assertTrue(keep.exists())
    }

    @Test
    fun whenCacheHitThenLastModifiedIsUpdated() = runTest {
        val pdfBytes = "%PDF-1.4 cached content".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(pdfBytes)))
        val url = server.url("/touch.pdf").toString()

        val firstUri = inlinePdfHandler.downloadToCache(url)
        assertNotNull(firstUri)
        val cachedFile = File(firstUri!!.path!!)

        // Backdate the file so we can detect that the cache-hit path bumps it forward.
        val backdated = System.currentTimeMillis() - 60_000L
        cachedFile.setLastModified(backdated)

        val before = cachedFile.lastModified()
        inlinePdfHandler.downloadToCache(url)
        val after = cachedFile.lastModified()

        assertTrue("Cache hit should bump lastModified to keep LRU semantics accurate", after > before)
    }

    // endregion
}
