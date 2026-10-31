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

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.pdf.PdfPageContext
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.RandomAccessFile
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class PdfPageContextProviderTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    @get:Rule val files = TemporaryFolder()
    private val testee by lazy { RealPdfPageContextProvider(coroutineRule.testDispatcherProvider) }

    @Test fun `metadata has no bytes until requested`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val metadata = testee.get("tab", false)!!
        assertNull(metadata.data)
        assertEquals(PdfPageContext.Status.AVAILABLE, metadata.status)
        assertEquals("document.pdf", metadata.title)
        assertEquals(file.readText(), String(Base64.getDecoder().decode(testee.get("tab", true)!!.data)))
    }

    @Test fun `oversized document never returns bytes`() = runTest {
        val file = files.newFile()
        RandomAccessFile(file, "rw").use { it.setLength(30L * 1024 * 1024 + 1) }
        testee.show("tab", Uri.fromFile(file), "large.pdf", "https://example.com/large.pdf")
        val result = testee.get("tab", true)!!
        assertEquals(PdfPageContext.Status.TOO_LARGE, result.status)
        assertNull(result.data)
    }

    @Test fun `evicted cache remains a PDF but cannot attach`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        file.delete()
        val result = testee.get("tab", true)!!
        assertEquals(PdfPageContext.Status.UNAVAILABLE, result.status)
        assertNull(result.data)
    }

    @Test fun `hidden document is not available to later requests`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        testee.hide("tab")
        assertNull(testee.get("tab", true))
    }

    @Test fun `document changed during IO is discarded`() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val dispatchers = org.mockito.kotlin.mock<com.duckduckgo.common.utils.DispatcherProvider>()
        org.mockito.kotlin.whenever(dispatchers.io()).thenReturn(dispatcher)
        val provider = RealPdfPageContextProvider(dispatchers)
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        provider.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val pending = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { provider.get("tab", true) }
        provider.hide("tab")
        assertEquals(PdfPageContext.Status.STALE, pending.await()!!.status)
        assertNull(pending.await()!!.data)
    }

    @Test fun `exact size ceiling is accepted`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4") }
        RandomAccessFile(file, "rw").use { it.setLength(30L * 1024 * 1024) }
        testee.show("tab", Uri.fromFile(file), "large.pdf", "https://example.com/large.pdf")
        assertEquals(PdfPageContext.Status.AVAILABLE, testee.get("tab", false)!!.status)
    }

    @Test fun `invalid cached bytes are unavailable`() = runTest {
        val file = files.newFile().apply { writeText("not a PDF") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        assertEquals(PdfPageContext.Status.UNAVAILABLE, testee.get("tab", true)!!.status)
    }
}
