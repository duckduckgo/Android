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

package com.duckduckgo.downloads.impl.location

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.duckduckgo.downloads.api.DownloadDestination
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import logcat.asLog
import logcat.logcat
import okio.Buffer
import okio.sink
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

data class DownloadWriteTarget(
    val storagePath: String,
    val fileName: String,
    private val outputStreamProvider: () -> OutputStream?,
    private val cleanupAction: () -> Unit,
) {
    fun openOutputStream(): OutputStream? = outputStreamProvider()

    fun cleanup() = cleanupAction()
}

class DownloadFileWriter @Inject constructor(
    private val context: Context,
    private val safDownloadStorage: SafDownloadStorage,
) {

    fun prepareTarget(
        pendingFileDownload: PendingFileDownload,
        fileName: String,
    ): DownloadWriteTarget? {
        return when (val destination = pendingFileDownload.destination) {
            is DownloadDestination.Default -> prepareDefaultTarget(pendingFileDownload.directory, fileName)
            is DownloadDestination.CustomTree -> prepareSafTarget(destination, fileName, pendingFileDownload.mimeType)
        }
    }

    fun fileExists(
        pendingFileDownload: PendingFileDownload,
        fileName: String,
    ): Boolean {
        return when (val destination = pendingFileDownload.destination) {
            is DownloadDestination.Default -> File(pendingFileDownload.directory, fileName).exists()
            is DownloadDestination.CustomTree -> safDownloadStorage.fileExists(Uri.parse(destination.treeUri), fileName)
        }
    }

    fun resolveUniqueFileName(
        pendingFileDownload: PendingFileDownload,
        fileName: String,
    ): String {
        return when (val destination = pendingFileDownload.destination) {
            is DownloadDestination.Default -> resolveUniqueDefaultName(pendingFileDownload.directory, fileName)
            is DownloadDestination.CustomTree -> safDownloadStorage.resolveUniqueFileName(Uri.parse(destination.treeUri), fileName)
        }
    }

    fun contentLength(storagePath: String): Long {
        return if (storagePath.startsWith(CONTENT_URI_PREFIX)) {
            DocumentFile.fromSingleUri(context, Uri.parse(storagePath))?.length() ?: 0L
        } else {
            File(storagePath).length()
        }
    }

    private fun prepareDefaultTarget(directory: File, fileName: String): DownloadWriteTarget? {
        if (!directory.exists()) directory.mkdirs()
        var uniqueName = fileName
        var file = File(directory, uniqueName)
        var count = 1
        val dotIndex = fileName.lastIndexOf('.')
        while (true) {
            try {
                if (file.createNewFile()) {
                    break
                }
            } catch (e: Exception) {
                logcat { "Failed to create default download file: ${file.absolutePath} ${e.asLog()}" }
                return null
            }

            uniqueName = if (dotIndex > 0) {
                "${fileName.substring(0, dotIndex)}-$count${fileName.substring(dotIndex)}"
            } else {
                "$fileName-$count"
            }
            file = File(directory, uniqueName)
            count++
            if (count > 100) {
                logcat { "Failed to create default download file after 100 attempts" }
                return null
            }
        }
        return DownloadWriteTarget(
            storagePath = file.absolutePath,
            fileName = file.name,
            outputStreamProvider = { file.outputStream() },
            cleanupAction = { file.delete() },
        )
    }

    private fun prepareSafTarget(
        destination: DownloadDestination.CustomTree,
        fileName: String,
        mimeType: String?,
    ): DownloadWriteTarget? {
        val treeUri = Uri.parse(destination.treeUri)
        val uniqueName = safDownloadStorage.resolveUniqueFileName(treeUri, fileName)
        val documentFile = safDownloadStorage.createFile(treeUri, uniqueName, mimeType) ?: return null
        val fileUri = documentFile.uri
        return DownloadWriteTarget(
            storagePath = fileUri.toString(),
            fileName = uniqueName,
            outputStreamProvider = {
                context.contentResolver.openOutputStream(fileUri, "wt")
            },
            cleanupAction = { safDownloadStorage.deleteFile(fileUri) },
        )
    }

    private fun resolveUniqueDefaultName(directory: File, fileName: String): String {
        var target = File(directory, fileName)
        if (!target.exists()) return fileName

        val dotIndex = fileName.lastIndexOf('.')
        var count = 1
        do {
            target = if (dotIndex > 0) {
                File(directory, "${fileName.substring(0, dotIndex)}-$count${fileName.substring(dotIndex)}")
            } else {
                File(directory, "$fileName-$count")
            }
            count++
        } while (target.exists())

        return target.name
    }

    companion object {
        private const val CONTENT_URI_PREFIX = "content://"
    }
}

fun DownloadWriteTarget.writeStreaming(
    readSizeBytes: Long,
    readBlock: (buffer: Buffer, readSize: Long) -> Long,
    onBytesWritten: (Long) -> Unit,
): Boolean {
    val outputStream = openOutputStream() ?: return false
    val sink = outputStream.sink()
    val buffer = Buffer()
    var totalRead = 0L

    return try {
        while (true) {
            val didRead = readBlock(buffer, readSizeBytes)
            if (didRead <= 0) break
            totalRead += didRead
            sink.write(buffer, didRead)
            onBytesWritten(totalRead)
        }
        true
    } catch (t: Throwable) {
        logcat { "Failed to write download stream: ${t.asLog()}" }
        false
    } finally {
        sink.close()
        outputStream.close()
    }
}

fun DownloadWriteTarget.writeBytes(bytes: ByteArray): Boolean {
    val outputStream = openOutputStream() ?: return false
    return try {
        outputStream.write(bytes)
        true
    } catch (t: Throwable) {
        logcat { "Failed to write download bytes: ${t.asLog()}" }
        false
    } finally {
        outputStream.close()
    }
}
