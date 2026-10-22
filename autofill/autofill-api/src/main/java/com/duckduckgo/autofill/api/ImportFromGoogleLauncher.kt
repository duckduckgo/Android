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

package com.duckduckgo.autofill.api

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

/**
 * Launches the flow for importing data from Google.
 *
 * Register [bookmarksImportContract] with `registerForActivityResult` in the hosting Activity or Fragment, then start the
 * flow with [launchBookmarksImport]. Every outcome, including the flow being unavailable, is delivered to the registered
 * callback.
 */
interface ImportFromGoogleLauncher {
    /**
     * The contract to register for the bookmark import flow's result.
     */
    fun bookmarksImportContract(): ActivityResultContract<BookmarksImportRequest, ImportFromGoogleResult>

    /**
     * Start the bookmark import flow. If the flow is unsupported, nothing is launched and
     * [ImportFromGoogleResult.Unavailable] is delivered to the registered callback.
     *
     * @param launcher The launcher returned when registering [bookmarksImportContract].
     */
    suspend fun launchBookmarksImport(launcher: ActivityResultLauncher<BookmarksImportRequest>)

    /**
     * Input of [bookmarksImportContract]. Only [launchBookmarksImport] creates it.
     */
    interface BookmarksImportRequest

    sealed interface ImportFromGoogleResult {
        data class Success(val bookmarksImported: Int) : ImportFromGoogleResult
        data object UserCancelled : ImportFromGoogleResult
        data object Error : ImportFromGoogleResult
        data object Unavailable : ImportFromGoogleResult
    }
}
