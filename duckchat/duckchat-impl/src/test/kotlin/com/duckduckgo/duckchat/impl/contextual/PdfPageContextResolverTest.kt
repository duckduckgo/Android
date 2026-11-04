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

package com.duckduckgo.duckchat.impl.contextual

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.pdf.PdfContentResult
import com.duckduckgo.browser.api.pdf.PdfPageContext
import com.duckduckgo.browser.api.pdf.PdfPageContextProvider
import com.duckduckgo.duckchat.api.DuckAiPdfMetadata
import com.duckduckgo.duckchat.api.PageContextTrigger
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class PdfPageContextResolverTest {
    @get:org.junit.Rule val coroutineRule = com.duckduckgo.common.test.CoroutineTestRule()
    private val toggle: Toggle = mock { on { isEnabled() } doReturn true }
    private val feature: DuckChatFeature = mock { on { pdfPageContext() } doReturn toggle }
    private val provider: PdfPageContextProvider = mock()
    private val pixels: PageContextExtractionPixels = mock()
    private val testee = PdfPageContextResolver(feature, provider, pixels, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
    private val document = PdfPageContext("document.pdf", "https://example.com/document.pdf", PdfPageContext.Status.AVAILABLE, mock())

    @Test fun `automatic attachment off sends PDF title without bytes`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document)
        val result = testee.resolve("tab", null, false)!!.toJson()
        assertEquals("document.pdf", result.getString("title"))
        assertEquals("application/pdf", result.getString("mimeType"))
        assertFalse(result.has("data"))
        assertFalse(result.getBoolean("attached"))
        assertTrue(result.getBoolean("attachable"))
        verify(provider, never()).read(any())
        verifyNoInteractions(pixels)
    }

    @Test fun `metadata API never requests PDF bytes`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document)

        val result = testee.getMetadata("tab")!!

        assertEquals("document.pdf", result.title)
        assertEquals(DuckAiPdfMetadata.Availability.AVAILABLE, result.availability)
        verify(provider).getMetadata("tab")
        verify(provider, never()).read(any())
    }

    @Test fun `disabled metadata API does not read the PDF`() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)

        assertNull(testee.getMetadata("tab"))

        verifyNoInteractions(provider, pixels)
    }

    @Test fun `explicit attach sends base64 PDF`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document)
        whenever(provider.read(document.snapshot)).thenReturn(PdfContentResult.Success("%PDF-".toByteArray()))
        val result = testee.resolve("tab", null, true)!!.toJson()
        assertEquals("JVBERi0=", result.getString("data"))
        assertTrue(result.getBoolean("attached"))
        assertTrue(result.getBoolean("attachable"))
        assertEquals("", result.getString("content"))
        assertFalse(result.getBoolean("truncated"))
        assertEquals(0, result.getInt("fullContentLength"))
        assertEquals(0, result.getJSONArray("favicon").length())
    }

    @Test fun `too large is not attachable and has no bytes`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document.copy(status = PdfPageContext.Status.TOO_LARGE))
        whenever(provider.read(document.snapshot)).thenReturn(PdfContentResult.TooLarge)
        val result = testee.resolve("tab", null, true)!!.toJson()
        assertFalse(result.getBoolean("attachable"))
        assertFalse(result.has("data"))
    }

    @Test fun `oversized metadata does not report prevented extraction`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document.copy(status = PdfPageContext.Status.TOO_LARGE))
        whenever(provider.read(document.snapshot)).thenReturn(PdfContentResult.TooLarge)
        val result = testee.resolve("tab", null, false) as PdfPageContextResolver.Result.Pdf

        assertEquals(DuckAiPdfMetadata.Availability.TOO_LARGE, result.document.availability)
        assertNull(result.data)
        verifyNoInteractions(pixels)
    }

    @Test fun `oversized attachment reports prevented extraction`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document.copy(status = PdfPageContext.Status.TOO_LARGE))
        whenever(provider.read(document.snapshot)).thenReturn(PdfContentResult.TooLarge)
        testee.resolve("tab", null, true, PageContextTrigger.USER_REQUEST)

        verify(pixels).prevented(PageContextExtractionPixels.Category.DOCUMENT_TOO_LARGE, PageContextTrigger.USER_REQUEST)
    }

    @Test fun `stale PDF never becomes hidden web page context`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document.copy(status = PdfPageContext.Status.STALE))
        assertNull(testee.resolve("tab", "{\"content\":\"hidden page\"}", true))
    }

    @Test fun `disabled flag preserves markdown without reading PDF`() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertEquals(PdfPageContextResolver.Result.Page("markdown"), testee.resolve("tab", "markdown", true))
        verifyNoInteractions(provider, pixels)
    }

    @Test fun `navigation during encoding discards content and success pixel`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document, null)
        whenever(provider.read(document.snapshot)).thenReturn(PdfContentResult.Success("%PDF-".toByteArray()))
        assertNull(testee.resolve("tab", null, true))
        verifyNoInteractions(pixels)
    }

    @Test fun `successful read supplies available content even if earlier metadata was unavailable`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document.copy(status = PdfPageContext.Status.UNAVAILABLE))
        whenever(provider.read(document.snapshot)).thenReturn(PdfContentResult.Success("%PDF-".toByteArray()))
        val result = testee.resolve("tab", null, true)!!.toJson()
        assertTrue(result.getBoolean("attachable"))
        assertEquals("JVBERi0=", result.getString("data"))
    }

    @Test fun `sensitive result string representations are redacted`() {
        val metadata = DuckAiPdfMetadata("private.pdf", "https://private.example", DuckAiPdfMetadata.Availability.AVAILABLE)
        assertEquals("DuckAiPdfMetadata(availability=AVAILABLE)", metadata.toString())
        assertEquals("PdfPageContextResolver.Result.Pdf", PdfPageContextResolver.Result.Pdf(metadata, "private bytes").toString())
        assertEquals("PdfPageContextResolver.Result.Page", PdfPageContextResolver.Result.Page("private page").toString())
    }

    @Test fun `overlapping requests share bytes and encoded string but later requests read again`() = runTest {
        val pending = CompletableDeferred<PdfContentResult>()
        whenever(provider.getMetadata("tab")).thenReturn(document)
        whenever(provider.read(document.snapshot)).doSuspendableAnswer { pending.await() }
        val automatic = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true, PageContextTrigger.AUTO) }
        val manual = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true, PageContextTrigger.USER_REQUEST) }
        runCurrent()
        verify(provider, times(1)).read(document.snapshot)
        pending.complete(PdfContentResult.Success("%PDF-content".toByteArray()))
        val autoResult = automatic.await() as PdfPageContextResolver.Result.Pdf
        val manualResult = manual.await() as PdfPageContextResolver.Result.Pdf
        assertSame(autoResult.data, manualResult.data)
        testee.resolve("tab", null, true)
        verify(provider, times(2)).read(document.snapshot)
    }

    @Test fun `cancelling one request preserves shared work for the other`() = runTest {
        val pending = CompletableDeferred<PdfContentResult>()
        whenever(provider.getMetadata("tab")).thenReturn(document)
        whenever(provider.read(document.snapshot)).doSuspendableAnswer { pending.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true) }
        runCurrent()
        first.cancelAndJoin()
        pending.complete(PdfContentResult.Success("%PDF-content".toByteArray()))
        assertNotNull((second.await() as PdfPageContextResolver.Result.Pdf).data)
        verify(provider, times(1)).read(document.snapshot)
    }

    @Test fun `cancelling every request cancels shared read and next request retries`() = runTest {
        val pending = CompletableDeferred<PdfContentResult>()
        val cancelled = CompletableDeferred<Unit>()
        whenever(provider.getMetadata("tab")).thenReturn(document)
        whenever(provider.read(document.snapshot)).doSuspendableAnswer {
            try { pending.await() } finally { cancelled.complete(Unit) }
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true) }
        runCurrent()
        first.cancelAndJoin()
        assertFalse(cancelled.isCompleted)
        second.cancelAndJoin()
        cancelled.await()
        doReturn(PdfContentResult.Success("%PDF-content".toByteArray())).whenever(provider).read(document.snapshot)
        assertNotNull(testee.resolve("tab", null, true))
        verify(provider, times(2)).read(document.snapshot)
    }

    @Test fun `shared failure is released so a later attachment can succeed`() = runTest {
        val pending = CompletableDeferred<PdfContentResult>()
        whenever(provider.getMetadata("tab")).thenReturn(document)
        whenever(provider.read(document.snapshot)).doSuspendableAnswer { pending.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true) }
        pending.complete(PdfContentResult.Unavailable)
        assertNull((first.await() as PdfPageContextResolver.Result.Pdf).data)
        assertNull((second.await() as PdfPageContextResolver.Result.Pdf).data)
        doReturn(PdfContentResult.Success("%PDF-content".toByteArray())).whenever(provider).read(document.snapshot)
        assertNotNull((testee.resolve("tab", null, true) as PdfPageContextResolver.Result.Pdf).data)
        verify(provider, times(2)).read(document.snapshot)
    }

    @Test fun `request during final validation shares completed encoded content`() = runTest {
        val validating = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var lookups = 0
        whenever(provider.getMetadata("tab")).doSuspendableAnswer {
            if (++lookups == 2) {
                validating.complete(Unit)
                release.await()
            }
            document
        }
        whenever(provider.read(document.snapshot)).thenReturn(PdfContentResult.Success("%PDF-content".toByteArray()))
        val first = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true) }
        validating.await()
        val second = testee.resolve("tab", null, true) as PdfPageContextResolver.Result.Pdf
        release.complete(Unit)
        assertSame((first.await() as PdfPageContextResolver.Result.Pdf).data, second.data)
        verify(provider, times(1)).read(document.snapshot)
    }

    @Test fun `navigation discards unavailable outcome and its pixels`() = runTest {
        whenever(provider.getMetadata("tab")).thenReturn(document, document.copy(snapshot = mock()))
        whenever(provider.read(document.snapshot)).thenReturn(PdfContentResult.Unavailable)
        assertNull(testee.resolve("tab", null, true))
        verifyNoInteractions(pixels)
    }

    @Test fun `distinct document snapshots never share reads`() = runTest {
        val other = document.copy(snapshot = mock())
        val pending = CompletableDeferred<PdfContentResult>()
        whenever(provider.getMetadata("tab")).thenReturn(document)
        whenever(provider.getMetadata("other")).thenReturn(other)
        whenever(provider.read(any())).doSuspendableAnswer { pending.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("tab", null, true) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { testee.resolve("other", null, true) }
        runCurrent()
        verify(provider).read(document.snapshot)
        verify(provider).read(other.snapshot)
        pending.complete(PdfContentResult.Success("%PDF-content".toByteArray()))
        assertNotSame((first.await() as PdfPageContextResolver.Result.Pdf).data, (second.await() as PdfPageContextResolver.Result.Pdf).data)
    }
}
