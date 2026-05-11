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

package com.duckduckgo.duckchat.impl.ui.nativeinput.file

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealFileAttachmentProcessorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val contentResolver: ContentResolver = mock()
    private val context: Context = mock()
    private val uri: Uri = mock()

    private lateinit var processor: RealFileAttachmentProcessor

    @Before
    fun setUp() {
        whenever(context.contentResolver).thenReturn(contentResolver)
        processor = RealFileAttachmentProcessor(coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenQueryReturnsNullThenProcessFileReturnsNull() = runTest {
        whenever(contentResolver.query(uri, null, null, null, null)).thenReturn(null)

        assertNull(processor.processFile(context, uri))
    }

    @Test
    fun whenCursorIsEmptyThenProcessFileReturnsNull() = runTest {
        val cursor: Cursor = mock()
        whenever(contentResolver.query(uri, null, null, null, null)).thenReturn(cursor)
        whenever(cursor.moveToFirst()).thenReturn(false)

        assertNull(processor.processFile(context, uri))
    }

    @Test
    fun whenQueryThrowsThenProcessFileReturnsNull() = runTest {
        whenever(contentResolver.query(uri, null, null, null, null)).thenThrow(RuntimeException("query failed"))

        assertNull(processor.processFile(context, uri))
    }

    @Test
    fun whenOpenInputStreamReturnsNullThenProcessFileReturnsNull() = runTest {
        givenCursorReturns(fileName = "test.txt", fileSize = 100L)
        whenever(contentResolver.getType(uri)).thenReturn("text/plain")
        whenever(contentResolver.openInputStream(uri)).thenReturn(null)

        assertNull(processor.processFile(context, uri))
    }

    @Test
    fun whenAllFieldsPresentThenFileAttachmentHasCorrectFields() = runTest {
        givenCursorReturns(fileName = "report.txt", fileSize = 5L)
        whenever(contentResolver.getType(uri)).thenReturn("text/plain")
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream("hello".toByteArray()))

        val result = processor.processFile(context, uri)

        assertNotNull(result)
        assertEquals("report.txt", result!!.fileName)
        assertEquals(5L, result.sizeBytes)
        assertEquals("text/plain", result.mimeType)
        assertEquals("aGVsbG8=", result.base64Data)
        assertEquals(uri, result.uri)
        assertNull(result.pageCount)
    }

    @Test
    fun whenGetTypeReturnsNullThenMimeTypeDefaultsToOctetStream() = runTest {
        givenCursorReturns(fileName = "unknown", fileSize = 0L)
        whenever(contentResolver.getType(uri)).thenReturn(null)
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(ByteArray(0)))

        val result = processor.processFile(context, uri)

        assertEquals("application/octet-stream", result!!.mimeType)
    }

    @Test
    fun whenDisplayNameColumnMissingThenFileNameFallsBackToLastPathSegment() = runTest {
        val cursor: Cursor = mock()
        whenever(contentResolver.query(uri, null, null, null, null)).thenReturn(cursor)
        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(-1)
        whenever(cursor.getColumnIndex(OpenableColumns.SIZE)).thenReturn(0)
        whenever(cursor.getLong(0)).thenReturn(10L)
        whenever(uri.lastPathSegment).thenReturn("segment.txt")
        whenever(contentResolver.getType(uri)).thenReturn("text/plain")
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(ByteArray(0)))

        val result = processor.processFile(context, uri)

        assertEquals("segment.txt", result!!.fileName)
    }

    @Test
    fun whenDisplayNameColumnMissingAndLastPathSegmentIsNullThenFileNameFallsBackToFile() = runTest {
        val cursor: Cursor = mock()
        whenever(contentResolver.query(uri, null, null, null, null)).thenReturn(cursor)
        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(-1)
        whenever(cursor.getColumnIndex(OpenableColumns.SIZE)).thenReturn(0)
        whenever(cursor.getLong(0)).thenReturn(0L)
        whenever(uri.lastPathSegment).thenReturn(null)
        whenever(contentResolver.getType(uri)).thenReturn("text/plain")
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(ByteArray(0)))

        val result = processor.processFile(context, uri)

        assertEquals("file", result!!.fileName)
    }

    @Test
    fun whenSizeColumnMissingThenFileSizeFallsBackToActualByteCount() = runTest {
        val cursor: Cursor = mock()
        whenever(contentResolver.query(uri, null, null, null, null)).thenReturn(cursor)
        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0)
        whenever(cursor.getColumnIndex(OpenableColumns.SIZE)).thenReturn(-1)
        whenever(cursor.getString(0)).thenReturn("file.txt")
        whenever(contentResolver.getType(uri)).thenReturn("text/plain")
        val fileBytes = "hello world".toByteArray()
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(fileBytes))

        val result = processor.processFile(context, uri)

        assertEquals(fileBytes.size.toLong(), result!!.sizeBytes)
    }

    @Test
    fun whenSizeColumnNullThenFileSizeFallsBackToActualByteCount() = runTest {
        val cursor: Cursor = mock()
        whenever(contentResolver.query(uri, null, null, null, null)).thenReturn(cursor)
        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0)
        whenever(cursor.getColumnIndex(OpenableColumns.SIZE)).thenReturn(1)
        whenever(cursor.isNull(1)).thenReturn(true)
        whenever(cursor.getString(0)).thenReturn("file.txt")
        whenever(contentResolver.getType(uri)).thenReturn("text/plain")
        val fileBytes = "hello world".toByteArray()
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(fileBytes))

        val result = processor.processFile(context, uri)

        assertEquals(fileBytes.size.toLong(), result!!.sizeBytes)
    }

    @Test
    fun whenMimeTypeIsNotPdfThenPageCountIsNull() = runTest {
        givenCursorReturns(fileName = "image.png", fileSize = 100L)
        whenever(contentResolver.getType(uri)).thenReturn("image/png")
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream("data".toByteArray()))

        val result = processor.processFile(context, uri)

        assertNull(result!!.pageCount)
    }

    @Test
    fun whenMimeTypeIsPdfAndFileDescriptorIsNullThenPageCountIsNull() = runTest {
        givenCursorReturns(fileName = "doc.pdf", fileSize = 100L)
        whenever(contentResolver.getType(uri)).thenReturn("application/pdf")
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream("data".toByteArray()))
        whenever(contentResolver.openFileDescriptor(uri, "r")).thenReturn(null)

        val result = processor.processFile(context, uri)

        assertNull(result!!.pageCount)
    }

    @Test
    fun whenMimeTypeIsPdfAndFileDescriptorThrowsThenPageCountIsNullAndAttachmentIsStillReturned() = runTest {
        givenCursorReturns(fileName = "doc.pdf", fileSize = 100L)
        whenever(contentResolver.getType(uri)).thenReturn("application/pdf")
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream("data".toByteArray()))
        whenever(contentResolver.openFileDescriptor(uri, "r")).thenThrow(RuntimeException("fd error"))

        val result = processor.processFile(context, uri)

        assertNotNull(result)
        assertNull(result!!.pageCount)
    }

    private fun givenCursorReturns(fileName: String, fileSize: Long) {
        val cursor: Cursor = mock()
        whenever(contentResolver.query(uri, null, null, null, null)).thenReturn(cursor)
        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0)
        whenever(cursor.getColumnIndex(OpenableColumns.SIZE)).thenReturn(1)
        whenever(cursor.getString(0)).thenReturn(fileName)
        whenever(cursor.getLong(1)).thenReturn(fileSize)
    }
}
