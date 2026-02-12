/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.downloads.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.downloads.impl.DataUriParser.GeneratedFilename
import com.duckduckgo.downloads.impl.DataUriParser.ParseResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class DataUriDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mockDataUriParser: DataUriParser = mock()
    private val mockCallback: DownloadCallback = mock()

    private lateinit var dataUriDownloader: DataUriDownloader

    @Before
    fun setup() {
        dataUriDownloader = DataUriDownloader(dataUriParser = mockDataUriParser)
    }

    @Test
    fun whenDataUriIsInvalidThenOnErrorCalledWithUrl() {
        val url = "data:invalid"
        val pending = buildPendingDownload(url)

        whenever(mockDataUriParser.generate(url, null)).thenReturn(ParseResult.Invalid)

        dataUriDownloader.download(pending, mockCallback)

        verify(mockCallback).onError(url = url, reason = DownloadFailReason.DataUriParseException)
        verify(mockCallback, never()).onStart(any())
        verify(mockCallback, never()).onSuccess(any(), anyOrNull())
    }

    @Test
    fun whenDataUriIsValidAndWriteSucceedsThenOnSuccessCalled() {
        val url = "data:image/png;base64,aVZC"
        val pending = buildPendingDownload(url)
        val filename = GeneratedFilename(name = "image", fileType = "png")
        val parsedDataUri = ParseResult.ParsedDataUri(
            fileTypeGeneral = "image",
            fileTypeSpecific = "png",
            data = "aVZC",
            mimeType = "image/png",
            filename = filename,
        )

        whenever(mockDataUriParser.generate(url, null)).thenReturn(parsedDataUri)

        dataUriDownloader.download(pending, mockCallback)

        verify(mockCallback).onStart(any())
        verify(mockCallback).onSuccess(file = any(), mimeType = eq("image/png"))
        verify(mockCallback, never()).onError(anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun whenDataUriIsValidButWriteFailsThenOnErrorCalledWithUrl() {
        val url = "data:image/png;base64,!!invalid_base64!!"
        val pending = buildPendingDownload(url)
        val filename = GeneratedFilename(name = "image", fileType = "png")
        val parsedDataUri = ParseResult.ParsedDataUri(
            fileTypeGeneral = "image",
            fileTypeSpecific = "png",
            data = "!!invalid_base64!!",
            mimeType = "image/png",
            filename = filename,
        )

        whenever(mockDataUriParser.generate(url, null)).thenReturn(parsedDataUri)

        dataUriDownloader.download(pending, mockCallback)

        verify(mockCallback).onStart(any())
        verify(mockCallback).onError(url = eq(url), downloadId = any(), reason = eq(DownloadFailReason.DataUriParseException))
        verify(mockCallback, never()).onSuccess(any(), anyOrNull())
    }

    @Test
    fun whenDataUriIsInvalidThenOnStartNeverCalled() {
        val url = "data:invalid"
        val pending = buildPendingDownload(url)

        whenever(mockDataUriParser.generate(url, null)).thenReturn(ParseResult.Invalid)

        dataUriDownloader.download(pending, mockCallback)

        verify(mockCallback, never()).onStart(any())
    }

    @Test
    fun whenDownloadSucceedsThenFileIsCreatedInCorrectDirectory() {
        val url = "data:text/plain;base64,SGVsbG8="
        val pending = buildPendingDownload(url)
        val filename = GeneratedFilename(name = "text", fileType = "txt")
        val parsedDataUri = ParseResult.ParsedDataUri(
            fileTypeGeneral = "text",
            fileTypeSpecific = "plain",
            data = "SGVsbG8=",
            mimeType = "text/plain",
            filename = filename,
        )

        whenever(mockDataUriParser.generate(url, null)).thenReturn(parsedDataUri)

        dataUriDownloader.download(pending, mockCallback)

        verify(mockCallback).onSuccess(
            file = check { file ->
                assert(file.parentFile == pending.directory)
                assert(file.name == "text.txt")
            },
            mimeType = eq("text/plain"),
        )
    }

    private var folderCounter = 0

    private fun buildPendingDownload(url: String): PendingFileDownload {
        return PendingFileDownload(
            url = url,
            subfolder = "Downloads",
            directory = temporaryFolder.newFolder("downloads_${folderCounter++}"),
        )
    }
}
