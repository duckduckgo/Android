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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface ImportGoogleBookmarkResult : Parcelable {
    @Parcelize
    data class Success(
        val importedCount: Int,
    ) : ImportGoogleBookmarkResult

    @Parcelize
    data class UserCancelled(
        val stage: String,
    ) : ImportGoogleBookmarkResult

    @Parcelize
    data class Error(
        val reason: UserCannotImportReason,
    ) : ImportGoogleBookmarkResult

    companion object {
        const val RESULT_KEY = "importBookmarkResult"
        const val RESULT_KEY_DETAILS = "importBookmarkResultDetails"
    }
}

sealed interface UserCannotImportReason : Parcelable {
    @Parcelize
    data object ErrorParsingBookmarks : UserCannotImportReason

    @Parcelize
    data object DownloadError : UserCannotImportReason

    @Parcelize
    data object Unknown : UserCannotImportReason

    @Parcelize
    data object WebViewError : UserCannotImportReason
}
