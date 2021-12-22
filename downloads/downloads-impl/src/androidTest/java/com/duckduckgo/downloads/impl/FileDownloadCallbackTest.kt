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

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.downloads.api.DownloadCommand.ShowDownloadFailedMessage
import com.duckduckgo.downloads.api.DownloadCommand.ShowDownloadStartedMessage
import com.duckduckgo.downloads.api.DownloadCommand.ShowDownloadSuccessMessage
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.api.model.DownloadStatus.FINISHED
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName
import com.duckduckgo.downloads.store.DownloadsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
@Config(manifest = Config.NONE)
class FileDownloadCallbackTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockDownloadsRepository: DownloadsRepository = mock()

    private val mockPixel: Pixel = mock()

    private val mockFileDownloadNotificationManager: FileDownloadNotificationManager = mock()

    private lateinit var callback: FileDownloadCallback

    @Before
    fun before() {
        callback = FileDownloadCallback(
            fileDownloadNotificationManager = mockFileDownloadNotificationManager,
            downloadsRepository = mockDownloadsRepository,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = TestScope()
        )
    }

    @Test
    fun whenOnStartCalledThenPixelFiredAndItemInsertedAndDownloadStartedCommandSent() = runTest {
        val item = oneItem()

        callback.onStart(downloadItem = item)

        verify(mockPixel).fire(DownloadsPixelName.DOWNLOAD_REQUEST_STARTED)
        verify(mockDownloadsRepository).insert(downloadItem = item)
        verify(mockFileDownloadNotificationManager, never()).showDownloadInProgressNotification()
        callback.commands().test {
            val actualItem = awaitItem()
            assertTrue(actualItem is ShowDownloadStartedMessage)
            assertFalse(actualItem.showNotification)
            assertEquals(R.string.downloadsDownloadStartedMessage, actualItem.messageId)
            assertEquals(item.fileName, (actualItem as ShowDownloadStartedMessage).fileName)
        }
    }

    @Test
    fun whenOnSuccessCalledForDownloadIdThenPixelFiredAndItemUpdatedAndDownloadSuccessCommandSent() = runTest {
        val item = oneItem()
        val updatedContentLength = 20L
        whenever(mockDownloadsRepository.getDownloadItem(item.downloadId)).thenReturn(item.copy(contentLength = updatedContentLength))

        callback.onSuccess(downloadId = item.downloadId, contentLength = updatedContentLength)

        verify(mockPixel).fire(DownloadsPixelName.DOWNLOAD_REQUEST_SUCCEEDED)
        verify(mockDownloadsRepository).update(
            downloadId = item.downloadId,
            downloadStatus = FINISHED,
            contentLength = updatedContentLength
        )
        verify(mockFileDownloadNotificationManager, never()).showDownloadFinishedNotification(anyString(), any(), anyOrNull())
        callback.commands().test {
            val actualItem = awaitItem()
            assertTrue(actualItem is ShowDownloadSuccessMessage)
            assertFalse(actualItem.showNotification)
            assertEquals(R.string.downloadsDownloadFinishedMessage, actualItem.messageId)
            assertEquals(item.fileName, (actualItem as ShowDownloadSuccessMessage).fileName)
            assertEquals(item.filePath, actualItem.filePath)
        }
    }

    @Test
    fun whenOnSuccessCalledForFileThenPixelFiredAndItemUpdatedAndDownloadSuccessCommandSent() = runTest {
        val item = oneItem()
        val mimeType = "image/jpeg"
        val file = File(item.fileName)

        callback.onSuccess(file = file, mimeType = mimeType)

        verify(mockPixel).fire(DownloadsPixelName.DOWNLOAD_REQUEST_SUCCEEDED)
        verify(mockDownloadsRepository).update(
            fileName = item.fileName,
            downloadStatus = FINISHED,
            contentLength = file.length()
        )
        verify(mockFileDownloadNotificationManager).showDownloadFinishedNotification(file.name, file.absolutePath.toUri(), mimeType)
        callback.commands().test {
            val actualItem = awaitItem()
            assertTrue(actualItem is ShowDownloadSuccessMessage)
            assertTrue(actualItem.showNotification)
            assertEquals(R.string.downloadsDownloadFinishedMessage, actualItem.messageId)
            assertEquals(item.fileName, (actualItem as ShowDownloadSuccessMessage).fileName)
            assertEquals(item.filePath, actualItem.filePath)
            assertEquals(mimeType, actualItem.mimeType)
        }
    }

    @Test
    fun whenOnCancelCalledForDownloadIdThenPixelFiredAndItemDeleted() = runTest {
        val downloadId = 1L

        callback.onCancel(downloadId = downloadId)

        verify(mockPixel).fire(DownloadsPixelName.DOWNLOAD_REQUEST_CANCELLED)
        verify(mockDownloadsRepository).delete(
            downloadIdList = listOf(downloadId)
        )
    }

    @Test
    fun whenOnFailureCalledForDownloadIdAndUnsupportedUrlThenPixelFiredAndDownloadFailedCommandSent() = runTest {
        val downloadId = 1L
        val failReason = DownloadFailReason.UnsupportedUrlType

        callback.onFailure(downloadId = downloadId, url = "url", reason = failReason)

        verify(mockPixel).fire(DownloadsPixelName.DOWNLOAD_REQUEST_FAILED)
        verify(mockFileDownloadNotificationManager, never()).showDownloadFinishedNotification(anyString(), any(), anyOrNull())
        callback.commands().test {
            val actualItem = awaitItem()
            assertTrue(actualItem is ShowDownloadFailedMessage)
            assertFalse(actualItem.showNotification)
            assertEquals(R.string.downloadsDownloadGenericErrorMessage, actualItem.messageId)
            assertFalse((actualItem as ShowDownloadFailedMessage).showEnableDownloadManagerAction)
        }
    }

    @Test
    fun whenOnFailureCalledForConnectionRefusedThenPixelFiredAndDownloadFailedCommandSent() = runTest {
        val downloadId = 1L
        val failReason = DownloadFailReason.ConnectionRefused

        callback.onFailure(downloadId = downloadId, url = "url", reason = failReason)

        verify(mockPixel).fire(DownloadsPixelName.DOWNLOAD_REQUEST_FAILED)
        verify(mockFileDownloadNotificationManager, never()).showDownloadFinishedNotification(anyString(), any(), anyOrNull())
        callback.commands().test {
            val actualItem = awaitItem()
            assertTrue(actualItem is ShowDownloadFailedMessage)
            assertFalse(actualItem.showNotification)
            assertEquals(R.string.downloadsErrorMessage, actualItem.messageId)
            assertFalse((actualItem as ShowDownloadFailedMessage).showEnableDownloadManagerAction)
        }
    }

    @Test
    fun whenOnFailureCalledForDownloadManagerDisabledThenPixelFiredAndDownloadFailedCommandSent() = runTest {
        val downloadId = 1L
        val failReason = DownloadFailReason.DownloadManagerDisabled

        callback.onFailure(downloadId = downloadId, url = "url", reason = failReason)

        verify(mockPixel).fire(DownloadsPixelName.DOWNLOAD_REQUEST_FAILED)
        verify(mockFileDownloadNotificationManager, never()).showDownloadFinishedNotification(anyString(), any(), anyOrNull())
        callback.commands().test {
            val actualItem = awaitItem()
            assertTrue(actualItem is ShowDownloadFailedMessage)
            assertFalse(actualItem.showNotification)
            assertEquals(R.string.downloadsDownloadManagerDisabledErrorMessage, actualItem.messageId)
            assertTrue((actualItem as ShowDownloadFailedMessage).showEnableDownloadManagerAction)
        }
    }

    @Test
    fun whenOnFailureCalledForNoDownloadIdAndUnsupportedUrlThenPixelFiredAndNotificationSentAndDownloadFailedCommandSent() = runTest {
        val noDownloadId = 0L
        callback.onFailure(downloadId = noDownloadId, url = "url", reason = DownloadFailReason.UnsupportedUrlType)

        verify(mockPixel).fire(DownloadsPixelName.DOWNLOAD_REQUEST_FAILED)
        verify(mockFileDownloadNotificationManager).showDownloadFailedNotification()
        callback.commands().test {
            val actualItem = awaitItem()
            assertTrue(actualItem is ShowDownloadFailedMessage)
            assertTrue(actualItem.showNotification)
            assertEquals(R.string.downloadsDownloadGenericErrorMessage, actualItem.messageId)
            assertFalse((actualItem as ShowDownloadFailedMessage).showEnableDownloadManagerAction)
        }
    }

    private fun oneItem() =
        DownloadItem(
            id = 1L,
            downloadId = 10L,
            downloadStatus = FINISHED,
            fileName = "file.jpg",
            contentLength = 100L,
            createdAt = "2022-02-21T10:56:22",
            filePath = File("file.jpg").absolutePath
        )
}
