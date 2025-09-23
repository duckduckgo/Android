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
import com.duckduckgo.autofill.impl.importing.takeout.zip.ZipEntryContentReader.ReadResult
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat

interface TakeoutBookmarkExtractor {

    sealed class ExtractionResult {
        data class Success(val bookmarkHtmlContent: String) : ExtractionResult() {
            override fun toString(): String {
                return "ExtractionResult=success"
            }
        }
        data class Error(val exception: Exception) : ExtractionResult()
    }

    /**
     * Extracts the bookmark HTML content from the provided Google Takeout ZIP file URI.
     * @param fileUri The URI of the Google Takeout ZIP file containing the bookmarks.
     * @return ExtractionResult containing either the bookmark HTML content or an error.
     */
    suspend fun extractBookmarksHtml(fileUri: Uri): ExtractionResult
}

@ContributesBinding(AppScope::class)
class TakeoutZipBookmarkExtractor @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val zipEntryContentReader: ZipEntryContentReader,
) : TakeoutBookmarkExtractor {

    override suspend fun extractBookmarksHtml(fileUri: Uri): ExtractionResult {
        return withContext(dispatchers.io()) {
            runCatching {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipInputStream ->
                        processZipEntries(zipInputStream)
                    }
                } ?: ExtractionResult.Error(Exception("Unable to open file: $fileUri"))
            }.getOrElse { ExtractionResult.Error(Exception(it)) }
        }
    }

    private fun processZipEntries(zipInputStream: ZipInputStream): ExtractionResult {
        var entry = zipInputStream.nextEntry

        if (entry == null) {
            logcat(WARN) { "No entries found in ZIP stream" }
            return ExtractionResult.Error(Exception("Invalid or empty ZIP file"))
        }

        while (entry != null) {
            val entryName = entry.name
            logcat { "Processing zip entry '$entryName'" }

            if (isBookmarkEntry(entry)) {
                return when (val readResult = zipEntryContentReader.readAndValidateContent(zipInputStream, entryName)) {
                    is ReadResult.Success -> ExtractionResult.Success(readResult.content)
                    is ReadResult.Error -> ExtractionResult.Error(readResult.exception)
                }
            }

            entry = zipInputStream.nextEntry
        }

        return ExtractionResult.Error(Exception("Chrome/Bookmarks.html not found in file"))
    }

    private fun isBookmarkEntry(entry: ZipEntry): Boolean {
        return !entry.isDirectory && entry.name.endsWith(EXPECTED_BOOKMARKS_FILENAME, ignoreCase = true)
    }

    companion object {
        private const val EXPECTED_BOOKMARKS_FILENAME = "Chrome/Bookmarks.html"
    }
}
