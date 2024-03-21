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

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.toStringDropScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.Observable
import javax.inject.Inject
import org.jetbrains.annotations.VisibleForTesting

interface AutoComplete {
    fun autoComplete(query: String): Observable<AutoCompleteResult>

    data class AutoCompleteResult(
        val query: String,
        val suggestions: List<AutoCompleteSuggestion>,
    )

    sealed class AutoCompleteSuggestion(open val phrase: String) {
        data class AutoCompleteSearchSuggestion(
            override val phrase: String,
            val isUrl: Boolean,
        ) : AutoCompleteSuggestion(phrase)

        data class AutoCompleteBookmarkSuggestion(
            override val phrase: String,
            val title: String,
            val url: String,
            val isFavorite: Boolean = false,
        ) : AutoCompleteSuggestion(phrase)

        data class AutoCompleteHistorySuggestion(
            override val phrase: String,
            val title: String,
            val url: String,
        ) : AutoCompleteSuggestion(phrase)

        data class AutoCompleteHistorySearchSuggestion(
            override val phrase: String,
        ) : AutoCompleteSuggestion(phrase)
    }
}

@ContributesBinding(AppScope::class)
class AutoCompleteApi @Inject constructor(
    private val autoCompleteService: AutoCompleteService,
    private val repository: SavedSitesRepository,
) : AutoComplete {

    override fun autoComplete(query: String): Observable<AutoCompleteResult> {
        if (query.isBlank()) {
            return Observable.just(AutoCompleteResult(query = query, suggestions = emptyList()))
        }

        val savedSitesObservable = getAutoCompleteBookmarkResults(query)
            .zipWith(
                getAutoCompleteFavoritesResults(query),
            ) { bookmarks, favorites ->
                (favorites + bookmarks).take(2)
            }

        return savedSitesObservable.zipWith(
            getAutoCompleteSearchResults(query),
        ) { bookmarksResults, searchResults ->
            AutoCompleteResult(
                query = query,
                suggestions = (bookmarksResults + searchResults).distinctBy { it.phrase },
            )
        }
    }

    private fun getAutoCompleteSearchResults(query: String) =
        autoCompleteService.autoComplete(query)
            .flatMapIterable { it }
            .map {
                AutoCompleteSearchSuggestion(phrase = it.phrase, isUrl = (it.isNav ?: UriString.isWebUrl(it.phrase)))
            }
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    private fun getAutoCompleteBookmarkResults(query: String) =
        repository.getBookmarksObservable()
            .map { rankBookmarks(query, it) }
            .flattenAsObservable { it }
            .map {
                AutoCompleteBookmarkSuggestion(phrase = it.url.toUri().toStringDropScheme(), title = it.title, url = it.url, isFavorite = false)
            }
            .distinctUntilChanged()
            .take(2)
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    private fun getAutoCompleteFavoritesResults(query: String) =
        repository.getFavoritesObservable()
            .map { rankFavorites(query, it) }
            .flattenAsObservable { it }
            .map {
                AutoCompleteBookmarkSuggestion(phrase = it.url.toUri().toStringDropScheme(), title = it.title, url = it.url, isFavorite = true)
            }
            .distinctUntilChanged()
            .take(2)
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    private fun rankBookmarks(
        query: String,
        bookmarks: List<Bookmark>,
    ): List<SavedSite> {
        return bookmarks.asSequence()
            .sortByRank(query)
    }

    private fun rankFavorites(
        query: String,
        favorites: List<SavedSite.Favorite>,
    ): List<SavedSite> {
        return favorites.asSequence().sortByRank(query)
    }

    @VisibleForTesting
    fun score(
        title: String?,
        url: Uri,
        visitCount: Int,
        query: String,
        queryTokens: List<String>? = null,
    ): Int {
        // To optimize, query tokens can be precomputed
        val tokens = queryTokens ?: tokensFrom(query)

        var score = 0
        val lowercasedTitle = title?.lowercase() ?: ""
        val queryCount = query.count()
        val nakedUrl = url.naked()
        val domain = url.host?.removePrefix("www.") ?: ""

        // Full matches
        if (nakedUrl.startsWith(query)) {
            score += 300
            // Prioritize root URLs most
            if (url.isRoot()) score += 2000
        } else if (lowercasedTitle.startsWith(query)) {
            score += 200
            if (url.isRoot()) score += 2000
        } else if (queryCount > 2 && domain.contains(query)) {
            score += 150
        } else if (queryCount > 2 && lowercasedTitle.contains(" $query")) { // Exact match from the beginning of the word within string.
            score += 100
        } else {
            // Tokenized matches
            if (tokens.size > 1) {
                var matchesAllTokens = true
                for (token in tokens) {
                    // Match only from the beginning of the word to avoid unintuitive matches.
                    if (!lowercasedTitle.startsWith(token) && !lowercasedTitle.contains(" $token") && !nakedUrl.startsWith(token)) {
                        matchesAllTokens = false
                        break
                    }
                }

                if (matchesAllTokens) {
                    // Score tokenized matches
                    score += 10

                    // Boost score if first token matches:
                    val firstToken = tokens.firstOrNull()
                    if (firstToken != null) { // nakedUrlString - high score boost
                        if (nakedUrl.startsWith(firstToken)) {
                            score += 70
                        } else if (lowercasedTitle.startsWith(firstToken)) { // beginning of the title - moderate score boost
                            score += 50
                        }
                    }
                }
            }
        }

        if (score > 0) {
            // Second sort based on visitCount
            score *= 1000
            score += visitCount
        }

        return score
    }

    private fun Uri.isRoot(): Boolean {
        return (path.isNullOrEmpty() || path == "/") &&
            query == null &&
            fragment == null &&
            userInfo == null
    }

    @VisibleForTesting
    fun tokensFrom(query: String): List<String> {
        return query
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
    }

    private fun Uri.naked(): String {
        if (host == null) {
            return toString().removePrefix("//")
        }

        val builder = buildUpon()

        builder.scheme(null)
        builder.authority(host!!.removePrefix("www."))

        if (path?.lastOrNull() == '/') {
            builder.path(path!!.dropLast(1))
        }

        return builder.build().toString().removePrefix("//")
    }

    private fun Sequence<SavedSite>.sortByRank(query: String): List<SavedSite> {
        return this.map { RankedBookmark(savedSite = it) }
            .map { scoreTitle(it, query) }
            .map { scoreTokens(it, query) }
            .filter { it.score >= 0 }
            .sortedByDescending { it.score }
            .map { it.savedSite }
            .toList()
    }

    private fun scoreTitle(
        rankedBookmark: RankedBookmark,
        query: String,
    ): RankedBookmark {
        if (rankedBookmark.savedSite.title.startsWith(query, ignoreCase = true)) {
            rankedBookmark.score += 200
        } else if (rankedBookmark.savedSite.title.contains(" $query", ignoreCase = true)) {
            rankedBookmark.score += 100
        }

        return rankedBookmark
    }

    private fun scoreTokens(
        rankedBookmark: RankedBookmark,
        query: String,
    ): RankedBookmark {
        val savedSite = rankedBookmark.savedSite
        val domain = savedSite.url.toUri().baseHost
        val tokens = query.split(" ")
        if (tokens.size > 1) {
            tokens.forEach { token ->
                if (!savedSite.title.startsWith(token, ignoreCase = true) &&
                    !savedSite.title.contains(" $token", ignoreCase = true) &&
                    domain?.startsWith(token, ignoreCase = true) == false
                ) {
                    return rankedBookmark
                }
            }

            rankedBookmark.score += 10

            if (domain?.startsWith(tokens.first(), ignoreCase = true) == true) {
                rankedBookmark.score += 300
            } else if (savedSite.title.startsWith(tokens.first(), ignoreCase = true)) {
                rankedBookmark.score += 50
            }
        } else if (savedSite.url.redactSchemeAndWwwSubDomain().startsWith(tokens.first().trimEnd { it == '/' }, ignoreCase = true)) {
            rankedBookmark.score += 300
        }

        return rankedBookmark
    }

    private fun String.redactSchemeAndWwwSubDomain(): String {
        return this.toUri().toStringDropScheme().removePrefix("www.")
    }

    private data class RankedBookmark(
        val savedSite: SavedSite,
        var score: Int = BOOKMARK_SCORE,
    )

    companion object {
        private const val BOOKMARK_SCORE = -1
    }
}
