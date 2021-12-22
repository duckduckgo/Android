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
import androidx.core.net.toUri
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
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

interface DownloadCallback {
    fun onStart(downloadItem: DownloadItem)
    fun onSuccess(downloadId: Long, contentLength: Long)
    fun onSuccess(file: File, mimeType: String?)
    fun onCancel(downloadId: Long)
    fun onFailure(downloadId: Long? = null, url: String? = null, reason: DownloadFailReason)
    fun commands(): Flow<FileDownloadCallback.DownloadCommand>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class FileDownloadCallback @Inject constructor(
    private val fileDownloadNotificationManager: FileDownloadNotificationManager,
    private val downloadsRepository: DownloadsRepository,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
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

    override fun onStart(downloadItem: DownloadItem) {
        Timber.d("Download started for file ${downloadItem.fileName}")
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_STARTED)
        val downloadStartedMessage = DownloadCommand.ShowDownloadStartedMessage(
            messageId = R.string.downloadsDownloadStartedMessage,
            showNotification = downloadItem.downloadId == 0L,
            fileName = downloadItem.fileName
        )
        if (downloadStartedMessage.showNotification) {
            fileDownloadNotificationManager.showDownloadInProgressNotification()
        }
        appCoroutineScope.launch(dispatchers.io()) {
            command.send(downloadStartedMessage)
            downloadsRepository.insert(downloadItem)
        }
    }

    override fun onSuccess(downloadId: Long, contentLength: Long) {
        Timber.d("Download succeeded for file with downloadId $downloadId")
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_SUCCEEDED)
        appCoroutineScope.launch(dispatchers.io()) {
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
    }

    override fun onSuccess(file: File, mimeType: String?) {
        Timber.d("Download succeeded for file with name ${file.name}")
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_SUCCEEDED)
        fileDownloadNotificationManager.showDownloadFinishedNotification(filename = file.name, uri = file.absolutePath.toUri(), mimeType = mimeType)
        appCoroutineScope.launch(dispatchers.io()) {
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
    }

    override fun onCancel(downloadId: Long) {
        Timber.d("Download cancelled from the notification for file with downloadId $downloadId")
        // Deliberately cancelled by the user. The DownloadManager handles this as a SUCCESS, however it removes the record from
        // its internal DB.
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_CANCELLED)
        appCoroutineScope.launch(dispatchers.io()) {
            downloadsRepository.delete(listOf(downloadId))
        }
    }

    override fun onFailure(downloadId: Long?, url: String?, reason: DownloadFailReason) {
        Timber.d("Failed to download file with downloadId $downloadId or url $url with reason $reason")
        pixel.fire(AppPixelName.DOWNLOAD_REQUEST_FAILED)
        val messageId = when (reason) {
            ConnectionRefused -> R.string.downloadsErrorMessage
            DownloadManagerDisabled -> R.string.downloadsDownloadManagerDisabledErrorMessage
            Other, UnsupportedUrlType, DataUriParseException -> R.string.downloadsDownloadGenericErrorMessage
        }
        val downloadFailedMessage = DownloadCommand.ShowDownloadFailedMessage(
            messageId = messageId,
            showNotification = downloadId == 0L,
            showEnableDownloadManagerAction = reason == DownloadManagerDisabled
        )
        if (downloadFailedMessage.showNotification) {
            fileDownloadNotificationManager.showDownloadFailedNotification()
        }
        appCoroutineScope.launch(dispatchers.io()) {
            command.send(downloadFailedMessage)
        }
    }

    override fun commands(): Flow<DownloadCommand> {
        return command.receiveAsFlow()
    }
}
