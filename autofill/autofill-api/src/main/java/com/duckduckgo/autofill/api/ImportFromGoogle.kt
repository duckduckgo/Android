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

package com.duckduckgo.autofill.api

import android.content.Intent

/**
 * API for importing data from Google
 */
interface ImportFromGoogle {
    /**
     * Get the intent which will launch the bookmark import flow. It's possible the flow is unsupported, in which case null will be returned.
     */
    suspend fun getBookmarksImportLaunchIntent(): Intent?

    /**
     * Parse the result of the bookmark import flow
     * @param intent The intent returned in onActivityResult from the import flow
     * @return The result of the import operation
     */
    suspend fun parseResult(intent: Intent?): ImportFromGoogleResult

    sealed interface ImportFromGoogleResult {
        data class Success(val bookmarksImported: Int) : ImportFromGoogleResult
        data object UserCancelled : ImportFromGoogleResult
        data object Error : ImportFromGoogleResult
    }
}
