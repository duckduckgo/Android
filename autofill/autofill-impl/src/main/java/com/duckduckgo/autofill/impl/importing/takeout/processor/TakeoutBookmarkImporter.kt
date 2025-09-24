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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesImporter
import com.duckduckgo.savedsites.api.service.SavedSitesImporter.ImportFolder
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Interface for importing bookmarks with flexible destination handling.
 * Supports both root-level imports and folder-based imports while preserving structure.
 */
interface TakeoutBookmarkImporter {

    /**
     * Imports bookmarks from HTML content to the specified destination.
     * @param htmlContent The HTML bookmark content to import (in Netscape format)
     * @param destination Where to import the bookmarks (Root or named Folder within bookmarks root)
     * @return ImportSavedSitesResult indicating success with imported items or error
     */
    suspend fun importBookmarks(htmlContent: String, destination: ImportFolder): ImportSavedSitesResult
}

@ContributesBinding(AppScope::class)
class RealTakeoutBookmarkImporter @Inject constructor(
    private val savedSitesImporter: SavedSitesImporter,
    private val dispatchers: DispatcherProvider,
) : TakeoutBookmarkImporter {

    override suspend fun importBookmarks(htmlContent: String, destination: ImportFolder): ImportSavedSitesResult {
        return withContext(dispatchers.io()) {
            import(htmlContent = htmlContent, destination = destination)
        }
    }

    private suspend fun import(htmlContent: String, destination: ImportFolder = ImportFolder.Root): ImportSavedSitesResult {
        return try {
            // saved sites importer needs a file uri, so we create a temp file here
            val tempFile = File.createTempFile("bookmark_import_", ".html")
            try {
                tempFile.writeText(htmlContent)
                return savedSitesImporter.import(Uri.fromFile(tempFile), destination)
            } finally {
                // delete the temp file after import
                tempFile.takeIf { it.exists() }?.delete()
            }
        } catch (exception: Exception) {
            ImportSavedSitesResult.Error(exception)
        }
    }
}
