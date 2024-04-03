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
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import io.reactivex.Observable
import io.reactivex.Single
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AutoCompleteApiTest {

    @Mock
    private lateinit var mockAutoCompleteService: AutoCompleteService

    @Mock
    private lateinit var mockSavedSitesRepository: SavedSitesRepository

    private lateinit var testee: AutoCompleteApi

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        testee = AutoCompleteApi(mockAutoCompleteService, mockSavedSitesRepository)
    }

    @Test
    fun whenQueryIsBlankThenReturnAnEmptyList() {
        val result = testee.autoComplete("").test()
        val value = result.values()[0] as AutoCompleteResult

        assertTrue(value.suggestions.isEmpty())
    }

    @Test
    fun whenReturnBookmarkSuggestionsThenPhraseIsURLBaseHost() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(Observable.just(emptyList()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(Single.just(bookmarks()))
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals("example.com", value.suggestions[0].phrase)
    }

    @Test
    fun whenAutoCompleteDoesNotMatchAnySavedSiteReturnEmptySavedSiteList() {
        whenever(mockAutoCompleteService.autoComplete("wrong")).thenReturn(Observable.just(emptyList()))

        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(
                        title = "title",
                        url = "https://example.com",
                    ),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(listOf(favorite(title = "title"))))

        val result = testee.autoComplete("wrong").test()
        val value = result.values()[0] as AutoCompleteResult

        assertTrue(value.suggestions.isEmpty())
    }

    @Test
    fun whenAutoCompleteReturnsMultipleBookmarkAndFavoriteHitsOnlyShowFavoritesBeforeSearchSuggestions() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            Observable.just(
                listOf(
                    AutoCompleteServiceRawResult("foo", isNav = false),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(
            Single.just(
                listOf(
                    favorite(title = "title", url = "https://example.com"),
                    favorite(title = "title", url = "https://foo.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "title", url = "https://bar.com"),
                    bookmark(title = "title", url = "https://baz.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isAllowedInTopHits = true, isFavorite = true),
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title", "https://foo.com", isAllowedInTopHits = true, isFavorite = true),
                AutoCompleteSearchSuggestion("foo", false),
                AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title", "https://bar.com", isAllowedInTopHits = false, isFavorite = false),
                AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title", "https://baz.com", isAllowedInTopHits = false, isFavorite = false),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsMultipleFavoriteHitsLimitTopHitsTo2() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            Observable.just(
                listOf(
                    AutoCompleteServiceRawResult("foo", isNav = false),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(
            Single.just(
                listOf(
                    favorite(title = "title", url = "https://example.com"),
                    favorite(title = "title", url = "https://foo.com"),
                    favorite(title = "title", url = "https://bar.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(),
            ),
        )

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com", isAllowedInTopHits = true, isFavorite = true),
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title", "https://foo.com", isAllowedInTopHits = true, isFavorite = true),
                AutoCompleteSearchSuggestion("foo", false),
                AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title", "https://bar.com", isAllowedInTopHits = true, isFavorite = true),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsMultipleSavedSitesHitsThenShowFavcritesFirst() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(Observable.just(emptyList()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "title", url = "https://example.com"),
                    bookmark(title = "title", url = "https://foo.com"),
                    bookmark(title = "title", url = "https://bar.com"),
                    bookmark(title = "title", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(
            Single.just(
                listOf(
                    favorite(title = "title", url = "https://favexample.com"),
                    favorite(title = "title", url = "https://favfoo.com"),
                    favorite(title = "title", url = "https://favbar.com"),
                    favorite(title = "title", url = "https://favbaz.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertTrue((value.suggestions[0] as AutoCompleteBookmarkSuggestion).isAllowedInTopHits)
        assertTrue((value.suggestions[1] as AutoCompleteBookmarkSuggestion).isAllowedInTopHits)
        assertTrue((value.suggestions[2] as AutoCompleteBookmarkSuggestion).isAllowedInTopHits)
        assertTrue((value.suggestions[3] as AutoCompleteBookmarkSuggestion).isAllowedInTopHits)
        assertFalse((value.suggestions[4] as AutoCompleteBookmarkSuggestion).isAllowedInTopHits)
    }

    @Test
    fun whenAutoCompleteReturnsDuplicatedItemsThenDedup() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            Observable.just(
                listOf(
                    AutoCompleteServiceRawResult("example.com", false),
                    AutoCompleteServiceRawResult("foo.com", true),
                    AutoCompleteServiceRawResult("bar.com", true),
                    AutoCompleteServiceRawResult("baz.com", true),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                    bookmark(title = "title foo", url = "https://foo.com/path/to/foo"),
                    bookmark(title = "title foo", url = "https://foo.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(
            Single.just(
                listOf(
                    favorite(title = "title example", url = "https://example.com"),
                    favorite(title = "title foo", url = "https://foo.com/path/to/foo"),
                    favorite(title = "title foo", url = "https://foo.com"),
                    favorite(title = "title bar", url = "https://bar.com"),
                ),
            ),
        )

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(
                    phrase = "example.com",
                    "title example",
                    "https://example.com",
                    isAllowedInTopHits = true,
                    isFavorite = true,
                ),
                AutoCompleteBookmarkSuggestion(
                    phrase = "foo.com/path/to/foo",
                    "title foo",
                    "https://foo.com/path/to/foo",
                    isAllowedInTopHits = true,
                    isFavorite = true,
                ),
                AutoCompleteSearchSuggestion(phrase = "baz.com", true),
                AutoCompleteBookmarkSuggestion(
                    phrase = "foo.com",
                    title = "title foo",
                    url = "https://foo.com",
                    isAllowedInTopHits = true,
                    isFavorite = true,
                ),
                AutoCompleteBookmarkSuggestion(
                    phrase = "bar.com",
                    title = "title bar",
                    url = "https://bar.com",
                    isAllowedInTopHits = true,
                    isFavorite = true,
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenReturnOneBookmarkAndOneFavoriteSuggestionsThenShowBothFavoriteFirst() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(Observable.just(emptyList()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(
                        title = "title",
                        url = "https://example.com",
                    ),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(
            Single.just(
                listOf(
                    favorite(
                        title = "title",
                        url = "https://favexample.com",
                    ),
                ),
            ),
        )

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(
                    phrase = "favexample.com",
                    title = "title",
                    url = "https://favexample.com",
                    isAllowedInTopHits = true,
                    isFavorite = true,
                ),
                AutoCompleteBookmarkSuggestion(
                    phrase = "example.com",
                    title = "title",
                    url = "https://example.com",
                    isAllowedInTopHits = false,
                    isFavorite = false,
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenAutoCompleteReturnsDuplicatedItemsThenDedupConsideringQueryParams() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            Observable.just(
                listOf(
                    AutoCompleteServiceRawResult("example.com", false),
                    AutoCompleteServiceRawResult("foo.com", true),
                    AutoCompleteServiceRawResult("bar.com", true),
                    AutoCompleteServiceRawResult("baz.com", true),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "title foo", url = "https://foo.com?key=value"),
                    bookmark(title = "title foo", url = "https://foo.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoCompleteSearchSuggestion(phrase = "example.com", false),
                AutoCompleteSearchSuggestion(phrase = "baz.com", true),
                AutoCompleteBookmarkSuggestion(
                    phrase = "foo.com?key=value",
                    "title foo",
                    "https://foo.com?key=value",
                ),
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title foo", "https://foo.com"),
                AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title bar", "https://bar.com"),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenBookmarkTitleStartsWithQueryThenScoresHigher() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "the title example", url = "https://example.com"),
                    bookmark(title = "the title foo", url = "https://foo.com/path/to/foo"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "the title foo", url = "https://foo.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

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
    fun whenSingleTokenQueryAndBookmarkDomainStartsWithItThenScoreHigher() {
        whenever(mockAutoCompleteService.autoComplete("foo")).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "title foo", url = "https://foo.com"),
                    bookmark(title = "title baz", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("foo").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title foo", "https://foo.com"),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenSingleTokenQueryAndBookmarkReturnsDuplicatedItemsThenDedup() {
        whenever(mockAutoCompleteService.autoComplete("cnn")).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "CNN international", url = "https://cnn.com"),
                    bookmark(title = "CNN international", url = "https://cnn.com"),
                    bookmark(title = "CNN international - world", url = "https://cnn.com/world"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("cnn").test()
        val value = result.values()[0] as AutoCompleteResult

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
    fun whenSingleTokenQueryEndsWithSlashThenIgnoreItWhileMatching() {
        whenever(mockAutoCompleteService.autoComplete("reddit.com/")).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "Reddit", url = "https://reddit.com"),
                    bookmark(title = "Reddit - duckduckgo", url = "https://reddit.com/r/duckduckgo"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("reddit.com/").test()
        val value = result.values()[0] as AutoCompleteResult

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
    fun whenSingleTokenQueryEndsWithMultipleSlashThenIgnoreThemWhileMatching() {
        whenever(mockAutoCompleteService.autoComplete("reddit.com///")).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "Reddit", url = "https://reddit.com"),
                    bookmark(title = "Reddit - duckduckgo", url = "https://reddit.com/r/duckduckgo"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("reddit.com///").test()
        val value = result.values()[0] as AutoCompleteResult

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
    fun whenSingleTokenQueryContainsMultipleSlashThenIgnoreThemWhileMatching() {
        whenever(mockAutoCompleteService.autoComplete("reddit.com/r//")).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "Reddit", url = "https://reddit.com"),
                    bookmark(title = "Reddit - duckduckgo", url = "https://reddit.com/r/duckduckgo"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("reddit.com/r//").test()
        val value = result.values()[0] as AutoCompleteResult

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
    fun whenSingleTokenQueryDomainContainsWwwThenResultMathUrl() {
        whenever(mockAutoCompleteService.autoComplete("reddit")).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "Reddit", url = "https://www.reddit.com"),
                    bookmark(title = "duckduckgo", url = "https://www.reddit.com/r/duckduckgo"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("reddit").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "www.reddit.com", "Reddit", "https://www.reddit.com"),
                AutoCompleteBookmarkSuggestion(
                    phrase = "www.reddit.com/r/duckduckgo",
                    "duckduckgo",
                    "https://www.reddit.com/r/duckduckgo",
                ),
            ),
            value.suggestions,
        )
    }

    @Test
    fun whenMultipleTokenQueryAndNoTokenMatchThenReturnEmpty() {
        val query = "example title foo"
        whenever(mockAutoCompleteService.autoComplete(query)).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "the title foo", url = "https://foo.com"),
                    bookmark(title = "title baz", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete(query).test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(listOf<AutoCompleteSuggestion>(), value.suggestions)
    }

    @Test
    fun whenMultipleTokenQueryAndMultipleMatchesThenReturnCorrectScore() {
        val query = "title foo"
        whenever(mockAutoCompleteService.autoComplete(query)).thenReturn(Observable.just(listOf()))
        whenever(mockSavedSitesRepository.getBookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    bookmark(title = "title example", url = "https://example.com"),
                    bookmark(title = "title bar", url = "https://bar.com"),
                    bookmark(title = "the title foo", url = "https://foo.com"),
                    bookmark(title = "title foo baz", url = "https://baz.com"),
                ),
            ),
        )
        whenever(mockSavedSitesRepository.getFavoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete(query).test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title foo baz", "https://baz.com"),
                AutoCompleteBookmarkSuggestion(phrase = "foo.com", "the title foo", "https://foo.com"),
            ),
            value.suggestions,
        )
    }

    @Test
    fun testWhenQueryIsJustWhitespaces_ThenTokensAreEmpty() {
        val query = "  \t\n\t\t \t \t  \n\n\n "
        val tokens = testee.tokensFrom(query)

        assertEquals(0, tokens.size)
    }

    @Test
    fun testWhenQueryContainsTabsOrNewlines_ThenResultIsTheSameAsIfThereAreSpaces() {
        val spaceQuery = "testing query tokens"
        val tabQuery = "testing\tquery\ttokens"
        val newlineQuery = "testing\nquery\ntokens"
        val spaceTokens = testee.tokensFrom(spaceQuery)
        val tabTokens = testee.tokensFrom(tabQuery)
        val newlineTokens = testee.tokensFrom(newlineQuery)

        assertEquals(listOf("testing", "query", "tokens"), spaceTokens)
        assertEquals(spaceTokens, tabTokens)
        assertEquals(spaceTokens, newlineTokens)
    }

    @Test
    fun testWhenURLMatchesWithQuery_ThenScoreIsIncreased() {
        val query = "testcase.com/no"
        val score = testee.score(
            "Test case website",
            "https://www.testcase.com/notroot".toUri(),
            100,
            query,
        )

        assertTrue(score > 0)
    }

    @Test
    fun testWhenTitleMatchesFromTheBeginning_ThenScoreIsIncreased() {
        val query = "test"
        val score1 = testee.score(
            "Test case website",
            "https://www.website.com".toUri(),
            100,
            query,
        )

        val score2 = testee.score(
            "Case test website 2",
            "https://www.website2.com".toUri(),
            100,
            query,
        )

        assertTrue(score1 > score2)
    }

    @Test
    fun testWhenDomainMatchesFromTheBeginning_ThenScoreIsIncreased() {
        val query = "test"
        val score1 = testee.score(
            "Website",
            "https://www.test.com".toUri(),
            100,
            query,
        )

        val score2 = testee.score(
            "Website 2",
            "https://www.websitetest.com".toUri(),
            100,
            query,
        )

        assertTrue(score1 > score2)
    }

    @Test
    fun testWhenThereIsMoreVisitCount_ThenScoreIsIncreased() {
        val query = "website"
        val score1 = testee.score(
            "Website",
            "https://www.website.com".toUri(),
            100,
            query,
        )

        val score2 = testee.score(
            "Website 2",
            "https://www.website2.com".toUri(),
            101,
            query,
        )

        assertTrue(score1 < score2)
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
