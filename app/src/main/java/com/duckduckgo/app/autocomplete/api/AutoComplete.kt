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
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.autocomplete.impl.AutoCompleteRepository
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.AppUrl.Url
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.UrlScheme
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.toStringDropScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.HistoryEntry.VisitedPage
import com.duckduckgo.history.api.HistoryEntry.VisitedSERP
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.Observable
import java.io.InterruptedIOException
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.runBlocking

const val maximumNumberOfSuggestions = 12
const val maximumNumberOfTopHits = 2
const val minimumNumberInSuggestionGroup = 5

interface AutoComplete {
    fun autoComplete(query: String): Observable<AutoCompleteResult>
    fun userDismissedHistoryInAutoCompleteIAM()
    fun submitUserSeenHistoryIAM()

    data class AutoCompleteResult(
        val query: String,
        val suggestions: List<AutoCompleteSuggestion>,
    )

    sealed class AutoCompleteSuggestion(open val phrase: String) {
        data class AutoCompleteSearchSuggestion(
            override val phrase: String,
            val isUrl: Boolean,
        ) : AutoCompleteSuggestion(phrase)

        data class AutoCompleteDefaultSuggestion(
            override val phrase: String,
        ) : AutoCompleteSuggestion(phrase)

        data class AutoCompleteBookmarkSuggestion(
            override val phrase: String,
            val title: String,
            val url: String,
            val isFavorite: Boolean = false,
        ) : AutoCompleteSuggestion(phrase)

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
    }
}

@ContributesBinding(AppScope::class)
class AutoCompleteApi @Inject constructor(
    private val autoCompleteService: AutoCompleteService,
    private val savedSitesRepository: SavedSitesRepository,
    private val navigationHistory: NavigationHistory,
    private val autoCompleteScorer: AutoCompleteScorer,
    private val autoCompleteRepository: AutoCompleteRepository,
    private val userStageStore: UserStageStore,
    private val dispatcherProvider: DispatcherProvider,
) : AutoComplete {

    override fun autoComplete(query: String): Observable<AutoCompleteResult> {
        if (query.isBlank()) {
            return Observable.just(AutoCompleteResult(query = query, suggestions = emptyList()))
        }
        val savedSitesObservable: Observable<List<AutoCompleteSuggestion>> =
            getAutoCompleteBookmarkResults(query)
                .zipWith(
                    getAutoCompleteFavoritesResults(query),
                ) { bookmarks, favorites ->
                    (favorites + bookmarks.filter { favorites.none { favorite -> (it.suggestion).url == favorite.suggestion.url } })
                }.zipWith(
                    getHistoryResults(query),
                ) { bookmarksAndFavorites, historyItems ->
                    val searchHistory = historyItems.filter { it.suggestion is AutoCompleteHistorySearchSuggestion }
                    val navigationHistory = historyItems
                        .filter { it.suggestion is AutoCompleteHistorySuggestion } as List<RankedSuggestion<AutoCompleteHistorySuggestion>>
                    (removeDuplicates(navigationHistory, bookmarksAndFavorites) + searchHistory).sortedByDescending { it.score }.map { it.suggestion }
                }

        return savedSitesObservable.zipWith(getAutoCompleteSearchResults(query)) { bookmarksAndHistory, searchResults ->
            val topHits = (searchResults + bookmarksAndHistory).filter {
                when (it) {
                    is AutoCompleteHistorySearchSuggestion -> it.isAllowedInTopHits
                    is AutoCompleteHistorySuggestion -> it.isAllowedInTopHits
                    is AutoCompleteBookmarkSuggestion -> true
                    else -> false
                }
            }.take(maximumNumberOfTopHits)

            val maxBottomSection = maximumNumberOfSuggestions - (topHits.size + minimumNumberInSuggestionGroup)
            val filteredBookmarks =
                bookmarksAndHistory
                    .filter { suggestion -> topHits.none { it.phrase == suggestion.phrase } }
                    .take(maxBottomSection)
            val maxSearchResults = maximumNumberOfSuggestions - (topHits.size + filteredBookmarks.size)
            val filteredSearchResults = searchResults
                .filter { searchSuggestion -> filteredBookmarks.none { it.phrase == searchSuggestion.phrase } }
                .take(maxSearchResults)

            val inAppMessage = mutableListOf<AutoCompleteSuggestion>()

            val suggestions = (topHits + filteredSearchResults + filteredBookmarks).distinctBy { it.phrase }

            runBlocking(dispatcherProvider.io()) {
                if (shouldShowHistoryInAutoCompleteIAM(suggestions)) {
                    inAppMessage.add(0, AutoCompleteInAppMessageSuggestion)
                }
            }

            AutoCompleteResult(
                query = query,
                suggestions = inAppMessage + suggestions.ifEmpty { listOf(AutoCompleteDefaultSuggestion(query)) },
            )
        }.onErrorResumeNext(Observable.empty())
    }

    private fun removeDuplicates(
        historySuggestions: List<RankedSuggestion<AutoCompleteHistorySuggestion>>,
        bookmarkSuggestions: List<RankedSuggestion<AutoCompleteBookmarkSuggestion>>,
    ): List<RankedSuggestion<*>> {
        val bookmarkMap = bookmarkSuggestions.associateBy { it.suggestion.phrase.lowercase() }

        val uniqueHistorySuggestions = historySuggestions.filter { !bookmarkMap.containsKey(it.suggestion.phrase.lowercase()) }
        val updatedBookmarkSuggestions = bookmarkSuggestions.map { bookmarkSuggestion ->
            val historySuggestion = historySuggestions.find { it.suggestion.phrase.equals(bookmarkSuggestion.suggestion.phrase, ignoreCase = true) }
            if (historySuggestion != null) {
                bookmarkSuggestion.copy(
                    score = max(historySuggestion.score, bookmarkSuggestion.score),
                )
            } else {
                bookmarkSuggestion
            }
        }

        return uniqueHistorySuggestions + updatedBookmarkSuggestions
    }

    override fun userDismissedHistoryInAutoCompleteIAM() {
        autoCompleteRepository.dismissHistoryInAutoCompleteIAM()
    }

    private suspend fun shouldShowHistoryInAutoCompleteIAM(suggestions: List<AutoCompleteSuggestion>): Boolean {
        return isExistingUser() && !autoCompleteRepository.wasHistoryInAutoCompleteIAMDismissed() &&
            autoCompleteRepository.countHistoryInAutoCompleteIAMShown() < 3 &&
            suggestions.any { it is AutoCompleteHistorySuggestion || it is AutoCompleteHistorySearchSuggestion }
    }

    private suspend fun isExistingUser(): Boolean {
        if (userStageStore.getUserAppStage() == AppStage.NEW || userStageStore.getUserAppStage() == AppStage.DAX_ONBOARDING) {
            // do not show anymore
            autoCompleteRepository.dismissHistoryInAutoCompleteIAM()
            return false
        }
        return true
    }

    override fun submitUserSeenHistoryIAM() {
        autoCompleteRepository.submitUserSeenHistoryIAM()
    }

    private fun isAllowedInTopHits(entry: HistoryEntry): Boolean {
        return entry.visits.size > 3 || entry.url.isRoot()
    }

    private fun getAutoCompleteSearchResults(query: String) =
        autoCompleteService.autoComplete(query)
            .flatMapIterable { it }
            .map {
                AutoCompleteSearchSuggestion(phrase = it.phrase, isUrl = (it.isNav ?: UriString.isWebUrl(it.phrase)))
            }
            .toList()
            .onErrorReturn {
                if (it is InterruptedIOException) throw it else emptyList<AutoCompleteSearchSuggestion>()
            }
            .toObservable()

    private fun getAutoCompleteBookmarkResults(query: String): Observable<MutableList<RankedSuggestion<AutoCompleteBookmarkSuggestion>>> =
        savedSitesRepository.getBookmarksObservable()
            .map { rankBookmarks(query, it) }
            .flattenAsObservable { it }
            .distinctUntilChanged()
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    private fun getAutoCompleteFavoritesResults(query: String): Observable<MutableList<RankedSuggestion<AutoCompleteBookmarkSuggestion>>> =
        savedSitesRepository.getFavoritesObservable()
            .map { rankFavorites(query, it) }
            .flattenAsObservable { it }
            .distinctUntilChanged()
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    private fun getHistoryResults(query: String): Observable<List<RankedSuggestion<AutoCompleteHistoryRelatedSuggestion>>> =
        navigationHistory.getHistorySingle()
            .map { rankHistory(query, it) }
            .flattenAsObservable { it }
            .distinctUntilChanged()
            .toList()
            .onErrorReturn { emptyList() }
            .toObservable()

    private fun rankBookmarks(
        query: String,
        bookmarks: List<Bookmark>,
    ): List<RankedSuggestion<AutoCompleteBookmarkSuggestion>> {
        return bookmarks.asSequence()
            .sortByRank(query)
    }

    private fun rankFavorites(
        query: String,
        favorites: List<Favorite>,
    ): List<RankedSuggestion<AutoCompleteBookmarkSuggestion>> {
        return favorites.asSequence().sortByRank(query)
    }

    private fun rankHistory(
        query: String,
        history: List<HistoryEntry>,
    ): List<RankedSuggestion<AutoCompleteHistoryRelatedSuggestion>> {
        return history.asSequence().sortHistoryByRank(query)
    }

    private fun Sequence<SavedSite>.sortByRank(query: String): List<RankedSuggestion<AutoCompleteBookmarkSuggestion>> {
        return this.map { savedSite ->
            RankedSuggestion(
                AutoCompleteBookmarkSuggestion(
                    phrase = savedSite.url.toUri().toStringDropScheme(),
                    title = savedSite.title,
                    url = savedSite.url,
                    isFavorite = savedSite is Favorite,
                ),
            )
        }
            .map { scoreTitle(it, query) }
            .map { scoreTokens(it, query) }
            .map { if (it.suggestion.isFavorite && it.score > 0) it.copy(score = it.score + 5) else it }
            .filter { it.score > 0 }
            .toList()
    }

    private fun Sequence<HistoryEntry>.sortHistoryByRank(query: String): List<RankedSuggestion<AutoCompleteHistoryRelatedSuggestion>> {
        return this.let { entries ->
            entries.filterIsInstance<VisitedSERP>()
                .groupBy { it.query }
                .mapNotNull { (query, suggestions) ->
                    val sanitizedUrl =
                        Uri.Builder()
                            .scheme(UrlScheme.https)
                            .appendQueryParameter(AppUrl.ParamKey.QUERY, query)
                            .authority(Url.HOST)
                            .build()

                    suggestions.firstOrNull()?.let { suggestion ->
                        VisitedSERP(sanitizedUrl, suggestion.title, query, suggestions.flatMap { it.visits })
                    }
                } + entries.filterIsInstance<VisitedPage>()
        }
            .map { entry ->
                when (entry) {
                    is VisitedPage -> {
                        AutoCompleteHistorySuggestion(
                            phrase = entry.url.toStringDropScheme(),
                            title = entry.title,
                            url = entry.url.toString(),
                            isAllowedInTopHits = isAllowedInTopHits(entry),
                        )
                    }
                    is VisitedSERP -> {
                        AutoCompleteHistorySearchSuggestion(
                            phrase = entry.query,
                            isAllowedInTopHits = isAllowedInTopHits(entry),
                        )
                    }
                }.let { suggestion ->
                    RankedSuggestion(suggestion, autoCompleteScorer.score(entry.title, entry.url, entry.visits.size, query))
                }
            }.filter { it.score > 0 }
            .toList()
    }

    private fun scoreTitle(
        rankedBookmark: RankedSuggestion<AutoCompleteBookmarkSuggestion>,
        query: String,
    ): RankedSuggestion<AutoCompleteBookmarkSuggestion> {
        return if (rankedBookmark.suggestion.title.startsWith(query, ignoreCase = true)) {
            rankedBookmark.copy(score = rankedBookmark.score + 200)
        } else if (rankedBookmark.suggestion.title.contains(" $query", ignoreCase = true)) {
            rankedBookmark.copy(score = rankedBookmark.score + 100)
        } else {
            rankedBookmark
        }
    }

    private fun scoreTokens(
        rankedBookmark: RankedSuggestion<AutoCompleteBookmarkSuggestion>,
        query: String,
    ): RankedSuggestion<AutoCompleteBookmarkSuggestion> {
        val suggestion = rankedBookmark.suggestion
        val domain = suggestion.url.toUri().baseHost
        val tokens = query.split(" ")
        var toReturn = rankedBookmark
        if (tokens.size > 1) {
            tokens.forEach { token ->
                if (!suggestion.title.startsWith(token, ignoreCase = true) &&
                    !suggestion.title.contains(" $token", ignoreCase = true) &&
                    (domain == null || !domain.startsWith(token, ignoreCase = true))
                ) {
                    return rankedBookmark
                }
            }

            toReturn = toReturn.copy(score = toReturn.score + 10)

            if (domain?.startsWith(tokens.first(), ignoreCase = true) == true) {
                toReturn = toReturn.copy(score = toReturn.score + 300)
            } else if (suggestion.title.startsWith(tokens.first(), ignoreCase = true)) {
                toReturn = toReturn.copy(score = toReturn.score + 50)
            }
        } else if (suggestion.url.redactSchemeAndWwwSubDomain().startsWith(tokens.first().trimEnd { it == '/' }, ignoreCase = true)) {
            toReturn = toReturn.copy(score = toReturn.score + 300)
        }

        return toReturn
    }

    private fun String.redactSchemeAndWwwSubDomain(): String {
        return this.toUri().toStringDropScheme().removePrefix("www.")
    }

    private data class RankedSuggestion<T : AutoCompleteSuggestion> (
        val suggestion: T,
        val score: Int = DEFAULT_SCORE,
    )
}
