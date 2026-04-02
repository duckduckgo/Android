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
import org.robolectric.annotation.Config
import java.io.File

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
    @Config(sdk = [31])
    fun whenPdfMimeTypeThenShouldRenderInline() {
        assertTrue(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", null, "application/pdf"))
    }

    @Test
    @Config(sdk = [30])
    fun whenApiBelow31ThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", null, "application/pdf"))
    }

    @Test
    @Config(sdk = [31])
    fun whenContentDispositionIsAttachmentThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", "attachment; filename=doc.pdf", "application/pdf"))
    }

    @Test
    @Config(sdk = [31])
    fun whenMimeTypeIsNotPdfAndUrlNotPdfThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.txt", null, "text/plain"))
    }

    @Test
    @Config(sdk = [31])
    fun whenMimeTypeIsNotPdfButUrlEndsPdfThenShouldRenderInline() {
        assertTrue(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", null, "application/octet-stream"))
    }

    // endregion

    // region feature flag tests

    @Test
    fun whenFeatureDisabledThenDownloadToCacheReturnsNull() = runTest {
        androidBrowserConfigFeature.pdfViewer().setRawStoredState(State(enable = false))
        val pdfBytes = "%PDF-1.4 test content".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(pdfBytes)),
        )

        val uri = inlinePdfHandler.downloadToCache(server.url("/test.pdf").toString())

        assertNull(uri)
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
        val partialFile = File(cacheDir, "cancelled.pdf")
        assertFalse("Partial file should be deleted after cancellation", partialFile.exists())
    }

    // endregion
}
