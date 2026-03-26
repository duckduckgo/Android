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

package com.duckduckgo.browser.api.autocomplete

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.Flow

interface AutoComplete {
    fun autoComplete(query: String): Flow<AutoCompleteResult>
    suspend fun userDismissedHistoryInAutoCompleteIAM()
    suspend fun submitUserSeenHistoryIAM()
    suspend fun fireAutocompletePixel(
        suggestions: List<AutoCompleteSuggestion>,
        suggestion: AutoCompleteSuggestion,
        experimentalInputScreen: Boolean = false,
    )

    data class Config(
        val showInstalledApps: Boolean = false,
    )

    data class AutoCompleteResult(
        val query: String,
        val suggestions: List<AutoCompleteSuggestion>,
    )

    sealed class AutoCompleteSuggestion(open val phrase: String) {
        data class AutoCompleteSearchSuggestion(
            override val phrase: String,
            val isUrl: Boolean,
            val isAllowedInTopHits: Boolean,
        ) : AutoCompleteSuggestion(phrase)

        data class AutoCompleteDefaultSuggestion(
            override val phrase: String,
        ) : AutoCompleteSuggestion(phrase)

        sealed class AutoCompleteUrlSuggestion(
            phrase: String,
            open val title: String,
            open val url: String,
        ) : AutoCompleteSuggestion(phrase) {

            data class AutoCompleteBookmarkSuggestion(
                override val phrase: String,
                override val title: String,
                override val url: String,
                val isFavorite: Boolean = false,
            ) : AutoCompleteUrlSuggestion(phrase, title, url)

            data class AutoCompleteSwitchToTabSuggestion(
                override val phrase: String,
                override val title: String,
                override val url: String,
                val tabId: String,
            ) : AutoCompleteUrlSuggestion(phrase, title, url)
        }

        sealed class AutoCompleteHistoryRelatedSuggestion(phrase: String) : AutoCompleteSuggestion(phrase) {
            data class AutoCompleteHistorySuggestion(
                override val phrase: String,
                val title: String,
                val url: String,
                val isAllowedInTopHits: Boolean,
            ) : AutoCompleteHistoryRelatedSuggestion(phrase)

            data class AutoCompleteHistorySearchSuggestion(
                override val phrase: String,
                val isAllowedInTopHits: Boolean,
            ) : AutoCompleteHistoryRelatedSuggestion(phrase)

            data object AutoCompleteInAppMessageSuggestion : AutoCompleteHistoryRelatedSuggestion("")
        }

        data class AutoCompleteDuckAIPrompt(
            override val phrase: String,
        ) : AutoCompleteSuggestion(phrase)

        data class AutoCompleteDeviceAppSuggestion(
            override val phrase: String,
            val shortName: String,
            val packageName: String,
            val launchIntent: Intent,
            private var icon: Drawable? = null,
        ) : AutoCompleteSuggestion(phrase) {
            fun retrieveIcon(packageManager: PackageManager): Drawable {
                return icon ?: packageManager.getApplicationIcon(packageName).also {
                    icon = it
                }
            }
        }
    }
}
