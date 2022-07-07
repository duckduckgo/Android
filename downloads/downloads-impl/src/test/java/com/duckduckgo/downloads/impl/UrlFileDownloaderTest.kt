/*
 * Copyright (c) 2022 DuckDuckGo
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

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.downloads.api.DownloadCallback
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.FileDownloader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*
import retrofit2.Call
import retrofit2.Response
import java.io.File

@ExperimentalCoroutinesApi
class UrlFileDownloaderTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val downloadFileService: DownloadFileService = mock()
    private val call: Call<ResponseBody> = mock()
    private lateinit var realFileDownloadManager: RealFileDownloadManager

    private lateinit var urlFileDownloader: UrlFileDownloader

    @Before
    fun setup() {

        realFileDownloadManager = RealFileDownloadManager()
        whenever(downloadFileService.downloadFile(anyString(), anyString(), anyString())).thenReturn(call)

        urlFileDownloader = UrlFileDownloader(
            downloadFileService, realFileDownloadManager, coroutineRule.testDispatcherProvider, FakeCookieManagerWrapper()
        )
    }

    @Test
    fun whenDownloadFileSuccessfulThenCallOnStartInProgressAndSuccessCallbacks() = runTest {
        val pendingFileDownload = buildPendingDownload("https://example.com/file.txt")
        val filename = "file.txt"
        val downloadCallback = mock<DownloadCallback>()

        whenever(call.execute()).thenReturn(Response.success("success".toResponseBody()))

        urlFileDownloader.downloadFile(pendingFileDownload, filename, downloadCallback)

        verify(downloadCallback).onStart(any())
        verify(downloadCallback).onProgress(any(), eq(filename), eq(100))
        verify(downloadCallback).onSuccess(any(), eq("success".length.toLong()), eq(File(pendingFileDownload.directory, filename)), anyOrNull())
    }

    @Test
    fun whenDownloadFileFailedAsCancelledThenCallOnCancelCallback() = runTest {
        val pendingFileDownload = buildPendingDownload("https://example.com/file.txt")
        val filename = "file.txt"
        val downloadCallback = mock<DownloadCallback>()

        whenever(call.isCanceled).thenReturn(true)
        whenever(call.execute()).thenReturn(Response.success("success".toResponseBody()))
        // this is hacky, but it is to throw while writing file, which will cause response success but on error path
        whenever(downloadCallback.onProgress(any(), any(), any())).thenThrow(IllegalStateException("hacky throw"))

        urlFileDownloader.downloadFile(pendingFileDownload, filename, downloadCallback)

        verify(downloadCallback).onStart(any())
        verify(downloadCallback).onProgress(any(), any(), any())
        verify(downloadCallback, never()).onSuccess(any(), any(), any(), anyOrNull())
        verify(downloadCallback, never()).onError(any(), any(), any())

        verify(downloadCallback).onCancel(any())
    }

    @Test
    fun whenDownloadFileFailedAsWriteErrorThenCallOnCancelCallback() = runTest {
        val pendingFileDownload = buildPendingDownload("https://example.com/file.txt")
        val filename = "file.txt"
        val downloadCallback = mock<DownloadCallback>()

        whenever(call.isCanceled).thenReturn(false)
        whenever(call.execute()).thenReturn(Response.success("success".toResponseBody()))
        // this is hacky, but it is to throw while writing file, which will cause response success but on error path
        whenever(downloadCallback.onProgress(any(), any(), any())).thenThrow(IllegalStateException("hacky throw"))

        urlFileDownloader.downloadFile(pendingFileDownload, filename, downloadCallback)

        verify(downloadCallback).onStart(any())
        verify(downloadCallback).onProgress(any(), any(), any())
        verify(downloadCallback, never()).onSuccess(any(), any(), any(), anyOrNull())
        verify(downloadCallback, never()).onCancel(any())

        verify(downloadCallback).onError(any(), any(), eq(DownloadFailReason.Other))
    }

    @Test
    fun whenDownloadFileFailedAsErrorThenCallOnErrorCallback() = runTest {
        val pendingFileDownload = buildPendingDownload("https://example.com/file.txt")
        val filename = "file.txt"
        val downloadCallback = mock<DownloadCallback>()

        whenever(call.execute()).thenReturn(Response.error(400, "error".toResponseBody()))

        urlFileDownloader.downloadFile(pendingFileDownload, filename, downloadCallback)

        verify(downloadCallback).onStart(any())
        verify(downloadCallback, never()).onProgress(any(), any(), any())
        verify(downloadCallback, never()).onSuccess(any(), any(), any(), anyOrNull())
        verify(downloadCallback, never()).onCancel(any())

        verify(downloadCallback).onError(any(), any(), any())
    }

    @Test
    fun whenDownloadFileSucceedsAsCancelledThenCallOnCancelCallback() = runTest {
        val pendingFileDownload = buildPendingDownload("https://example.com/file.txt")
        val filename = "file.txt"
        val downloadCallback = mock<DownloadCallback>()

        whenever(call.isCanceled).thenReturn(true)
        whenever(call.execute()).thenReturn(Response.success("success".toResponseBody()))
        // this is hacky, but it is to throw while writing file, which will cause response success but on error path
        whenever(downloadCallback.onProgress(any(), any(), any())).thenThrow(IllegalStateException("hacky throw"))

        urlFileDownloader.downloadFile(pendingFileDownload, filename, downloadCallback)

        verify(downloadCallback).onStart(any())
        verify(downloadCallback).onProgress(any(), any(), any())
        verify(downloadCallback, never()).onSuccess(any(), any(), any(), anyOrNull())

        verify(downloadCallback).onCancel(any())
        verify(downloadCallback, never()).onError(any(), any(), any())
    }

    @Test
    fun whenDownloadFileSucceedsAsFailedToWriteThenCallOnErrorCallback() = runTest {
        val pendingFileDownload = buildPendingDownload("https://example.com/file.txt")
        val filename = "file.txt"
        val downloadCallback = mock<DownloadCallback>()

        whenever(call.isCanceled).thenReturn(false)
        whenever(call.execute()).thenReturn(Response.success("success".toResponseBody()))
        // this is hacky, but it is to throw while writing file, which will cause response success but on error path
        whenever(downloadCallback.onProgress(any(), any(), any())).thenThrow(IllegalStateException("hacky throw"))

        urlFileDownloader.downloadFile(pendingFileDownload, filename, downloadCallback)

        verify(downloadCallback).onStart(any())
        verify(downloadCallback).onProgress(any(), any(), any())
        verify(downloadCallback, never()).onSuccess(any(), any(), any(), anyOrNull())

        verify(downloadCallback, never()).onCancel(any())
        verify(downloadCallback, never()).onError(eq(pendingFileDownload.url), any(), eq(DownloadFailReason.ConnectionRefused))
    }

    private fun buildPendingDownload(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null,
    ): FileDownloader.PendingFileDownload {
        return FileDownloader.PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = "folder",
            userAgent = "user_agent",
            directory = File("directory"),
        )
    }

    private class FakeCookieManagerWrapper : CookieManagerWrapper {
        override fun getCookie(url: String): String? {
            return null
        }
    }
}
