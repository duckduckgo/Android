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
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.duckduckgo.app.autocomplete.AutocompleteTabsFeature
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.app.autocomplete.impl.AutoCompleteRepository
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
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
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

const val maximumNumberOfSuggestions = 12
const val maximumNumberOfTopHits = 2
const val minimumNumberInSuggestionGroup = 5

interface AutoComplete {
    fun autoComplete(query: String): Flow<AutoCompleteResult>
    suspend fun userDismissedHistoryInAutoCompleteIAM()
    suspend fun submitUserSeenHistoryIAM()

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
    }
}

@ContributesBinding(AppScope::class)
class AutoCompleteApi @Inject constructor(
    private val autoCompleteService: AutoCompleteService,
    private val savedSitesRepository: SavedSitesRepository,
    private val navigationHistory: NavigationHistory,
    private val autoCompleteScorer: AutoCompleteScorer,
    private val autoCompleteRepository: AutoCompleteRepository,
    private val tabRepository: TabRepository,
    private val userStageStore: UserStageStore,
    private val dispatcherProvider: DispatcherProvider,
    private val autocompleteTabsFeature: AutocompleteTabsFeature,
) : AutoComplete {

    private var isAutocompleteTabsFeatureEnabled: Boolean? = null

    override fun autoComplete(query: String): Flow<AutoCompleteResult> = flow {
        if (query.isBlank()) {
            return@flow emit(AutoCompleteResult(query = query, suggestions = emptyList()))
        }
        val savedSites = getAutoCompleteBookmarkResults(query)
            .combine(getAutoCompleteFavoritesResults(query)) { bookmarks, favorites ->
                (favorites + bookmarks.filter { favorites.none { favorite -> (it.suggestion).url == favorite.suggestion.url } })
            }.combine(getAutocompleteSwitchToTabResults(query)) { bookmarksAndFavorites, tabs ->
                (tabs + bookmarksAndFavorites) as List<RankedSuggestion<AutoCompleteUrlSuggestion>>
            }.combine(getHistoryResults(query)) { bookmarksAndFavoritesAndTabs, historyItems ->
                val searchHistory = historyItems.filter { it.suggestion is AutoCompleteHistorySearchSuggestion }
                val navigationHistory = historyItems
                    .filter { it.suggestion is AutoCompleteHistorySuggestion } as List<RankedSuggestion<AutoCompleteHistorySuggestion>>
                (removeDuplicates(navigationHistory, bookmarksAndFavoritesAndTabs) + searchHistory)
                    .sortedByDescending { it.score }
                    .map { it.suggestion }
            }.combine(getAutoCompleteSearchResults(query)) { bookmarksAndFavoritesAndTabsAndHistory, searchResults ->
                Pair(bookmarksAndFavoritesAndTabsAndHistory, searchResults)
            }

        savedSites.collect { (bookmarksAndFavoritesAndTabsAndHistory, searchResults) ->
            val topHits = (searchResults + bookmarksAndFavoritesAndTabsAndHistory).filter {
                when (it) {
                    is AutoCompleteHistorySearchSuggestion -> it.isAllowedInTopHits
                    is AutoCompleteHistorySuggestion -> it.isAllowedInTopHits
                    is AutoCompleteUrlSuggestion -> true
                    else -> false
                }
            }.take(maximumNumberOfTopHits)

            val maxBottomSection = maximumNumberOfSuggestions - (topHits.size + minimumNumberInSuggestionGroup)
            val filteredBookmarksAndTabsAndHistory =
                bookmarksAndFavoritesAndTabsAndHistory
                    .filter { suggestion -> topHits.none { it.phrase == suggestion.phrase } }
                    .take(maxBottomSection)
            val maxSearchResults = maximumNumberOfSuggestions - (topHits.size + filteredBookmarksAndTabsAndHistory.size)
            val filteredSearchResults = searchResults
                .filter { searchSuggestion -> filteredBookmarksAndTabsAndHistory.none { it.phrase == searchSuggestion.phrase } }
                .take(maxSearchResults)

            val inAppMessage = mutableListOf<AutoCompleteSuggestion>()

            val distinctPhrases = (topHits + filteredBookmarksAndTabsAndHistory).distinctBy { it.phrase }.map { it.phrase }.toSet()
            val distinctSearchResults = filteredSearchResults.distinctBy { it.phrase }.filterNot { it.phrase in distinctPhrases }
            val suggestions = (topHits + distinctSearchResults + filteredBookmarksAndTabsAndHistory).distinctBy {
                Pair(it.phrase, it::class.java)
            }

            if (shouldShowHistoryInAutoCompleteIAM(suggestions)) {
                inAppMessage.add(0, AutoCompleteInAppMessageSuggestion)
            }

            return@collect emit(
                AutoCompleteResult(
                    query = query,
                    suggestions = inAppMessage + suggestions.ifEmpty { listOf(AutoCompleteDefaultSuggestion(query)) },
                ),
            )
        }
    }

    private fun removeDuplicates(
        historySuggestions: List<RankedSuggestion<AutoCompleteHistorySuggestion>>,
        bookmarkSuggestions: List<RankedSuggestion<AutoCompleteUrlSuggestion>>,
    ): List<RankedSuggestion<*>> {
        val bookmarkMap = bookmarkSuggestions.associateBy { it.suggestion.phrase.lowercase() }

        val uniqueHistorySuggestions = historySuggestions.filter { !bookmarkMap.containsKey(it.suggestion.phrase.lowercase()) }
        val updatedBookmarkSuggestions = bookmarkSuggestions.map { bookmarkSuggestion ->
            val historySuggestion =
                historySuggestions.find { it.suggestion.phrase.equals(bookmarkSuggestion.suggestion.phrase, ignoreCase = true) }
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

    override suspend fun userDismissedHistoryInAutoCompleteIAM() {
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

    override suspend fun submitUserSeenHistoryIAM() {
        autoCompleteRepository.submitUserSeenHistoryIAM()
    }

    private fun isAllowedInTopHits(entry: HistoryEntry): Boolean {
        return entry.visits.size > 3 || entry.url.isRoot()
    }

    private fun getAutocompleteSwitchToTabResults(query: String): Flow<List<RankedSuggestion<AutoCompleteSwitchToTabSuggestion>>> =
        runCatching {
            if (autocompleteTabsEnabled) {
                tabRepository.flowTabs
                    .map { rankTabs(query, it) }
                    .distinctUntilChanged()
            } else {
                flowOf(emptyList())
            }
        }.getOrElse { flowOf(emptyList()) }

    private val autocompleteTabsEnabled: Boolean by lazy {
        isAutocompleteTabsFeatureEnabled ?: run {
            val enabled = autocompleteTabsFeature.self().isEnabled()
            isAutocompleteTabsFeatureEnabled = enabled
            enabled
        }
    }

    private fun getAutoCompleteSearchResults(query: String) = flow {
        val searchSuggestionsList = mutableListOf<AutoCompleteSearchSuggestion>()
        runCatching {
            val rawResults = autoCompleteService.autoComplete(query)
            for (rawResult in rawResults) {
                val searchSuggestion = AutoCompleteSearchSuggestion(
                    phrase = rawResult.phrase.formatIfUrl(),
                    isUrl = rawResult.isNav ?: UriString.isWebUrl(rawResult.phrase),
                )
                searchSuggestionsList.add(searchSuggestion)
            }
            emit(searchSuggestionsList)
        }.getOrElse { emit(searchSuggestionsList) }
    }

    private fun getAutoCompleteBookmarkResults(query: String): Flow<List<RankedSuggestion<AutoCompleteBookmarkSuggestion>>> =
        runCatching {
            savedSitesRepository.getBookmarks()
                .map { rankBookmarks(query, it) }
                .distinctUntilChanged()
        }.getOrElse { flowOf(emptyList()) }

    private fun getAutoCompleteFavoritesResults(query: String): Flow<List<RankedSuggestion<AutoCompleteBookmarkSuggestion>>> =
        runCatching {
            savedSitesRepository.getFavorites()
                .map { rankFavorites(query, it) }
                .distinctUntilChanged()
        }.getOrElse { flowOf(emptyList()) }

    private fun getHistoryResults(query: String): Flow<List<RankedSuggestion<AutoCompleteHistoryRelatedSuggestion>>> =
        runCatching {
            navigationHistory.getHistory()
                .map { rankHistory(query, it) }
                .distinctUntilChanged()
        }.getOrElse { flowOf(emptyList()) }

    private fun rankTabs(
        query: String,
        tabs: List<TabEntity>,
    ): List<RankedSuggestion<AutoCompleteSwitchToTabSuggestion>> {
        return tabs.asSequence()
            .filter { it.url != null }
            .distinctBy { it.url }
            .sortTabsByRank(query)
    }

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

    private fun Sequence<TabEntity>.sortTabsByRank(query: String): List<RankedSuggestion<AutoCompleteSwitchToTabSuggestion>> {
        return this.map { tabEntity ->
            RankedSuggestion(
                AutoCompleteSwitchToTabSuggestion(
                    phrase = tabEntity.url?.formatIfUrl().orEmpty(),
                    title = tabEntity.title.orEmpty(),
                    url = tabEntity.url.orEmpty(),
                    tabId = tabEntity.tabId,
                ),
            )
        }
            .map { scoreTitle(it, query) }
            .map { scoreTokens(it, query) }
            .filter { it.score > 0 }
            .toList()
    }

    private fun Sequence<SavedSite>.sortByRank(query: String): List<RankedSuggestion<AutoCompleteBookmarkSuggestion>> {
        return this.map { savedSite ->
            RankedSuggestion(
                AutoCompleteBookmarkSuggestion(
                    phrase = savedSite.url.formatIfUrl(),
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
                            phrase = entry.url.toString().formatIfUrl(),
                            title = entry.title,
                            url = entry.url.toString(),
                            isAllowedInTopHits = isAllowedInTopHits(entry),
                        )
                    }
                    is VisitedSERP -> {
                        AutoCompleteHistorySearchSuggestion(
                            phrase = entry.query.formatIfUrl(),
                            isAllowedInTopHits = isAllowedInTopHits(entry),
                        )
                    }
                }.let { suggestion ->
                    RankedSuggestion(suggestion, autoCompleteScorer.score(entry.title, entry.url, entry.visits.size, query))
                }
            }.filter { it.score > 0 }
            .toList()
    }

    private fun <T : AutoCompleteUrlSuggestion> scoreTitle(
        rankedSuggestion: RankedSuggestion<T>,
        query: String,
    ): RankedSuggestion<T> {
        return if (rankedSuggestion.suggestion.title.startsWith(query, ignoreCase = true)) {
            rankedSuggestion.copy(score = rankedSuggestion.score + 200)
        } else if (rankedSuggestion.suggestion.title.contains(" $query", ignoreCase = true)) {
            rankedSuggestion.copy(score = rankedSuggestion.score + 100)
        } else {
            rankedSuggestion
        }
    }

    private fun <T : AutoCompleteUrlSuggestion> scoreTokens(
        rankedBookmark: RankedSuggestion<T>,
        query: String,
    ): RankedSuggestion<T> {
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

@VisibleForTesting
internal fun String.formatIfUrl(): String {
    val trimmedUrl = this.trimEnd('/')

    val prefixToRemove = listOf("http://www.", "https://www.", "www.", "http://", "https://")
    val formattedUrl = prefixToRemove.find { trimmedUrl.startsWith(it, ignoreCase = true) }?.let {
        trimmedUrl.substring(it.length)
    } ?: trimmedUrl

    return formattedUrl
}
