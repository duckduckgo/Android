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

package com.duckduckgo.duckchat.impl.history

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.api.model.DownloadItem
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Writes a formatted chat export to the public Downloads directory and registers it in the app's
 * Downloads database so it appears in the in-app Downloads screen.
 *
 * For Text payloads a `.txt` file is written. For Zip payloads a `.zip` is written containing
 * a UTF-8-BOM `chat.txt` and the supplied images as `image-N.jpeg` entries (N matches the
 * placeholder index assigned by [ChatExporter]).
 *
 * Filename pattern: `duck.ai_yyyy-MM-dd_HH-mm-ss.<txt|zip>` per the cross-platform reference.
 * Collisions are resolved by appending `-1`, `-2`, ... before the extension.
 */
interface ChatExportWriter {
    suspend fun write(payload: ExportPayload): File
}

sealed interface ExportPayload {
    val content: String

    data class Text(override val content: String, val mimeType: String = "text/plain") : ExportPayload

    data class Zip(
        override val content: String,
        val images: List<Image>,
    ) : ExportPayload {
        data class Image(val name: String, val bytes: ByteArray)
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealChatExportWriter @Inject constructor(
    private val context: Context,
    private val downloadsRepository: DownloadsRepository,
) : ChatExportWriter {

    private val clock: () -> LocalDateTime = { LocalDateTime.now(ZoneId.systemDefault()) }

    override suspend fun write(payload: ExportPayload): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .also { it.mkdirs() }
        return when (payload) {
            is ExportPayload.Text -> writeText(dir, payload)
            is ExportPayload.Zip -> writeZip(dir, payload)
        }
    }

    private suspend fun writeText(dir: File, payload: ExportPayload.Text): File {
        val file = resolveAvailableFile(dir, baseName(), EXTENSION_TXT)
        file.writeText(payload.content)
        registerInDownloads(file, payload.mimeType)
        return file
    }

    private suspend fun writeZip(dir: File, payload: ExportPayload.Zip): File {
        val file = resolveAvailableFile(dir, baseName(), EXTENSION_ZIP)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("chat.txt"))
            // Reference samples ship chat.txt with a UTF-8 BOM for Notepad-friendliness on Windows.
            zip.write(BOM_UTF8)
            zip.write(payload.content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            payload.images.forEach { image ->
                zip.putNextEntry(ZipEntry(image.name))
                zip.write(image.bytes)
                zip.closeEntry()
            }
        }
        registerInDownloads(file, "application/zip")
        return file
    }

    private suspend fun registerInDownloads(file: File, mimeType: String) {
        downloadsRepository.insert(
            DownloadItem(
                downloadId = 0L,
                downloadStatus = DOWNLOAD_FINISHED,
                fileName = file.name,
                contentLength = file.length(),
                createdAt = DatabaseDateFormatter.timestamp(),
                filePath = file.absolutePath,
            ),
        )
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
    }

    private fun baseName(): String = "duck.ai_${clock().format(FILENAME_FORMATTER)}"

    private fun resolveAvailableFile(dir: File, baseName: String, extension: String): File {
        val initial = File(dir, "$baseName.$extension")
        if (!initial.exists()) return initial
        var count = 1
        while (true) {
            val candidate = File(dir, "$baseName-$count.$extension")
            if (!candidate.exists()) return candidate
            count++
        }
    }

    private companion object {
        const val DOWNLOAD_FINISHED = 1 // mirrors com.duckduckgo.downloads.store.DownloadStatus.FINISHED
        const val EXTENSION_TXT = "txt"
        const val EXTENSION_ZIP = "zip"
        val FILENAME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        val BOM_UTF8: ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}

/** Reads the bytes from an [InputStream] then closes it. Null-safe convenience used by the repository. */
internal fun InputStream?.readBytesAndClose(): ByteArray? = this?.use { it.readBytes() }
