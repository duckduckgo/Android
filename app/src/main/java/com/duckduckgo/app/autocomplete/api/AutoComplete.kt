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

import androidx.core.net.toUri
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.toStringDropScheme
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import javax.inject.Inject

interface AutoComplete {
    fun autoComplete(query: String): Observable<AutoCompleteResult>

    data class AutoCompleteResult(
        val query: String,
        val suggestions: List<AutoCompleteSuggestion>
    )

    sealed class AutoCompleteSuggestion(open val phrase: String) {
        data class AutoCompleteSearchSuggestion(override val phrase: String, val isUrl: Boolean) :
            AutoCompleteSuggestion(phrase)

        data class AutoCompleteBookmarkSuggestion(override val phrase: String, val title: String, val url: String) :
            AutoCompleteSuggestion(phrase)
    }
}

class AutoCompleteApi @Inject constructor(
    private val autoCompleteService: AutoCompleteService,
    private val bookmarksDao: BookmarksDao
) : AutoComplete {

    override fun autoComplete(query: String): Observable<AutoCompleteResult> {

        if (query.isBlank()) {
            return Observable.just(AutoCompleteResult(query = query, suggestions = emptyList()))
        }

        return getAutoCompleteBookmarkResults(query).zipWith(
            getAutoCompleteSearchResults(query),
            BiFunction { bookmarksResults, searchResults ->
                AutoCompleteResult(
                    query = query,
                    suggestions = (bookmarksResults + searchResults).distinctBy { it.phrase }
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
        bookmarksDao.bookmarksObservable()
            .map { rankBookmarks(query, it) }
            .flattenAsObservable { it }
            .map {
                AutoCompleteBookmarkSuggestion(phrase = it.url.toUri().toStringDropScheme(), title = it.title.orEmpty(), url = it.url)
            }
            .distinctUntilChanged()
            .take(2)
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    private fun rankBookmarks(query: String, bookmarks: List<BookmarkEntity>): List<BookmarkEntity> {
        return bookmarks.asSequence()
            .map { RankedBookmark(bookmarkEntity = it) }
            .map { scoreTitle(it, query) }
            .map { scoreTokens(it, query) }
            .filter { it.score >= 0 }
            .sortedByDescending { it.score }
            .map { it.bookmarkEntity }
            .toList()
    }

    private fun scoreTitle(rankedBookmark: RankedBookmark, query: String): RankedBookmark {
        if (rankedBookmark.bookmarkEntity.title?.startsWith(query, ignoreCase = true) == true) {
            rankedBookmark.score += 200
        } else if (rankedBookmark.bookmarkEntity.title?.contains(" $query", ignoreCase = true) == true) {
            rankedBookmark.score += 100
        }

        return rankedBookmark
    }

    private fun scoreTokens(rankedBookmark: RankedBookmark, query: String): RankedBookmark {
        val bookmark = rankedBookmark.bookmarkEntity
        val domain = bookmark.url.toUri().baseHost
        val tokens = query.split(" ")
        if (tokens.size > 1) {
            tokens.forEach { token ->
                if (bookmark.title?.startsWith(token, ignoreCase = true) == false &&
                    bookmark.title?.contains(" $token", ignoreCase = true) == false &&
                    domain?.startsWith(token, ignoreCase = true) == false
                ) {
                    return rankedBookmark
                }
            }

            rankedBookmark.score += 10

            if (domain?.startsWith(tokens.first(), ignoreCase = true) == true) {
                rankedBookmark.score += 300
            } else if (bookmark.title?.startsWith(tokens.first(), ignoreCase = true) == true) {
                rankedBookmark.score += 50
            }

        } else if (bookmark.url.redactSchemeAndWwwSubDomain().startsWith(tokens.first().trimEnd { it == '/' }, ignoreCase = true)) {
            rankedBookmark.score += 300
        }

        return rankedBookmark
    }

    private fun String.redactSchemeAndWwwSubDomain(): String {
        return this.toUri().toStringDropScheme().removePrefix("www.")
    }

    private data class RankedBookmark(
        val bookmarkEntity: BookmarkEntity,
        var score: Int = BOOKMARK_SCORE
    )

    companion object {
        private const val BOOKMARK_SCORE = -1
    }
}
