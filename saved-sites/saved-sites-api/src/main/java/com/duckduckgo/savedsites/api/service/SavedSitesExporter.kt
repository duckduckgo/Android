/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.api.service

import android.net.Uri

/**
 * Class that takes care of exporting [SavedSites]
 * This is used to export [SavedSites] to another Browser
 */
interface SavedSitesExporter {
    /**
     * Generates a HTML based file with all [SavedSites] that the user has
     * in Netscape format.
     * @param uri of the [File] where we'll store the data
     * @return [ExportSavedSitesResult] result of the operation
     */
    suspend fun export(uri: Uri): ExportSavedSitesResult
}

sealed class ExportSavedSitesResult {
    data object Success : ExportSavedSitesResult()
    data class Error(val exception: Exception) : ExportSavedSitesResult()
    data object NoSavedSitesExported : ExportSavedSitesResult()
}
