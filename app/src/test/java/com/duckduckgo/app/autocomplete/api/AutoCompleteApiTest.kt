/*
 * Copyright (c) 2022 DuckDuckGo
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.autocomplete.AutocompleteTabsFeature
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.app.autocomplete.impl.AutoCompleteRepository
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.AppStage.NEW
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.HistoryEntry.VisitedPage
import com.duckduckgo.history.api.HistoryEntry.VisitedSERP
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AutoCompleteApiTest {

    @Mock
    private lateinit var mockAutoCompleteService: AutoCompleteService

    @Mock
    private lateinit var mockSavedSitesRepository: SavedSitesRepository

    @Mock
    private lateinit var mockNavigationHistory: NavigationHistory

    @Mock
    private lateinit var mockAutoCompleteRepository: AutoCompleteRepository

    @Mock
    private lateinit var mockTabRepository: TabRepository

    @Mock
    private lateinit var mockUserStageStore: UserStageStore

    @Mock
    private lateinit var mockAutocompleteTabsFeature: AutocompleteTabsFeature

    @Mock
    private lateinit var mockToggle: Toggle

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: AutoCompleteApi

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        whenever(mockTabRepository.flowTabs).thenReturn(flowOf(listOf(TabEntity("1", position = 1))))
        whenever(mockNavigationHistory.getHistory()).thenReturn(flowOf(emptyList()))
        runTest {
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(NEW)
        }
        whenever(mockAutocompleteTabsFeature.self()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        testee = AutoCompleteApi(
            mockAutoCompleteService,
            mockSavedSitesRepository,
            mockNavigationHistory,
            RealAutoCompleteScorer(),
            mockAutoCompleteRepository,
            mockTabRepository,
            mockUserStageStore,
            mockAutocompleteTabsFeature,
        )
    }

    @Test
    fun whenQueryIsBlankThenReturnAnEmptyList() = runTest {
        val result = testee.autoComplete("")
        val value = result.first()

        assertTrue(value.suggestions.isEmpty())
    }

    @Test
    fun whenReturnBookmarkSuggestionsThenPhraseIsURLBaseHost() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(bookmarks()))
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals("example.com", value.suggestions[0].phrase)
    }

    @Test
    fun whenAutoCompleteDoesNotMatchAnySavedSiteReturnDefault() = runTest {
        whenever(mockAutoCompleteService.autoComplete("wrong")).thenReturn(emptyList())

        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(
                        title = "title",
                        url = "https://example.com",
                    ),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf(favorite(title = "title"))))

        val result = testee.autoComplete("wrong")
        val value = result.first()

        assertEquals(value.suggestions.first(), AutoCompleteDefaultSuggestion("wrong"))
    }

    @Test
    fun whenAutoCompleteReturnsMultipleBookmarkAndFavoriteHitsThenBothShowBeforeSearchSuggestionsAndFavoritesShowFirst() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "title", url = "https://example.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title", url = "https://bar.com"),
                    bookmark(title = "title", url = "https://baz.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isFavorite = true),
                AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title", "https://bar.com", isFavorite = false),
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title", "https://baz.com", isFavorite = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsMultipleTabAndBookmarkAndFavoriteHitsThenBothShowBeforeSearchSuggestionsAndFavoritesShowFirst() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "title", url = "https://example.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title", url = "https://bar.com"),
                    bookmark(title = "title", url = "https://baz.com"),
                ),
            ),
        )

        whenever(mockTabRepository.flowTabs).thenReturn(
            flowOf(
                listOf(
                    TabEntity(tabId = "1", position = 1, title = "title", url = "https://bar.com"),
                    TabEntity(tabId = "2", position = 2, title = "title", url = "https://baz.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isFavorite = true),
                AutoCompleteSwitchToTabSuggestion(phrase = "bar.com", "title", "https://bar.com", tabId = "1"),
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSwitchToTabSuggestion(phrase = "baz.com", title = "title", url = "https://baz.com", tabId = "2"),
                AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title", "https://baz.com", isFavorite = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsDuplicatedTabsAndBookmarkAndFavoriteHitsThenTabSuggestionsAreNotDuplicatedAndFirstTabPositionIsChosen() =
        runTest {
            whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
                listOf(
                    AutoCompleteServiceRawResult("foo", isNav = false),
                ),
            )
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
                flowOf(
                    listOf(
                        favorite(title = "title", url = "https://example.com"),
                    ),
                ),
            )
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
                flowOf(
                    listOf(
                        bookmark(title = "title", url = "https://bar.com"),
                        bookmark(title = "title", url = "https://baz.com"),
                    ),
                ),
            )

            whenever(mockTabRepository.flowTabs).thenReturn(
                flowOf(
                    listOf(
                        TabEntity(tabId = "1", position = 1, title = "title", url = "https://bar.com"),
                        TabEntity(tabId = "2", position = 2, title = "title", url = "https://bar.com"),
                        TabEntity(tabId = "3", position = 3, title = "title", url = "https://bar.com"),
                        TabEntity(tabId = "4", position = 4, title = "title", url = "https://baz.com"),
                        TabEntity(tabId = "5", position = 5, title = "title", url = "https://baz.com"),
                        TabEntity(tabId = "6", position = 6, title = "title", url = "https://baz.com"),
                    ),
                ),
            )

            val result = testee.autoComplete("title")
            val value = result.first()

            assertEquals(
                listOf(
                    AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isFavorite = true),
                    AutoCompleteSwitchToTabSuggestion(phrase = "bar.com", "title", "https://bar.com", tabId = "1"),
                    AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                    AutoCompleteSwitchToTabSuggestion(phrase = "baz.com", title = "title", url = "https://baz.com", tabId = "4"),
                    AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title", "https://baz.com", isFavorite = false),
                ),
                value.suggestions,
            )
        }

    @Test
    fun whenAutoCompleteReturnsMultipleVariousResultsThenOnlyMax12AreShown() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("aaa", isNav = false),
                AutoCompleteServiceRawResult("bbb", isNav = false),
                AutoCompleteServiceRawResult("ccc", isNav = false),
                AutoCompleteServiceRawResult("ddd", isNav = false),
                AutoCompleteServiceRawResult("eee", isNav = false),
                AutoCompleteServiceRawResult("fff", isNav = false),
                AutoCompleteServiceRawResult("ggg", isNav = false),
                AutoCompleteServiceRawResult("hhh", isNav = false),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "title", url = "https://iii.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title", url = "https://iii.com"),
                    bookmark(title = "title", url = "https://jjj.com"),
                    bookmark(title = "title", url = "https://kkk.com"),
                ),
            ),
        )

        whenever(mockTabRepository.flowTabs).thenReturn(
            flowOf(
                listOf(
                    TabEntity(tabId = "1", position = 1, title = "title", url = "https://lll.com"),
                    TabEntity(tabId = "2", position = 2, title = "title", url = "https://mmm.com"),
                    TabEntity(tabId = "3", position = 3, title = "title", url = "https://nnn.com"),
                    TabEntity(tabId = "4", position = 4, title = "title", url = "https://ooo.com"),
                    TabEntity(tabId = "5", position = 5, title = "title", url = "https://ppp.com"),
                    TabEntity(tabId = "6", position = 6, title = "title", url = "https://qqq.com"),
                    TabEntity(tabId = "6", position = 6, title = "title", url = "https://iii.com"),
                    TabEntity(tabId = "6", position = 6, title = "title", url = "https://jjj.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(12, value.suggestions.size)
        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "iii.com", title = "title", url = "https://iii.com", isFavorite = true),
                AutoCompleteSwitchToTabSuggestion(phrase = "lll.com", title = "title", url = "https://lll.com", tabId = "1"),
                AutoCompleteSearchSuggestion(phrase = "aaa", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "bbb", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "ccc", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "ddd", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "eee", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSwitchToTabSuggestion(phrase = "mmm.com", title = "title", url = "https://mmm.com", tabId = "2"),
                AutoCompleteSwitchToTabSuggestion(phrase = "nnn.com", title = "title", url = "https://nnn.com", tabId = "3"),
                AutoCompleteSwitchToTabSuggestion(phrase = "ooo.com", title = "title", url = "https://ooo.com", tabId = "4"),
                AutoCompleteSwitchToTabSuggestion(phrase = "ppp.com", title = "title", url = "https://ppp.com", tabId = "5"),
                AutoCompleteSwitchToTabSuggestion(phrase = "qqq.com", title = "title", url = "https://qqq.com", tabId = "6"),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsMultipleBookmarkAndFavoriteHitsWithBookmarksAlsoInHistoryThenBookmarksShowBeforeSearchSuggestions() =
        runTest {
            whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
                listOf(
                    AutoCompleteServiceRawResult("foo", isNav = false),
                ),
            )
            whenever(mockNavigationHistory.getHistory()).thenReturn(
                flowOf(
                    listOf(
                        VisitedPage(
                            title = "title",
                            url = "https://bar.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()),
                        ),
                    ),
                ),
            )
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
                flowOf(
                    listOf(
                        favorite(title = "title", url = "https://example.com"),
                    ),
                ),
            )
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
                flowOf(
                    listOf(
                        bookmark(title = "title", url = "https://bar.com"),
                        bookmark(title = "title", url = "https://baz.com"),
                    ),
                ),
            )

            val result = testee.autoComplete("title")
            val value = result.first()

            assertEquals(
                listOf(
                    AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title", "https://bar.com", isFavorite = false),
                    AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isFavorite = true),
                    AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                    AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title", "https://baz.com", isFavorite = false),
                ),
                value.suggestions,
            )
        }

    @Test
    fun whenAutoCompleteReturnsHistoryItemsWithLessThan3VisitsButRootPageTheyShowBeforeSuggestions() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockNavigationHistory.getHistory()).thenReturn(
            flowOf(
                listOf(
                    VisitedPage(
                        title = "title",
                        url = "https://bar.com".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                    VisitedPage(
                        title = "title",
                        url = "https://foo.com".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "title", url = "https://example.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title", url = "https://baz.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteHistorySuggestion(phrase = "bar.com", "title", "https://bar.com", isAllowedInTopHits = true),
                AutoCompleteHistorySuggestion(phrase = "foo.com", "title", "https://foo.com", isAllowedInTopHits = true),
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isFavorite = true),
                AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title", "https://baz.com", isFavorite = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsDuplicateHistorySerpWithMoreThan3CombinedVisitsTheyShowBeforeSuggestions() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockNavigationHistory.getHistory()).thenReturn(
            flowOf(
                listOf(
                    VisitedSERP(
                        "https://duckduckgo.com?q=query".toUri(),
                        "title",
                        "query",
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                    VisitedSERP(
                        "https://duckduckgo.com?q=query&atb=1".toUri(),
                        "title",
                        "query",
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf()))
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(listOf()))

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteHistorySearchSuggestion(phrase = "query", true),
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsDuplicateHistorySerpWithLessThan3CombinedVisitsTheyDoNotShowBeforeSuggestions() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockNavigationHistory.getHistory()).thenReturn(
            flowOf(
                listOf(
                    VisitedSERP("https://duckduckgo.com?q=query".toUri(), "title", "query", visits = listOf(LocalDateTime.now())),
                    VisitedSERP("https://duckduckgo.com?q=query&atb=1".toUri(), "title", "query", visits = listOf(LocalDateTime.now())),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf()))
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(listOf()))

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteHistorySearchSuggestion(phrase = "query", false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsHistoryItemsWithLessThan3VisitsAndNotRootPageTheyDoNotShowBeforeSuggestions() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockNavigationHistory.getHistory()).thenReturn(
            flowOf(
                listOf(
                    VisitedPage(
                        title = "title",
                        url = "https://bar.com/test".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                    VisitedPage(
                        title = "title",
                        url = "https://foo.com/test".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "title", url = "https://example.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title", url = "https://baz.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isFavorite = true),
                AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title", "https://baz.com", isFavorite = false),
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteHistorySuggestion(phrase = "bar.com/test", "title", "https://bar.com/test", isAllowedInTopHits = false),
                AutoCompleteHistorySuggestion(phrase = "foo.com/test", "title", "https://foo.com/test", isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsMultipleFavoriteHitsLimitTopHitsTo2() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "title", url = "https://example.com"),
                    favorite(title = "title", url = "https://foo.com"),
                    favorite(title = "title", url = "https://bar.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isFavorite = true),
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title", "https://foo.com", isFavorite = true),
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title", "https://bar.com", isFavorite = true),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsMultipleSavedSitesHitsThenShowFavoritesFirst() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title", url = "https://example.com"),
                    bookmark(title = "title", url = "https://foo.com"),
                    bookmark(title = "title", url = "https://bar.com"),
                    bookmark(title = "title", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "title", url = "https://favexample.com"),
                    favorite(title = "title", url = "https://favfoo.com"),
                    favorite(title = "title", url = "https://favbar.com"),
                    favorite(title = "title", url = "https://favbaz.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertTrue((value.suggestions[0] as AutoCompleteBookmarkSuggestion).isFavorite)
        assertTrue((value.suggestions[1] as AutoCompleteBookmarkSuggestion).isFavorite)
        assertTrue((value.suggestions[2] as AutoCompleteBookmarkSuggestion).isFavorite)
        assertTrue((value.suggestions[3] as AutoCompleteBookmarkSuggestion).isFavorite)
        assertFalse((value.suggestions[4] as AutoCompleteBookmarkSuggestion).isFavorite)
    }

    @Test
    fun whenNavResultsReturnedThenItShowsAsMiddleSectionIfUrlIsBookmarkAndTab() = runTest {
        whenever(mockAutoCompleteService.autoComplete("example")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("example", false),
                AutoCompleteServiceRawResult("example.com", true),
                AutoCompleteServiceRawResult("foo", false),
                AutoCompleteServiceRawResult("bar", false),
                AutoCompleteServiceRawResult("baz", false),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        whenever(mockTabRepository.flowTabs).thenReturn(
            flowOf(
                listOf(
                    TabEntity(tabId = "1", position = 1, title = "example", url = "https://example.com"),
                ),
            ),
        )

        val result = testee.autoComplete("example")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteSwitchToTabSuggestion(phrase = "example.com", "example", "https://example.com", tabId = "1"),
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title example", "https://example.com", isFavorite = false),
                AutoCompleteSearchSuggestion(phrase = "example", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "example.com", isUrl = true, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "bar", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "baz", isUrl = false, isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenNavResultsReturnedThenItShowsAsMiddleSectionIfUrlIsFavouriteAndTab() = runTest {
        whenever(mockAutoCompleteService.autoComplete("example")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("example", false),
                AutoCompleteServiceRawResult("example.com", true),
                AutoCompleteServiceRawResult("foo", false),
                AutoCompleteServiceRawResult("bar", false),
                AutoCompleteServiceRawResult("baz", false),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "title example", url = "https://example.com"),
                ),
            ),
        )

        whenever(mockTabRepository.flowTabs).thenReturn(
            flowOf(
                listOf(
                    TabEntity(tabId = "1", position = 1, title = "example", url = "https://example.com"),
                ),
            ),
        )

        val result = testee.autoComplete("example")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteSwitchToTabSuggestion(phrase = "example.com", "example", "https://example.com", tabId = "1"),
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title example", "https://example.com", isFavorite = true),
                AutoCompleteSearchSuggestion(phrase = "example", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "example.com", isUrl = true, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "bar", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "baz", isUrl = false, isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenReturnOneBookmarkAndOneFavoriteSuggestionsThenShowBothFavoriteFirst() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(
                        title = "title",
                        url = "https://example.com",
                    ),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(
                        title = "title",
                        url = "https://favexample.com",
                    ),
                ),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(
                    phrase = "favexample.com",
                    title = "title",
                    url = "https://favexample.com",
                    isFavorite = true,
                ),
                AutoCompleteBookmarkSuggestion(
                    phrase = "example.com",
                    title = "title",
                    url = "https://example.com",
                    isFavorite = false,
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsDuplicatedItemsThenDedupConsideringQueryParams() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("example.com", true),
                AutoCompleteServiceRawResult("foo.com", true),
                AutoCompleteServiceRawResult("bar.com", true),
                AutoCompleteServiceRawResult("baz.com", true),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title foo", url = "https://foo.com?key=value"),
                    bookmark(title = "title foo", url = "https://foo.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "foo.com?key=value", "title foo", "https://foo.com?key=value"),
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title foo", "https://foo.com"),
                AutoCompleteSearchSuggestion(phrase = "example.com", isUrl = true, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "foo.com", isUrl = true, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "baz.com", isUrl = true, isAllowedInTopHits = false),
                AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title bar", "https://bar.com"),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenBookmarkTitleStartsWithQueryThenScoresHigher() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "the title example", url = "https://example.com"),
                    bookmark(title = "the title foo", url = "https://foo.com/path/to/foo"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "the title foo", url = "https://foo.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title bar", "https://bar.com"), value.suggestions[0])
        assertEquals(
            AutoCompleteBookmarkSuggestion(
                phrase = "example.com",
                "the title example",
                "https://example.com",
            ),
            value.suggestions[1],
        )
    }

    @Test
    fun whenSingleTokenQueryAndBookmarkDomainStartsWithItThenScoreHigher() = runTest {
        whenever(mockAutoCompleteService.autoComplete("foo")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "title foo", url = "https://foo.com"),
                    bookmark(title = "title baz", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("foo")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title foo", "https://foo.com"),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenSingleTokenQueryAndBookmarkReturnsDuplicatedItemsThenDedup() = runTest {
        whenever(mockAutoCompleteService.autoComplete("cnn")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "CNN international", url = "https://cnn.com"),
                    bookmark(title = "CNN international", url = "https://cnn.com"),
                    bookmark(title = "CNN international - world", url = "https://cnn.com/world"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("cnn")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "cnn.com", "CNN international", "https://cnn.com"),
                AutoCompleteBookmarkSuggestion(
                    phrase = "cnn.com/world",
                    "CNN international - world",
                    "https://cnn.com/world",
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenSingleTokenQueryEndsWithSlashThenIgnoreItWhileMatching() = runTest {
        whenever(mockAutoCompleteService.autoComplete("reddit.com/")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "Reddit", url = "https://reddit.com"),
                    bookmark(title = "Reddit - duckduckgo", url = "https://reddit.com/r/duckduckgo"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("reddit.com/")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "reddit.com", "Reddit", "https://reddit.com"),
                AutoCompleteBookmarkSuggestion(
                    phrase = "reddit.com/r/duckduckgo",
                    "Reddit - duckduckgo",
                    "https://reddit.com/r/duckduckgo",
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenSingleTokenQueryEndsWithMultipleSlashThenIgnoreThemWhileMatching() = runTest {
        whenever(mockAutoCompleteService.autoComplete("reddit.com///")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "Reddit", url = "https://reddit.com"),
                    bookmark(title = "Reddit - duckduckgo", url = "https://reddit.com/r/duckduckgo"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("reddit.com///")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "reddit.com", "Reddit", "https://reddit.com"),
                AutoCompleteBookmarkSuggestion(
                    phrase = "reddit.com/r/duckduckgo",
                    "Reddit - duckduckgo",
                    "https://reddit.com/r/duckduckgo",
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenSingleTokenQueryContainsMultipleSlashThenIgnoreThemWhileMatching() = runTest {
        whenever(mockAutoCompleteService.autoComplete("reddit.com/r//")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "Reddit", url = "https://reddit.com"),
                    bookmark(title = "Reddit - duckduckgo", url = "https://reddit.com/r/duckduckgo"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("reddit.com/r//")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(
                    phrase = "reddit.com/r/duckduckgo",
                    "Reddit - duckduckgo",
                    "https://reddit.com/r/duckduckgo",
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenSingleTokenQueryDomainContainsWwwThenResultMathUrl() = runTest {
        whenever(mockAutoCompleteService.autoComplete("reddit")).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "Reddit", url = "https://www.reddit.com"),
                    bookmark(title = "duckduckgo", url = "https://www.reddit.com/r/duckduckgo"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete("reddit")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "reddit.com", "Reddit", "https://www.reddit.com"),
                AutoCompleteBookmarkSuggestion(
                    phrase = "reddit.com/r/duckduckgo",
                    "duckduckgo",
                    "https://www.reddit.com/r/duckduckgo",
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenMultipleTokenQueryAndNoTokenMatchThenReturnDefault() = runTest {
        val query = "example title foo"
        whenever(mockAutoCompleteService.autoComplete(query)).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "the title foo", url = "https://foo.com"),
                    bookmark(title = "title baz", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete(query)
        val value = result.first()

        assertEquals(listOf<AutoCompleteSuggestion>(AutoCompleteDefaultSuggestion("example title foo")), value.suggestions)
    }

    @Test
    fun whenMultipleTokenQueryAndMultipleMatchesThenReturnCorrectScore() = runTest {
        val query = "title foo"
        whenever(mockAutoCompleteService.autoComplete(query)).thenReturn(emptyList())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "the title foo", url = "https://foo.com"),
                    bookmark(title = "title foo baz", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete(query)
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title foo baz", "https://baz.com"),
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "the title foo", "https://foo.com"),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteQueryIsCapitalizedButResultsAreNotThenIgnoreCapitalization() = runTest {
        whenever(mockAutoCompleteService.autoComplete("Title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(),
            ),
        )
        whenever(mockNavigationHistory.getHistory()).thenReturn(
            flowOf(
                listOf(
                    VisitedPage(
                        title = "Title",
                        url = "https://example.com".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                    VisitedPage(
                        title = "Title",
                        url = "https://foo.com".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                    VisitedPage(
                        title = "Title",
                        url = "https://bar.com".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                ),
            ),
        )

        val result = testee.autoComplete("Title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteHistorySuggestion(phrase = "example.com", "Title", "https://example.com", isAllowedInTopHits = true),
                AutoCompleteHistorySuggestion(phrase = "foo.com", "Title", "https://foo.com", isAllowedInTopHits = true),
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteHistorySuggestion(phrase = "bar.com", "Title", "https://bar.com", isAllowedInTopHits = true),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteQueryIsNotCapitalizedButResultsAreThenIgnoreCapitalization() = runTest {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("foo", isNav = false),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(),
            ),
        )
        whenever(mockNavigationHistory.getHistory()).thenReturn(
            flowOf(
                listOf(
                    VisitedPage(
                        title = "Title",
                        url = "https://example.com".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                    VisitedPage(
                        title = "Title",
                        url = "https://foo.com".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                    VisitedPage(
                        title = "Title",
                        url = "https://bar.com".toUri(),
                        visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                    ),
                ),
            ),
        )

        val result = testee.autoComplete("title")
        val value = result.first()

        assertEquals(
            listOf(
                AutoCompleteHistorySuggestion(phrase = "example.com", "Title", "https://example.com", isAllowedInTopHits = true),
                AutoCompleteHistorySuggestion(phrase = "foo.com", "Title", "https://foo.com", isAllowedInTopHits = true),
                AutoCompleteSearchSuggestion("foo", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteHistorySuggestion(phrase = "bar.com", "Title", "https://bar.com", isAllowedInTopHits = true),
            ),
            value.suggestions,
        )
    }

    @Test
    fun testWhenAutoCompleteAndHistoryResultsAvailableAndSeenCountLessThan3AndIAMNotDismissedAndExistingUserThenInAppMessageIsPrepended() {
        runTest {
            whenever(mockAutoCompleteRepository.countHistoryInAutoCompleteIAMShown()).thenReturn(0)
            whenever(mockAutoCompleteRepository.wasHistoryInAutoCompleteIAMDismissed()).thenReturn(false)
            whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
            whenever(mockNavigationHistory.getHistory()).thenReturn(
                flowOf(
                    listOf(
                        VisitedPage(
                            title = "title",
                            url = "https://bar.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                        ),
                        VisitedPage(
                            title = "title",
                            url = "https://foo.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                        ),
                    ),
                ),
            )
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(emptyList()))
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)

            val result = testee.autoComplete("title")
            val value = result.first()

            assertEquals(
                listOf(
                    AutoCompleteInAppMessageSuggestion,
                    AutoCompleteHistorySuggestion(phrase = "bar.com", "title", "https://bar.com", isAllowedInTopHits = true),
                    AutoCompleteHistorySuggestion(phrase = "foo.com", "title", "https://foo.com", isAllowedInTopHits = true),
                ),
                value.suggestions,
            )
        }
    }

    @Test
    fun testWhenAutoCompleteAndHistoryResultsAvailableAndSeenCountLessThan3AndIAMNotDismissedAndExistingUserThenInAppMessageIsNotPrepended() {
        runTest {
            whenever(mockAutoCompleteRepository.countHistoryInAutoCompleteIAMShown()).thenReturn(0)
            whenever(mockAutoCompleteRepository.wasHistoryInAutoCompleteIAMDismissed()).thenReturn(false)
            whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
            whenever(mockNavigationHistory.getHistory()).thenReturn(
                flowOf(
                    listOf(
                        VisitedPage(
                            title = "title",
                            url = "https://bar.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                        ),
                        VisitedPage(
                            title = "title",
                            url = "https://foo.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                        ),
                    ),
                ),
            )
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(emptyList()))
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)

            val result = testee.autoComplete("title")
            val value = result.first()

            assertEquals(
                listOf(
                    AutoCompleteHistorySuggestion(phrase = "bar.com", "title", "https://bar.com", isAllowedInTopHits = true),
                    AutoCompleteHistorySuggestion(phrase = "foo.com", "title", "https://foo.com", isAllowedInTopHits = true),
                ),
                value.suggestions,
            )
        }
    }

    @Test
    fun testWhenAutoCompleteAndHistoryResultsAvailableAndSeenCount3AndIAMNotDismissedThenInAppMessageIsNotPrepended() {
        runTest {
            whenever(mockAutoCompleteRepository.countHistoryInAutoCompleteIAMShown()).thenReturn(3)
            whenever(mockAutoCompleteRepository.wasHistoryInAutoCompleteIAMDismissed()).thenReturn(false)
            whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
            whenever(mockNavigationHistory.getHistory()).thenReturn(
                flowOf(
                    listOf(
                        VisitedPage(
                            title = "title",
                            url = "https://bar.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                        ),
                        VisitedPage(
                            title = "title",
                            url = "https://foo.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                        ),
                    ),
                ),
            )
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(emptyList()))

            val result = testee.autoComplete("title")
            val value = result.first()

            assertEquals(
                listOf(
                    AutoCompleteHistorySuggestion(phrase = "bar.com", "title", "https://bar.com", isAllowedInTopHits = true),
                    AutoCompleteHistorySuggestion(phrase = "foo.com", "title", "https://foo.com", isAllowedInTopHits = true),
                ),
                value.suggestions,
            )
        }
    }

    @Test
    fun testWhenAutoCompleteAndHistoryResultsAvailableAndSeenCountLessThan3AndIAMDismissedThenInAppMessageIsNotPrepended() {
        runTest {
            whenever(mockAutoCompleteRepository.countHistoryInAutoCompleteIAMShown()).thenReturn(0)
            whenever(mockAutoCompleteRepository.wasHistoryInAutoCompleteIAMDismissed()).thenReturn(true)
            whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
            whenever(mockNavigationHistory.getHistory()).thenReturn(
                flowOf(
                    listOf(
                        VisitedPage(
                            title = "title",
                            url = "https://bar.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                        ),
                        VisitedPage(
                            title = "title",
                            url = "https://foo.com".toUri(),
                            visits = listOf(LocalDateTime.now(), LocalDateTime.now()),
                        ),
                    ),
                ),
            )
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(emptyList()))

            val result = testee.autoComplete("title")
            val value = result.first()

            assertEquals(
                listOf(
                    AutoCompleteHistorySuggestion(phrase = "bar.com", "title", "https://bar.com", isAllowedInTopHits = true),
                    AutoCompleteHistorySuggestion(phrase = "foo.com", "title", "https://foo.com", isAllowedInTopHits = true),
                ),
                value.suggestions,
            )
        }
    }

    @Test
    fun testUserSeenHistoryThenCallRepositoryUserSeenHistory() {
        runTest {
            testee.submitUserSeenHistoryIAM()

            verify(mockAutoCompleteRepository).submitUserSeenHistoryIAM()
        }
    }

    @Test
    fun whenOtherExceptionThenReturnDefaultSuggestion() = runTest {
        val query = "example title foo"
        whenever(mockAutoCompleteService.autoComplete(query)).thenThrow(RuntimeException())

        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(emptyList()))
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete(query)
        val value = result.first()

        assertEquals(listOf<AutoCompleteSuggestion>(AutoCompleteDefaultSuggestion(query)), value.suggestions)
    }

    @Test
    fun whenFormatIfUrlCalledOnStringThenTheStringHasExpectedPrefixAndSuffixRemoved() {
        assertEquals("example.com", "example.com".formatIfUrl())
        assertEquals("example.com", "example.com/".formatIfUrl())
        assertEquals("example.com", "www.example.com".formatIfUrl())
        assertEquals("example.com", "www.example.com/".formatIfUrl())
        assertEquals("example.com", "https://example.com".formatIfUrl())
        assertEquals("example.com", "https://example.com/".formatIfUrl())
        assertEquals("example.com", "https://www.example.com/".formatIfUrl())
        assertEquals("example.com", "https://www.example.com".formatIfUrl())
        assertEquals("example.com", "http://example.com".formatIfUrl())
        assertEquals("example.com", "http://example.com/".formatIfUrl())
        assertEquals("example.com", "http://www.example.com/".formatIfUrl())
        assertEquals("example.com", "http://www.example.com".formatIfUrl())
        assertEquals("example.com/path?query1=1&query2=1", "example.com/path?query1=1&query2=1".formatIfUrl())
        assertEquals("example.com/path?query1=1&query2=1", "www.example.com/path?query1=1&query2=1".formatIfUrl())
        assertEquals("example.com/path?query1=1&query2=1", "http://example.com/path?query1=1&query2=1".formatIfUrl())
        assertEquals("example.com/path?query1=1&query2=1", "http://www.example.com/path?query1=1&query2=1".formatIfUrl())
        assertEquals("example.com/path?query1=1&query2=1", "https://example.com/path?query1=1&query2=1".formatIfUrl())
        assertEquals("example.com/path?query1=1&query2=1", "https://www.example.com/path?query1=1&query2=1".formatIfUrl())
    }

    @Test
    fun whenAutoCompleteReturnsNavigationalLinkThatIsNotTabOrFavouriteOrBookmarkThenResultsAreShown() = runTest {
        val searchTerm = "espn"
        whenever(mockAutoCompleteService.autoComplete(searchTerm)).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("espn", isNav = false),
                AutoCompleteServiceRawResult("espn.com", isNav = true),
                AutoCompleteServiceRawResult("espn fantasy football", isNav = false),
                AutoCompleteServiceRawResult("espn sports", isNav = false),
                AutoCompleteServiceRawResult("espn nba", isNav = false),
            ),
        )

        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(emptyList()))
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
        whenever(mockTabRepository.flowTabs).thenReturn(flowOf(emptyList()))

        val result = testee.autoComplete(searchTerm)
        val value = result.first()

        assertEquals(5, value.suggestions.size)
        assertEquals(
            listOf(
                AutoCompleteSearchSuggestion(phrase = "espn.com", isUrl = true, isAllowedInTopHits = true),
                AutoCompleteSearchSuggestion(phrase = "espn", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn fantasy football", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn sports", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn nba", isUrl = false, isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsNavigationalLinkThatIsTabAndNotFavouriteOrBookmarkThenResultsAreShown() = runTest {
        val searchTerm = "espn"
        whenever(mockAutoCompleteService.autoComplete(searchTerm)).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("espn", isNav = false),
                AutoCompleteServiceRawResult("espn.com", isNav = true),
                AutoCompleteServiceRawResult("espn fantasy football", isNav = false),
                AutoCompleteServiceRawResult("espn sports", isNav = false),
                AutoCompleteServiceRawResult("espn nba", isNav = false),
            ),
        )

        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(emptyList()))
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        whenever(mockTabRepository.flowTabs).thenReturn(
            flowOf(
                listOf(
                    TabEntity(tabId = "1", position = 1, title = "espn", url = "https://espn.com"),
                    TabEntity(tabId = "2", position = 2, title = "title", url = "https://baz.com"),
                ),
            ),
        )

        val result = testee.autoComplete(searchTerm)
        val value = result.first()

        assertEquals(6, value.suggestions.size)
        assertEquals(
            listOf(
                AutoCompleteSwitchToTabSuggestion(phrase = "espn.com", "espn", "https://espn.com", tabId = "1"),
                AutoCompleteSearchSuggestion(phrase = "espn.com", isUrl = true, isAllowedInTopHits = true),
                AutoCompleteSearchSuggestion(phrase = "espn", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn fantasy football", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn sports", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn nba", isUrl = false, isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsNavigationalLinkThatIsTabAndBookmarkThenResultsAreShown() = runTest {
        val searchTerm = "espn"
        whenever(mockAutoCompleteService.autoComplete(searchTerm)).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("espn", isNav = false),
                AutoCompleteServiceRawResult("espn.com", isNav = true),
                AutoCompleteServiceRawResult("espn fantasy football", isNav = false),
                AutoCompleteServiceRawResult("espn sports", isNav = false),
                AutoCompleteServiceRawResult("espn nba", isNav = false),
            ),
        )

        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "espn", url = "https://espn.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "the title foo", url = "https://foo.com"),
                    bookmark(title = "title foo baz", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        whenever(mockTabRepository.flowTabs).thenReturn(
            flowOf(
                listOf(
                    TabEntity(tabId = "1", position = 1, title = "espn", url = "https://espn.com"),
                    TabEntity(tabId = "2", position = 2, title = "title", url = "https://baz.com"),
                ),
            ),
        )

        val result = testee.autoComplete(searchTerm)
        val value = result.first()

        assertEquals(7, value.suggestions.size)
        assertEquals(
            listOf(
                AutoCompleteSwitchToTabSuggestion(phrase = "espn.com", "espn", "https://espn.com", tabId = "1"),
                AutoCompleteBookmarkSuggestion(phrase = "espn.com", title = "espn", url = "https://espn.com", isFavorite = false),
                AutoCompleteSearchSuggestion(phrase = "espn", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn.com", isUrl = true, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn fantasy football", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn sports", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn nba", isUrl = false, isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsNavigationalLinkThatIsTabAndFavoriteThenResultsAreShown() = runTest {
        val searchTerm = "espn"
        whenever(mockAutoCompleteService.autoComplete(searchTerm)).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("espn", isNav = false),
                AutoCompleteServiceRawResult("espn.com", isNav = true),
                AutoCompleteServiceRawResult("espn fantasy football", isNav = false),
                AutoCompleteServiceRawResult("espn sports", isNav = false),
                AutoCompleteServiceRawResult("espn nba", isNav = false),
            ),
        )

        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
            flowOf(
                listOf(
                    bookmark(title = "espn", url = "https://espn.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "the title foo", url = "https://foo.com"),
                    bookmark(title = "title foo baz", url = "https://baz.com"),
                ),
            ),
        )

        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
            flowOf(
                listOf(
                    favorite(title = "espn", url = "https://espn.com"),
                ),
            ),
        )

        whenever(mockTabRepository.flowTabs).thenReturn(
            flowOf(
                listOf(
                    TabEntity(tabId = "1", position = 1, title = "espn", url = "https://espn.com"),
                    TabEntity(tabId = "2", position = 2, title = "title", url = "https://baz.com"),
                ),
            ),
        )

        val result = testee.autoComplete(searchTerm)
        val value = result.first()

        assertEquals(7, value.suggestions.size)
        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "espn.com", title = "espn", url = "https://espn.com", isFavorite = true),
                AutoCompleteSwitchToTabSuggestion(phrase = "espn.com", "espn", "https://espn.com", tabId = "1"),
                AutoCompleteSearchSuggestion(phrase = "espn", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn.com", isUrl = true, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn fantasy football", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn sports", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn nba", isUrl = false, isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsNavigationalLinkThatIsTabAndAnotherTabResultAppearsThenResultsAreShown() = runTest {
        val searchTerm = "espn"
        whenever(mockAutoCompleteService.autoComplete(searchTerm)).thenReturn(
            listOf(
                AutoCompleteServiceRawResult("espn", isNav = false),
                AutoCompleteServiceRawResult("espn.com", isNav = true),
                AutoCompleteServiceRawResult("espn fantasy football", isNav = false),
                AutoCompleteServiceRawResult("espn sports", isNav = false),
                AutoCompleteServiceRawResult("espn nba", isNav = false),
            ),
        )

        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(flowOf(emptyList()))

        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))

        whenever(mockTabRepository.flowTabs).thenReturn(
            flowOf(
                listOf(
                    TabEntity(tabId = "1", position = 1, title = "espn", url = "https://espn.com"),
                    TabEntity(tabId = "2", position = 2, title = "espn nfl", url = "https://espn.com/nfl"),
                ),
            ),
        )

        val result = testee.autoComplete(searchTerm)
        val value = result.first()

        assertEquals(7, value.suggestions.size)
        assertEquals(
            listOf(
                AutoCompleteSwitchToTabSuggestion(phrase = "espn.com", "espn", "https://espn.com", tabId = "1"),
                AutoCompleteSwitchToTabSuggestion(phrase = "espn.com/nfl", "espn nfl", "https://espn.com/nfl", tabId = "2"),
                AutoCompleteSearchSuggestion(phrase = "espn", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn.com", isUrl = true, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn fantasy football", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn sports", isUrl = false, isAllowedInTopHits = false),
                AutoCompleteSearchSuggestion(phrase = "espn nba", isUrl = false, isAllowedInTopHits = false),
            ),
            value.suggestions,
        )
    }

    private fun favorite(
        id: String = UUID.randomUUID().toString(),
        title: String = "title",
        url: String = "https://example.com",
        lastModified: String = DatabaseDateFormatter.iso8601(),
        position: Int = 1,
    ) = Favorite(id, title, url, lastModified, position)

    private fun bookmark(
        id: String = UUID.randomUUID().toString(),
        title: String = "title",
        url: String = "https://example.com",
    ) = Bookmark(id, title, url, SavedSitesNames.BOOKMARKS_ROOT, DatabaseDateFormatter.iso8601())

    private fun bookmarks() = listOf(
        Bookmark(
            UUID.randomUUID().toString(),
            "title",
            "https://example.com",
            SavedSitesNames.BOOKMARKS_ROOT,
            DatabaseDateFormatter.iso8601(),
        ),
    )
}
