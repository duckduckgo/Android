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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.*
import com.duckduckgo.downloads.api.DownloadCommand.ShowDownloadStartedMessage
import com.duckduckgo.downloads.api.DownloadCommand.ShowDownloadSuccessMessage
import com.duckduckgo.downloads.api.DownloadFailReason.*
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.impl.R.string
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName.DOWNLOAD_REQUEST_FAILED
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName.DOWNLOAD_REQUEST_STARTED
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName.DOWNLOAD_REQUEST_SUCCEEDED
import com.duckduckgo.downloads.store.DownloadStatus.FINISHED
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat
import java.io.File
import javax.inject.Inject

interface DownloadCallback {
    /**
     * Called when a download started. Takes a [downloadItem] as parameter with all data related to the file that is downloaded.
     */
    fun onStart(downloadItem: DownloadItem)

    /**
     * Called during the download progress. Takes as parameters the [downloadId] and the [progress] of the download.
     */
    fun onProgress(downloadId: Long, filename: String, progress: Int)

    /**
     * Called when a download done using the DownloadManager finishes with success. Takes as parameters the [downloadId] and [contentLength]
     * provided by the DownloadManager.
     */
    fun onSuccess(downloadId: Long, contentLength: Long, file: File, mimeType: String?)

    /**
     * Called when a download done without using the DownloadManager finishes with success. Takes as parameters the [file]
     * downloaded and the [mimeType] associated with the download.
     */
    fun onSuccess(file: File, mimeType: String?)

    /**
     * Called when the download fails. Takes as optional parameter the [url] which started the download. Takes optional parameters download [url] and
     * [downloadId] and a mandatory parameter [reason] describing why the download has failed.
     */
    fun onError(url: String? = null, downloadId: Long? = null, reason: DownloadFailReason)

    /**
     * Called when the download is cancelled from the app or from the notification. Takes as mandatory parameter the [downloadId] provided by
     * the DownloadManager.
     */
    fun onCancel(downloadId: Long)
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = DownloadCallback::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = DownloadStateListener::class,
)
@SingleInstanceIn(AppScope::class)
class FileDownloadCallback @Inject constructor(
    private val fileDownloadNotificationManager: FileDownloadNotificationManager,
    private val downloadsRepository: DownloadsRepository,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val mediaScanner: MediaScanner,
) : DownloadCallback, DownloadStateListener {

    private val command = Channel<DownloadCommand>(1, BufferOverflow.DROP_OLDEST)

    override fun onStart(downloadItem: DownloadItem) {
        logcat { "Download started for file ${downloadItem.fileName}" }
        pixel.fire(DOWNLOAD_REQUEST_STARTED)
        val downloadStartedMessage = ShowDownloadStartedMessage(
            messageId = string.downloadsDownloadStartedMessage,
            fileName = downloadItem.fileName,
        )
        fileDownloadNotificationManager.showDownloadInProgressNotification(downloadItem.downloadId, downloadItem.fileName)
        appCoroutineScope.launch(dispatchers.io()) {
            command.send(downloadStartedMessage)
            downloadsRepository.insert(downloadItem)
        }
    }

    override fun onProgress(downloadId: Long, filename: String, progress: Int) {
        fileDownloadNotificationManager.showDownloadInProgressNotification(downloadId, filename, progress)
    }

    override fun onSuccess(downloadId: Long, contentLength: Long, file: File, mimeType: String?) {
        logcat { "Download succeeded for file ${file.name} / $mimeType / $contentLength" }
        pixel.fire(DOWNLOAD_REQUEST_SUCCEEDED)
        fileDownloadNotificationManager.showDownloadFinishedNotification(
            downloadId = downloadId,
            file = file,
            mimeType = mimeType,
        )
        appCoroutineScope.launch(dispatchers.io()) {
            downloadsRepository.update(downloadId = downloadId, downloadStatus = FINISHED, contentLength = contentLength)
            mediaScanner.scan(file)
            downloadsRepository.getDownloadItem(downloadId)?.let {
                command.send(
                    ShowDownloadSuccessMessage(
                        messageId = string.downloadsDownloadFinishedMessage,
                        fileName = it.fileName,
                        filePath = it.filePath,
                    ),
                )
            }
        }
    }

    override fun onSuccess(file: File, mimeType: String?) {
        logcat { "Download succeeded for file with name ${file.name} / $mimeType" }
        pixel.fire(DOWNLOAD_REQUEST_SUCCEEDED)
        fileDownloadNotificationManager.showDownloadFinishedNotification(
            downloadId = 0,
            file = file,
            mimeType = mimeType,
        )
        appCoroutineScope.launch(dispatchers.io()) {
            downloadsRepository.update(fileName = file.name, downloadStatus = FINISHED, contentLength = file.length())
            mediaScanner.scan(file)
            command.send(
                ShowDownloadSuccessMessage(
                    messageId = string.downloadsDownloadFinishedMessage,
                    fileName = file.name,
                    filePath = file.absolutePath,
                    mimeType = mimeType,
                ),
            )
        }
    }

    override fun onError(url: String?, downloadId: Long?, reason: DownloadFailReason) {
        logcat { "Failed to download file with url $url (id = $downloadId) and reason $reason." }
        pixel.fire(DOWNLOAD_REQUEST_FAILED)
        handleFailedDownload(downloadId = downloadId ?: 0, url = url, reason = reason)
        downloadId?.let {
            appCoroutineScope.launch(dispatchers.io()) {
                downloadsRepository.delete(listOf(downloadId))
            }
        }
    }

    override fun onCancel(downloadId: Long) {
        // This is a cancelled download either from the app or from the notification.
        // If the database doesn't contain a record for that downloadId it means the download was cancelled by the user from the
        // application. A cancel pixel is sent as it was the user's decision to cancel the started download.
        // If there is a record in the database it will be removed as the download was cancelled from the notification and a cancel pixel sent.
        appCoroutineScope.launch(dispatchers.io()) {
            val item = downloadsRepository.getDownloadItem(downloadId)
            if (item == null) {
                logcat { "Cancelled download file with downloadId $downloadId from the app." }
            } else {
                logcat { "Cancelled to download file with downloadId $downloadId from the notification." }
                downloadsRepository.delete(listOf(downloadId))
            }
            pixel.fire(DownloadsPixelName.DOWNLOAD_REQUEST_CANCELLED)
        }
        fileDownloadNotificationManager.cancelDownloadFileNotification(downloadId)
    }

    override fun commands(): Flow<DownloadCommand> {
        return command.receiveAsFlow()
    }

    private fun handleFailedDownload(downloadId: Long, url: String?, reason: DownloadFailReason) {
        val messageId = when (reason) {
            ConnectionRefused -> R.string.downloadsErrorMessage
            Other, UnsupportedUrlType, DataUriParseException -> R.string.downloadsDownloadGenericErrorMessage
        }
        val downloadFailedMessage = DownloadCommand.ShowDownloadFailedMessage(messageId = messageId)
        fileDownloadNotificationManager.showDownloadFailedNotification(downloadId, url)
        appCoroutineScope.launch(dispatchers.io()) {
            command.send(downloadFailedMessage)
        }
    }
}
