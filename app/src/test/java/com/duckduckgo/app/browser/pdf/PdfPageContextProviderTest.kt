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
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.browser.api.pdf.PdfContentResult
import com.duckduckgo.browser.api.pdf.PdfPageContext
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class PdfPageContextProviderTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    @get:Rule val files = TemporaryFolder()
    private val regularTabs: TabsDao = mock()
    private val fireTabs: TabsDao = mock()
    private val tabChanges = MutableStateFlow(listOf("tab"))
    private val testee by lazy {
        RealPdfPageContextProvider(coroutineRule.testDispatcherProvider, regularTabs, fireTabs, coroutineRule.testScope.backgroundScope)
    }

    @Before fun setupTabs() {
        whenever(regularTabs.flowAllTabIds()).thenReturn(tabChanges)
        whenever(fireTabs.flowAllTabIds()).thenReturn(emptyFlow())
        whenever(regularTabs.tab(any())).thenReturn(mock())
    }

    @Test fun `metadata has no bytes until requested`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val metadata = testee.getMetadata("tab")!!
        assertEquals(PdfPageContext.Status.AVAILABLE, metadata.status)
        assertEquals("document.pdf", metadata.title)
        assertEquals(file.readText(), String((testee.read(metadata.snapshot) as PdfContentResult.Success).bytes))
    }

    @Test fun `oversized document never returns bytes`() = runTest {
        val file = files.newFile()
        RandomAccessFile(file, "rw").use { it.setLength(30L * 1024 * 1024 + 1) }
        testee.show("tab", Uri.fromFile(file), "large.pdf", "https://example.com/large.pdf")
        val result = testee.getMetadata("tab")!!
        assertEquals(PdfPageContext.Status.TOO_LARGE, result.status)
    }

    @Test fun `evicted cache remains a PDF but cannot attach`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        file.delete()
        val result = testee.getMetadata("tab")!!
        assertEquals(PdfPageContext.Status.UNAVAILABLE, result.status)
    }

    @Test fun `hidden document is not available to later requests`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        testee.hide("tab")
        assertNull(testee.getMetadata("tab"))
    }

    @Test fun `document changed during IO is discarded`() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val dispatchers = org.mockito.kotlin.mock<com.duckduckgo.common.utils.DispatcherProvider>()
        org.mockito.kotlin.whenever(dispatchers.io()).thenReturn(dispatcher)
        val provider = RealPdfPageContextProvider(dispatchers, regularTabs, fireTabs, backgroundScope)
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        provider.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val pending = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { provider.getMetadata("tab") }
        provider.hide("tab")
        assertEquals(PdfPageContext.Status.STALE, pending.await()!!.status)
    }

    @Test fun `exact size ceiling is accepted`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4") }
        RandomAccessFile(file, "rw").use { it.setLength(30L * 1024 * 1024) }
        testee.show("tab", Uri.fromFile(file), "large.pdf", "https://example.com/large.pdf")
        assertEquals(PdfPageContext.Status.AVAILABLE, testee.getMetadata("tab")!!.status)
    }

    @Test fun `invalid cached bytes are unavailable`() = runTest {
        val file = files.newFile().apply { writeText("not a PDF") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        assertEquals(PdfPageContext.Status.UNAVAILABLE, testee.getMetadata("tab")!!.status)
    }

    @Test fun `content read rejects previous revision even at same URL and URI`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        val uri = Uri.fromFile(file)
        testee.show("tab", uri, "document.pdf", "https://example.com/document.pdf")
        val metadata = testee.getMetadata("tab")!!
        testee.show("tab", uri, "document.pdf", "https://example.com/document.pdf")
        assertEquals(PdfContentResult.Stale, testee.read(metadata.snapshot))
    }

    @Test fun `content revalidates an evicted cache after metadata`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val metadata = testee.getMetadata("tab")!!
        file.delete()
        assertEquals(PdfContentResult.Unavailable, testee.read(metadata.snapshot))
    }

    @Test fun `content revalidates size and header after metadata`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val metadata = testee.getMetadata("tab")!!
        file.writeText("not a PDF document")
        assertEquals(PdfContentResult.Unavailable, testee.read(metadata.snapshot))
        RandomAccessFile(file, "rw").use { it.setLength(30L * 1024 * 1024 + 1) }
        assertEquals(PdfContentResult.TooLarge, testee.read(metadata.snapshot))
    }

    @Test fun `successful read at ceiling contains every byte`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-1.4") }
        RandomAccessFile(file, "rw").use { it.setLength(30L * 1024 * 1024) }
        testee.show("tab", Uri.fromFile(file), "large.pdf", "https://example.com/large.pdf")
        val result = testee.read(testee.getMetadata("tab")!!.snapshot) as PdfContentResult.Success
        assertEquals(30 * 1024 * 1024, result.bytes.size)
        assertArrayEquals("%PDF-".toByteArray(), result.bytes.take(5).toByteArray())
    }

    @Test fun `metadata and content string representations omit sensitive fields`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-private content") }
        testee.show("tab", Uri.fromFile(file), "private.pdf", "https://private.example/private.pdf")
        val metadata = testee.getMetadata("tab")!!
        assertEquals("PdfPageContext(status=AVAILABLE)", metadata.toString())
        assertEquals("PdfContentResult.Success", testee.read(metadata.snapshot).toString())
    }

    @Test fun `navigation during content IO discards the bytes`() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val dispatchers = org.mockito.kotlin.mock<com.duckduckgo.common.utils.DispatcherProvider>()
        org.mockito.kotlin.whenever(dispatchers.io()).thenReturn(dispatcher)
        val provider = RealPdfPageContextProvider(dispatchers, regularTabs, fireTabs, backgroundScope)
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        provider.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val metadata = provider.getMetadata("tab")!!
        val pending = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { provider.read(metadata.snapshot) }
        provider.hide("tab")
        assertEquals(PdfContentResult.Stale, pending.await())
    }

    @Test fun `cancelled content request propagates cancellation`() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val dispatchers = org.mockito.kotlin.mock<com.duckduckgo.common.utils.DispatcherProvider>()
        org.mockito.kotlin.whenever(dispatchers.io()).thenReturn(dispatcher)
        val provider = RealPdfPageContextProvider(dispatchers, regularTabs, fireTabs, backgroundScope)
        val file = files.newFile().apply { writeText("%PDF-1.4 document") }
        provider.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val metadata = provider.getMetadata("tab")!!
        val pending = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { provider.read(metadata.snapshot) }
        pending.cancel()
        pending.join()
        assertTrue(pending.isCancelled)
    }

    @Test fun `deleted tab is stale before membership observer emits`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        val metadata = testee.getMetadata("tab")!!
        whenever(regularTabs.tab("tab")).thenReturn(null)
        assertEquals(PdfContentResult.Stale, testee.read(metadata.snapshot))
        assertNull(testee.getMetadata("tab"))
    }

    @Test fun `membership observer clears registry after direct deletion`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        runCurrent()
        whenever(regularTabs.tab("tab")).thenReturn(null)
        tabChanges.value = emptyList()
        runCurrent()
        assertNull(testee.getMetadata("tab"))
    }

    @Test fun `late registration of deleted tab is removed without another database emission`() = runTest {
        whenever(regularTabs.tab("tab")).thenReturn(null)
        val file = files.newFile().apply { writeText("%PDF-document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        runCurrent()
        assertNull(testee.getMetadata("tab"))
    }

    @Test fun `stale membership emission does not delete an existing new tab`() = runTest {
        val file = files.newFile().apply { writeText("%PDF-document") }
        testee.show("new", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        tabChanges.value = emptyList()
        runCurrent()
        assertEquals(PdfPageContext.Status.AVAILABLE, testee.getMetadata("new")!!.status)
    }

    @Test fun `fire and undoable tabs retain their document when regular membership changes`() = runTest {
        whenever(regularTabs.tab("tab")).thenReturn(null)
        whenever(fireTabs.tab("tab")).thenReturn(TabEntity("tab", deletable = true))
        val file = files.newFile().apply { writeText("%PDF-document") }
        testee.show("tab", Uri.fromFile(file), "document.pdf", "https://example.com/document.pdf")
        tabChanges.value = emptyList()
        runCurrent()
        assertEquals(PdfPageContext.Status.AVAILABLE, testee.getMetadata("tab")!!.status)
    }
}
