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
 * Class that takes care of importing [SavedSites]
 * This is used to import [SavedSites] from another Browser
 */
interface SavedSitesImporter {
    /**
     * Reads a HTML based file with all [SavedSites] that the user has
     * in Netscape format.
     * @param uri of the [File] we'll read the data from
     * @return [ImportSavedSitesResult] result of the operation
     */
    suspend fun import(uri: Uri): ImportSavedSitesResult
}

sealed class ImportSavedSitesResult {
    data class Success(val savedSites: List<Any>) : ImportSavedSitesResult()
    data class Error(val exception: Exception) : ImportSavedSitesResult()
}
