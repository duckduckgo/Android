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

package com.duckduckgo.app.downloads

import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.downloader.DownloadFailReason
import com.duckduckgo.app.browser.downloader.DownloadFailReason.ConnectionRefused
import com.duckduckgo.app.browser.downloader.DownloadFailReason.DataUriParseException
import com.duckduckgo.app.browser.downloader.DownloadFailReason.DownloadManagerDisabled
import com.duckduckgo.app.browser.downloader.DownloadFailReason.Other
import com.duckduckgo.app.browser.downloader.DownloadFailReason.UnsupportedUrlType
import com.duckduckgo.app.downloads.model.DownloadItem
import com.duckduckgo.app.downloads.model.DownloadStatus
import com.duckduckgo.app.downloads.model.DownloadsRepository
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject

interface DownloadCallback {
    suspend fun onStart(downloadItem: DownloadItem)
    suspend fun onSuccess(downloadId: Long, contentLength: Long)
    suspend fun onSuccess(file: File, mimeType: String?)
    suspend fun onCancel(downloadId: Long)
    suspend fun onFailure(downloadId: Long? = null, url: String? = null, reason: DownloadFailReason)
    fun commands(): Flow<FileDownloadCallback.DownloadCommand>
}

class FileDownloadCallback @Inject constructor(
    private val downloadsRepository: DownloadsRepository,
    private val pixel: Pixel
) : DownloadCallback {

    sealed class DownloadCommand(@StringRes val messageId: Int, val showNotification: Boolean) {
        class ShowDownloadStartedMessage(
            @StringRes messageId: Int,
            showNotification: Boolean,
            val fileName: String
        ) : DownloadCommand(messageId, showNotification)
        class ShowDownloadSuccessMessage(
            @StringRes messageId: Int,
            showNotification: Boolean,
            val fileName: String,
            val filePath: String,
            val mimeType: String? = null
        ) : DownloadCommand(messageId, showNotification)
        class ShowDownloadFailedMessage(
            @StringRes messageId: Int,
            showNotification: Boolean,
            val showEnableDownloadManagerAction: Boolean
        ) : DownloadCommand(messageId, showNotification)
    }

    private val command = Channel<DownloadCommand>(1, BufferOverflow.DROP_OLDEST)

    override suspend fun onStart(downloadItem: DownloadItem) {
        Timber.d("Download started for file ${downloadItem.fileName}")
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_STARTED)
        command.send(
            DownloadCommand.ShowDownloadStartedMessage(
                messageId = R.string.downloadsDownloadStartedMessage,
                showNotification = downloadItem.downloadId == 0L,
                fileName = downloadItem.fileName
            )
        )
        downloadsRepository.insert(downloadItem)
    }

    override suspend fun onSuccess(downloadId: Long, contentLength: Long) {
        Timber.d("Download succeeded for file with downloadId $downloadId")
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_SUCCEEDED)
        downloadsRepository.update(downloadId = downloadId, downloadStatus = DownloadStatus.FINISHED, contentLength = contentLength)
        downloadsRepository.getDownloadItem(downloadId).let {
            command.send(
                DownloadCommand.ShowDownloadSuccessMessage(
                    messageId = R.string.downloadsDownloadFinishedMessage,
                    showNotification = false,
                    fileName = it.fileName,
                    filePath = it.filePath
                )
            )
        }
    }

    override suspend fun onSuccess(file: File, mimeType: String?) {
        Timber.d("Download succeeded for file with name ${file.name}")
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_SUCCEEDED)
        downloadsRepository.update(fileName = file.name, downloadStatus = DownloadStatus.FINISHED, contentLength = file.length())
        command.send(
            DownloadCommand.ShowDownloadSuccessMessage(
                messageId = R.string.downloadsDownloadFinishedMessage,
                showNotification = true,
                fileName = file.name,
                filePath = file.absolutePath,
                mimeType = mimeType
            )
        )
    }

    override suspend fun onCancel(downloadId: Long) {
        Timber.d("Download cancelled from the notification for file with downloadId $downloadId")
        // This will be marked as successful as the download started and it was deliberately cancelled by the user.
        // The DownloadManager also handles this as a SUCCESS, however it removes the record from its internal DB.
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_SUCCEEDED)
        downloadsRepository.delete(listOf(downloadId))
    }

    override suspend fun onFailure(downloadId: Long?, url: String?, reason: DownloadFailReason) {
        Timber.d("Failed to download file with downloadId $downloadId or url $url with reason $reason")
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_FAILED)
        val messageId = when (reason) {
            ConnectionRefused -> R.string.downloadsErrorMessage
            DownloadManagerDisabled -> R.string.downloadsDownloadManagerDisabledErrorMessage
            Other, UnsupportedUrlType, DataUriParseException -> R.string.downloadsDownloadGenericErrorMessage
        }
        command.send(
            DownloadCommand.ShowDownloadFailedMessage(
                messageId = messageId,
                showNotification = downloadId == 0L,
                showEnableDownloadManagerAction = reason == DownloadManagerDisabled
            )
        )
    }

    override fun commands(): Flow<DownloadCommand> {
        return command.receiveAsFlow()
    }
}
