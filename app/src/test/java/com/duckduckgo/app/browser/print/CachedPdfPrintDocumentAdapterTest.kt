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

package com.duckduckgo.app.browser.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CachedPdfPrintDocumentAdapterTest {

    @Test
    fun whenLayoutThenReportsPdfDocumentInfoWithDisplayName() {
        val adapter = CachedPdfPrintDocumentAdapter(createTempPdf("hello", "%PDF-1.4 hello".toByteArray()), "hello.pdf")
        val callback = mock<PrintDocumentAdapter.LayoutResultCallback>()

        adapter.onLayout(null, PrintAttributes.Builder().build(), null, callback, null)

        val captor = argumentCaptor<PrintDocumentInfo>()
        verify(callback).onLayoutFinished(captor.capture(), any())
        assertEquals("hello.pdf", captor.firstValue.name)
        assertEquals(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT, captor.firstValue.contentType)
    }

    @Test
    fun whenLayoutAndCancellationAlreadyTriggeredThenLayoutCancelled() {
        val adapter = CachedPdfPrintDocumentAdapter(createTempPdf("doc", "%PDF-1.4".toByteArray()), "doc.pdf")
        val callback = mock<PrintDocumentAdapter.LayoutResultCallback>()
        val signal = CancellationSignal().apply { cancel() }

        adapter.onLayout(null, PrintAttributes.Builder().build(), signal, callback, null)

        verify(callback).onLayoutCancelled()
        verify(callback, never()).onLayoutFinished(any(), any())
    }

    @Test
    fun whenWriteThenPdfFileBytesCopiedToDestination() {
        val pdfBytes = "%PDF-1.4 lorem ipsum dolor sit amet".toByteArray()
        val adapter = CachedPdfPrintDocumentAdapter(createTempPdf("doc", pdfBytes), "doc.pdf")
        val outFile = File.createTempFile("printed", ".pdf").apply { deleteOnExit() }
        val pfd = ParcelFileDescriptor.open(
            outFile,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE,
        )
        val callback = mock<PrintDocumentAdapter.WriteResultCallback>()

        adapter.onWrite(arrayOf(PageRange.ALL_PAGES), pfd, null, callback)
        pfd.close()

        verify(callback).onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        assertArrayEquals(pdfBytes, outFile.readBytes())
    }

    @Test
    fun whenWriteAndSourceFileMissingThenFailureReported() {
        val ghostFile = File.createTempFile("ghost", ".pdf").apply { delete() }
        val adapter = CachedPdfPrintDocumentAdapter(ghostFile, "ghost.pdf")
        val outFile = File.createTempFile("printed", ".pdf").apply { deleteOnExit() }
        val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_WRITE_ONLY)
        val callback = mock<PrintDocumentAdapter.WriteResultCallback>()

        adapter.onWrite(arrayOf(PageRange.ALL_PAGES), pfd, null, callback)
        pfd.close()

        verify(callback).onWriteFailed(any())
        verify(callback, never()).onWriteFinished(any())
    }

    private fun createTempPdf(prefix: String, bytes: ByteArray): File =
        File.createTempFile(prefix, ".pdf").apply {
            deleteOnExit()
            writeBytes(bytes)
        }
}
