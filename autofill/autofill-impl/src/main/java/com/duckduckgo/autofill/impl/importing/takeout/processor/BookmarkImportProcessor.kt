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

package com.duckduckgo.autofill.impl.importing.takeout.processor

import android.net.Uri
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.*
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult.Error
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult.Error.ParseError
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutBookmarkExtractor
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutBookmarkExtractor.ExtractionResult
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutZipDownloader
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesImporter.ImportFolder
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.io.File
import javax.inject.Inject

interface BookmarkImportProcessor {
    sealed class ImportResult {
        data class Success(
            val importedCount: Int,
        ) : ImportResult()

        sealed class Error : ImportResult() {
            data object DownloadError : Error()

            data object ParseError : Error()

            data object ImportError : Error()
        }
    }

    suspend fun downloadAndImportFromTakeoutZipUrl(
        url: String,
        userAgent: String,
        folderName: String,
    ): ImportResult
}

@ContributesBinding(AppScope::class)
class TakeoutBookmarkImportProcessor @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val takeoutZipDownloader: TakeoutZipDownloader,
    private val bookmarkExtractor: TakeoutBookmarkExtractor,
    private val takeoutBookmarkImporter: TakeoutBookmarkImporter,
) : BookmarkImportProcessor {
    override suspend fun downloadAndImportFromTakeoutZipUrl(
        url: String,
        userAgent: String,
        folderName: String,
    ): ImportResult =
        withContext(dispatchers.io()) {
            runCatching {
                val zipUri = takeoutZipDownloader.downloadZip(url, userAgent)
                processBookmarkZip(zipUri, folderName)
            }.getOrElse { e ->
                logcat(WARN) { "Bookmark zip download failed: ${e.asLog()}" }
                Error.DownloadError
            }
        }

    private suspend fun processBookmarkZip(
        zipUri: Uri,
        folderName: String,
    ): ImportResult =
        runCatching {
            val extractionResult = bookmarkExtractor.extractBookmarksFromFile(zipUri)
            handleExtractionResult(extractionResult, folderName)
        }.getOrElse { e ->
            logcat(WARN) { "Error processing bookmark zip: ${e.asLog()}" }
            ParseError
        }.also {
            cleanupZipFile(zipUri)
        }

    private suspend fun handleExtractionResult(
        extractionResult: ExtractionResult,
        folderName: String,
    ): ImportResult =
        when (extractionResult) {
            is ExtractionResult.Success -> {
                val importResult =
                    takeoutBookmarkImporter.importBookmarks(
                        extractionResult.tempFileUri,
                        ImportFolder.Folder(folderName),
                    )
                handleImportResult(importResult)
            }

            is ExtractionResult.Error -> {
                logcat(WARN) { "Error extracting bookmarks from zip" }
                ParseError
            }
        }

    private fun handleImportResult(importResult: ImportSavedSitesResult): ImportResult =
        when (importResult) {
            is ImportSavedSitesResult.Success -> {
                val importedCount = importResult.savedSites.size
                logcat { "Successfully imported $importedCount bookmarks" }
                ImportResult.Success(importedCount)
            }

            is ImportSavedSitesResult.Error -> {
                logcat(WARN) { "Error importing bookmarks: ${importResult.exception.message}" }
                Error.ImportError
            }
        }

    private fun cleanupZipFile(zipUri: Uri) {
        runCatching {
            val zipFile = File(zipUri.path ?: return)
            if (zipFile.exists() && zipFile.delete()) {
                logcat { "Cleaned up downloaded zip file: ${zipFile.absolutePath}" }
            }
        }.onFailure { logcat(WARN) { "Failed to cleanup downloaded zip file: ${it.asLog()}" } }
    }
}
