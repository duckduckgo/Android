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
import com.duckduckgo.app.autocomplete.impl.AutoCompletePixelNames
import com.duckduckgo.app.autocomplete.impl.AutoCompleteRepository
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.AppUrl.Url
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.UrlScheme
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.toStringDropScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.HistoryEntry.VisitedPage
import com.duckduckgo.history.api.HistoryEntry.VisitedSERP
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max

const val maximumNumberOfSuggestions = 12
const val maximumNumberOfTopHits = 2
const val minimumNumberInSuggestionGroup = 5

@ContributesBinding(AppScope::class)
class AutoCompleteApi @Inject constructor(
    private val autoCompleteService: AutoCompleteService,
    private val savedSitesRepository: SavedSitesRepository,
    private val navigationHistory: NavigationHistory,
    private val autoCompleteScorer: AutoCompleteScorer,
    private val autoCompleteRepository: AutoCompleteRepository,
    private val tabRepository: TabRepository,
    private val userStageStore: UserStageStore,
    private val autocompleteTabsFeature: AutocompleteTabsFeature,
    private val duckChat: DuckChat,
    private val history: NavigationHistory,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
) : AutoComplete {

    private var isAutocompleteTabsFeatureEnabled: Boolean? = null

    override fun autoComplete(query: String): Flow<AutoCompleteResult> {
        if (query.isBlank()) {
            return flowOf(AutoCompleteResult(query = query, suggestions = emptyList()))
        }

        return combine(
            getAutoCompleteBookmarkResults(query),
            getAutoCompleteFavoritesResults(query),
            getAutocompleteSwitchToTabResults(query),
            getAutoCompleteHistoryResults(query),
            getAutoCompleteSearchResults(query),
        ) { bookmarks, favorites, tabs, historyResults, searchResults ->
            val bookmarksFavoritesTabsAndHistory = combineBookmarksFavoritesTabsAndHistory(bookmarks, favorites, tabs, historyResults)
            val topHits = getTopHits(bookmarksFavoritesTabsAndHistory, searchResults)
            val filteredBookmarksFavoritesTabsAndHistory = filterBookmarksAndTabsAndHistory(bookmarksFavoritesTabsAndHistory, topHits)
            val middleSectionSearchResults = makeSearchResultsNotAllowedInTopHits(searchResults)
            val distinctSearchResults = getDistinctSearchResults(middleSectionSearchResults, topHits, filteredBookmarksFavoritesTabsAndHistory)

            (topHits + distinctSearchResults + filteredBookmarksFavoritesTabsAndHistory).distinctBy {
                Pair(it.phrase, it::class.java)
            }
        }.map { suggestions ->
            val inAppMessage = mutableListOf<AutoCompleteSuggestion>()
            if (shouldShowHistoryInAutoCompleteIAM(suggestions)) {
                inAppMessage.add(0, AutoCompleteInAppMessageSuggestion)
            }

            val duckAIPrompt = mutableListOf<AutoCompleteSuggestion>()
            if (duckChat.isEnabled()) {
                duckAIPrompt.add(AutoCompleteSuggestion.AutoCompleteDuckAIPrompt(query))
            }

            AutoCompleteResult(
                query = query,
                suggestions = inAppMessage + suggestions.ifEmpty { listOf(AutoCompleteDefaultSuggestion(query)) } + duckAIPrompt,
            )
        }
    }

    private fun combineBookmarksFavoritesTabsAndHistory(
        bookmarks: List<RankedSuggestion<AutoCompleteBookmarkSuggestion>>,
        favorites: List<RankedSuggestion<AutoCompleteBookmarkSuggestion>>,
        tabs: List<RankedSuggestion<AutoCompleteSwitchToTabSuggestion>>,
        historyItems: List<RankedSuggestion<AutoCompleteHistoryRelatedSuggestion>>,
    ): List<AutoCompleteSuggestion> {
        val bookmarksAndFavorites = (favorites + bookmarks.filter { favorites.none { favorite -> (it.suggestion).url == favorite.suggestion.url } })
        val bookmarksFavoritesAndTabs = (tabs + bookmarksAndFavorites) as List<RankedSuggestion<AutoCompleteUrlSuggestion>>
        val searchHistory = historyItems.filter { it.suggestion is AutoCompleteHistorySearchSuggestion }
        val navigationHistory =
            historyItems.filter { it.suggestion is AutoCompleteHistorySuggestion } as List<RankedSuggestion<AutoCompleteHistorySuggestion>>
        return (removeDuplicates(navigationHistory, bookmarksFavoritesAndTabs) + searchHistory)
            .sortedByDescending { it.score }
            .map { it.suggestion }
    }

    private fun getTopHits(
        bookmarksAndFavoritesAndTabsAndHistory: List<AutoCompleteSuggestion>,
        searchResults: List<AutoCompleteSearchSuggestion>,
    ): List<AutoCompleteSuggestion> {
        return (bookmarksAndFavoritesAndTabsAndHistory + searchResults).filter {
            when (it) {
                is AutoCompleteHistorySearchSuggestion -> it.isAllowedInTopHits
                is AutoCompleteHistorySuggestion -> it.isAllowedInTopHits
                is AutoCompleteUrlSuggestion -> true
                is AutoCompleteSearchSuggestion -> it.isAllowedInTopHits
                else -> false
            }
        }.take(maximumNumberOfTopHits)
    }

    private fun filterBookmarksAndTabsAndHistory(
        bookmarksAndFavoritesAndTabsAndHistory: List<AutoCompleteSuggestion>,
        topHits: List<AutoCompleteSuggestion>,
    ): List<AutoCompleteSuggestion> {
        val maxBottomSection = maximumNumberOfSuggestions - (topHits.size + minimumNumberInSuggestionGroup)
        return bookmarksAndFavoritesAndTabsAndHistory
            .filter { suggestion -> topHits.none { it.phrase == suggestion.phrase } }
            .take(maxBottomSection)
    }

    private fun makeSearchResultsNotAllowedInTopHits(searchResults: List<AutoCompleteSearchSuggestion>): List<AutoCompleteSearchSuggestion> {
        // we allow for search results to show navigational links if they are not favorites or bookmarks and not in top hits
        return searchResults.map {
            it.copy(
                isAllowedInTopHits = false,
            )
        }
    }

    private fun getDistinctSearchResults(
        searchResults: List<AutoCompleteSearchSuggestion>,
        topHits: List<AutoCompleteSuggestion>,
        filteredBookmarksAndTabsAndHistory: List<AutoCompleteSuggestion>,
    ): List<AutoCompleteSearchSuggestion> {
        // we allow for navigational search results if they are not part of top hits
        val distinctPhrases = (filteredBookmarksAndTabsAndHistory).distinctBy { it.phrase }.map { it.phrase }.toSet()
        val distinctPairs = (topHits + filteredBookmarksAndTabsAndHistory).distinctBy { Pair(it.phrase, it::class.java) }.size

        val maxSearchResults = maximumNumberOfSuggestions - distinctPairs
        return searchResults.distinctBy { it.phrase }.filterNot { it.phrase in distinctPhrases }.take(maxSearchResults)
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

    override suspend fun fireAutocompletePixel(
        suggestions: List<AutoCompleteSuggestion>,
        suggestion: AutoCompleteSuggestion,
        experimentalInputScreen: Boolean,
    ) {
        val hasBookmarks = withContext(dispatchers.io()) {
            savedSitesRepository.hasBookmarks()
        }
        val hasFavorites = withContext(dispatchers.io()) {
            savedSitesRepository.hasFavorites()
        }
        val hasHistory = withContext(dispatchers.io()) {
            history.hasHistory()
        }
        val hasTabs = withContext(dispatchers.io()) {
            (tabRepository.liveTabs.value?.size ?: 0) > 1
        }

        val hasBookmarkResults = suggestions.any { it is AutoCompleteBookmarkSuggestion && !it.isFavorite }
        val hasFavoriteResults = suggestions.any { it is AutoCompleteBookmarkSuggestion && it.isFavorite }
        val hasHistoryResults = suggestions.any { it is AutoCompleteHistorySuggestion || it is AutoCompleteHistorySearchSuggestion }
        val hasSwitchToTabResults = suggestions.any { it is AutoCompleteSwitchToTabSuggestion }
        val params = mapOf(
            PixelParameter.SHOWED_BOOKMARKS to hasBookmarkResults.toString(),
            PixelParameter.SHOWED_FAVORITES to hasFavoriteResults.toString(),
            PixelParameter.BOOKMARK_CAPABLE to hasBookmarks.toString(),
            PixelParameter.FAVORITE_CAPABLE to hasFavorites.toString(),
            PixelParameter.HISTORY_CAPABLE to hasHistory.toString(),
            PixelParameter.SHOWED_HISTORY to hasHistoryResults.toString(),
            PixelParameter.SWITCH_TO_TAB_CAPABLE to hasTabs.toString(),
            PixelParameter.SHOWED_SWITCH_TO_TAB to hasSwitchToTabResults.toString(),
        )
        val pixelName = when (suggestion) {
            is AutoCompleteBookmarkSuggestion -> {
                if (suggestion.isFavorite) {
                    AutoCompletePixelNames.AUTOCOMPLETE_FAVORITE_SELECTION
                } else {
                    AutoCompletePixelNames.AUTOCOMPLETE_BOOKMARK_SELECTION
                }
            }

            is AutoCompleteSearchSuggestion -> if (suggestion.isUrl) {
                AutoCompletePixelNames.AUTOCOMPLETE_SEARCH_WEBSITE_SELECTION
            } else {
                AutoCompletePixelNames.AUTOCOMPLETE_SEARCH_PHRASE_SELECTION
            }

            is AutoCompleteHistorySuggestion -> AutoCompletePixelNames.AUTOCOMPLETE_HISTORY_SITE_SELECTION
            is AutoCompleteHistorySearchSuggestion -> AutoCompletePixelNames.AUTOCOMPLETE_HISTORY_SEARCH_SELECTION
            is AutoCompleteSwitchToTabSuggestion -> AutoCompletePixelNames.AUTOCOMPLETE_SWITCH_TO_TAB_SELECTION
            is AutoCompleteSuggestion.AutoCompleteDuckAIPrompt -> if (experimentalInputScreen) {
                AutoCompletePixelNames.AUTOCOMPLETE_DUCKAI_PROMPT_EXPERIMENTAL_SELECTION
            } else {
                AutoCompletePixelNames.AUTOCOMPLETE_DUCKAI_PROMPT_LEGACY_SELECTION
            }

            else -> return
        }

        pixel.fire(pixelName, params)
    }

    private fun isAllowedInTopHits(entry: HistoryEntry): Boolean {
        return entry.visits.size > 3 || entry.url.isRoot()
    }

    private fun getAutocompleteSwitchToTabResults(query: String): Flow<List<RankedSuggestion<AutoCompleteSwitchToTabSuggestion>>> =
        runCatching {
            if (autocompleteTabsEnabled) {
                combine(
                    tabRepository.flowTabs,
                    tabRepository.flowSelectedTab,
                ) { tabs, selectedTab ->
                    rankTabs(query, tabs.filter { it.tabId != selectedTab?.tabId })
                }.distinctUntilChanged()
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
                    isAllowedInTopHits = rawResult.isNav ?: UriString.isWebUrl(rawResult.phrase),
                )
                searchSuggestionsList.add(searchSuggestion)
            }
            emit(searchSuggestionsList.toList())
        }.getOrElse { emit(searchSuggestionsList.toList()) }
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

    private fun getAutoCompleteHistoryResults(query: String): Flow<List<RankedSuggestion<AutoCompleteHistoryRelatedSuggestion>>> =
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

    private data class RankedSuggestion<T : AutoCompleteSuggestion>(
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
