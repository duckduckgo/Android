/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.autocomplete.api

import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Companion.BOOKMARK_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Companion.SUGGESTION_TYPE
import com.duckduckgo.app.global.UriString
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import javax.inject.Inject

open class AutoCompleteApi @Inject constructor(
    private val autoCompleteService: AutoCompleteService,
    private val bookmarksDao: BookmarksDao
) {

    fun autoComplete(query: String): Observable<AutoCompleteResult> {

        if (query.isBlank()) {
            return Observable.just(AutoCompleteResult(query = query, suggestions = emptyList(), hasBookmarks = false))
        }

        return getAutoCompleteBookmarkResults(query).zipWith(
            getAutoCompleteSearchResults(query),
            BiFunction { bookmarksResults, searchResults ->
                AutoCompleteResult(
                    query = query,
                    suggestions = (bookmarksResults + searchResults).distinct(),
                    hasBookmarks = bookmarksResults.isNotEmpty()
                )
            }
        )
    }

    private fun getAutoCompleteSearchResults(query: String) =
        autoCompleteService.autoComplete(query)
            .flatMapIterable { it }
            .map {
                AutoCompleteSearchSuggestion(phrase = it.phrase, isUrl = UriString.isWebUrl(it.phrase))
            }
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    private fun getAutoCompleteBookmarkResults(query: String) =
        bookmarksDao.bookmarksByQuery("%$query%")
            .flattenAsObservable { it }
            .map {
                AutoCompleteBookmarkSuggestion(phrase = it.url, title = it.title ?: "", url = it.url)
            }
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    data class AutoCompleteResult(
        val query: String,
        val suggestions: List<AutoCompleteSuggestion>,
        val hasBookmarks: Boolean
    )

    sealed class AutoCompleteSuggestion(val phrase: String, val suggestionType: Int) {
        class AutoCompleteSearchSuggestion(phrase: String, val isUrl: Boolean) :
            AutoCompleteSuggestion(phrase, SUGGESTION_TYPE)

        class AutoCompleteBookmarkSuggestion(phrase: String, val title: String, val url: String) :
            AutoCompleteSuggestion(phrase, BOOKMARK_TYPE)
    }
}