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

package com.duckduckgo.autofill.impl.importing.takeout.zip

import android.content.Context
import android.net.Uri
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutBookmarkExtractor.ExtractionResult
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject

interface TakeoutBookmarkExtractor {
    sealed class ExtractionResult {
        data class Success(
            val tempFileUri: Uri,
        ) : ExtractionResult() {
            override fun toString(): String = "ExtractionResult=success"
        }

        data class Error(
            val exception: Exception,
        ) : ExtractionResult()
    }

    /**
     * Extracts bookmarks from a Google Takeout ZIP file stored on disk.
     * @param takeoutZipUri The URI of the Google Takeout ZIP file containing the bookmarks.
     * @return ExtractionResult containing either a temp file URI with bookmark HTML content or an error.
     */
    suspend fun extractBookmarksFromFile(takeoutZipUri: Uri): ExtractionResult
}

@ContributesBinding(AppScope::class)
class TakeoutZipBookmarkExtractor @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : TakeoutBookmarkExtractor {
    override suspend fun extractBookmarksFromFile(takeoutZipUri: Uri): ExtractionResult =
        withContext(dispatchers.io()) {
            runCatching {
                context.contentResolver.openInputStream(takeoutZipUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipInputStream ->
                        extractFromZipStreamToTempFile(zipInputStream)
                    }
                } ?: ExtractionResult.Error(Exception("Unable to open file: $takeoutZipUri"))
            }.getOrElse { ExtractionResult.Error(Exception(it)) }
        }

    private fun extractFromZipStreamToTempFile(zipInputStream: ZipInputStream): ExtractionResult {
        var entry = zipInputStream.nextEntry

        if (entry == null) {
            logcat(WARN) { "No entries found in ZIP stream" }
            return ExtractionResult.Error(Exception("Invalid or empty ZIP file"))
        }

        while (entry != null) {
            val entryName = entry.name
            logcat { "Processing zip entry '$entryName'" }

            if (isBookmarkEntry(entry)) {
                return streamEntryToTempFile(zipInputStream, entryName)
            }

            entry = zipInputStream.nextEntry
        }

        return ExtractionResult.Error(Exception("Chrome/Bookmarks.html not found in file"))
    }

    private fun isBookmarkEntry(entry: ZipEntry): Boolean = !entry.isDirectory && entry.name.endsWith(EXPECTED_BOOKMARKS_FILENAME, ignoreCase = true)

    private fun streamEntryToTempFile(
        zipInputStream: ZipInputStream,
        entryName: String,
    ): ExtractionResult {
        cleanupOldTempFiles()
        val tempFile = createTempFile()

        return try {
            val totalBytesRead = streamAndValidateContent(zipInputStream, tempFile)
            logcat { "Successfully streamed '$entryName' to temp file: ${tempFile.absolutePath}, size: $totalBytesRead bytes" }
            ExtractionResult.Success(Uri.fromFile(tempFile))
        } catch (e: Exception) {
            runCatching { tempFile.takeIf { it.exists() }?.delete() }
            logcat(WARN) { "Error streaming ZIP entry to temp file: ${e.message}" }
            ExtractionResult.Error(e)
        }
    }

    private fun createTempFile(): File = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, context.cacheDir)

    private fun streamAndValidateContent(
        zipInputStream: ZipInputStream,
        tempFile: File,
    ): Long {
        var totalBytesRead = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        val validator = ContentValidator()

        tempFile.outputStream().buffered().use { fileOutput ->
            var bytesRead: Int
            while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead

                validator.processChunk(buffer, bytesRead)

                fileOutput.write(buffer, 0, bytesRead)
            }
        }

        if (!validator.isValid()) {
            tempFile.delete()
            throw Exception("File content is not a valid bookmark file")
        }

        return totalBytesRead
    }

    private inner class ContentValidator {
        private val validationBuffer = StringBuilder()
        private var validationResult: Boolean? = null

        fun processChunk(
            buffer: ByteArray,
            bytesRead: Int,
        ) {
            if (validationResult == null && validationBuffer.length < VALIDATION_BUFFER_MAX_SIZE) {
                val chunkText = String(buffer, 0, bytesRead, Charsets.UTF_8)
                validationBuffer.append(chunkText)

                if (validationBuffer.length >= VALIDATION_CONTENT_MIN_SIZE) {
                    val content = validationBuffer.toString()
                    validationResult = isValidBookmarkContent(content)
                    validationBuffer.clear() // Free memory after validation
                }
            }
        }

        fun isValid(): Boolean =
            validationResult ?: run {
                val content = validationBuffer.toString()
                isValidBookmarkContent(content)
            }
    }

    private fun isValidBookmarkContent(content: String): Boolean {
        val hasNetscapeHeader = content.contains(NETSCAPE_HEADER, ignoreCase = true)
        val hasBookmarkTitle = content.contains(BOOKMARK_TITLE, ignoreCase = true)

        logcat { "Content validation: hasNetscapeHeader=$hasNetscapeHeader, hasBookmarkTitle=$hasBookmarkTitle" }

        return hasNetscapeHeader || hasBookmarkTitle
    }

    private fun cleanupOldTempFiles() {
        try {
            context.cacheDir
                .listFiles { file ->
                    file.name.startsWith(TEMP_FILE_PREFIX) && file.name.endsWith(TEMP_FILE_SUFFIX)
                }?.forEach { file ->
                    if (file.delete()) {
                        logcat { "Cleaned up old temp file: ${file.name}" }
                    }
                }
        } catch (e: Exception) {
            logcat(WARN) { "Error cleaning up old temp files: ${e.message}" }
        }
    }

    companion object {
        private const val EXPECTED_BOOKMARKS_FILENAME = "Chrome/Bookmarks.html"
        private const val BUFFER_SIZE = 8192
        private const val NETSCAPE_HEADER = "<!DOCTYPE NETSCAPE-Bookmark-file"
        private const val BOOKMARK_TITLE = "<title>Bookmarks</title>"
        private const val VALIDATION_BUFFER_MAX_SIZE = 2048
        private const val VALIDATION_CONTENT_MIN_SIZE = 1024
        private const val TEMP_FILE_PREFIX = "takeout_bookmarks_"
        private const val TEMP_FILE_SUFFIX = ".html"
    }
}
