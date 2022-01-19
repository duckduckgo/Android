/*
 * Copyright (c) 2019 DuckDuckGo
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

import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import org.mockito.kotlin.whenever
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class AutoCompleteApiTest {

    @Mock
    private lateinit var mockAutoCompleteService: AutoCompleteService

    @Mock
    private lateinit var mockBookmarksDao: BookmarksDao

    @Mock
    private lateinit var mockFavoritesRepository: FavoritesRepository

    private lateinit var testee: AutoCompleteApi

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        testee = AutoCompleteApi(mockAutoCompleteService, mockBookmarksDao, mockFavoritesRepository)
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
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(Single.just(listOf(BookmarkEntity(0, "title", "https://example.com", 0))))
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals("example.com", value.suggestions[0].phrase)
    }

    @Test
    fun whenAutoCompleteDoesNotMatchAnySavedSiteReturnEmptySavedSiteList() {
        whenever(mockAutoCompleteService.autoComplete("wrong")).thenReturn(Observable.just(emptyList()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(Single.just(listOf(BookmarkEntity(0, "title", "https://example.com", 0))))
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(listOf(favorite(title = "title"))))

        val result = testee.autoComplete("wrong").test()
        val value = result.values()[0] as AutoCompleteResult

        assertTrue(value.suggestions.isEmpty())
    }

    @Test
    fun whenAutoCompleteReturnsMultipleBookmarkHitsThenLimitToMaxOfTwo() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(Observable.just(emptyList()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "title", "https://example.com", 0),
                    BookmarkEntity(0, "title", "https://foo.com", 0),
                    BookmarkEntity(0, "title", "https://bar.com", 0),
                    BookmarkEntity(0, "title", "https://baz.com", 0)
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title", "https://foo.com"),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenAutoCompleteReturnsMultipleSavedSitesHitsThenLimitToMaxOfTwoFavoritesFirst() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(Observable.just(emptyList()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "title", "https://example.com", 0),
                    BookmarkEntity(0, "title", "https://foo.com", 0),
                    BookmarkEntity(0, "title", "https://bar.com", 0),
                    BookmarkEntity(0, "title", "https://baz.com", 0)
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(
            Single.just(
                listOf(
                    favorite(title = "title", url = "https://favexample.com"),
                    favorite(title = "title", url = "https://favfoo.com"),
                    favorite(title = "title", url = "https://favbar.com"),
                    favorite(title = "title", url = "https://favbaz.com")
                )
            )
        )

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "favexample.com", "title", "https://favexample.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "favfoo.com", "title", "https://favfoo.com"),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenAutoCompleteReturnsDuplicatedItemsThenDedup() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(
            Observable.just(
                listOf(
                    AutoCompleteServiceRawResult("example.com", false),
                    AutoCompleteServiceRawResult("foo.com", true),
                    AutoCompleteServiceRawResult("bar.com", true),
                    AutoCompleteServiceRawResult("baz.com", true)
                )
            )
        )
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "title example", "https://example.com", 0),
                    BookmarkEntity(0, "title foo", "https://foo.com/path/to/foo", 0),
                    BookmarkEntity(0, "title foo", "https://foo.com", 0),
                    BookmarkEntity(0, "title bar", "https://bar.com", 0)
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(
            Single.just(
                listOf(
                    favorite(title = "title example", url = "https://example.com"),
                    favorite(title = "title foo", url = "https://foo.com/path/to/foo"),
                    favorite(title = "title foo", url = "https://foo.com"),
                    favorite(title = "title bar", url = "https://bar.com")
                )
            )
        )

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "example.com", "title example", "https://example.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(
                    phrase = "foo.com/path/to/foo",
                    "title foo",
                    "https://foo.com/path/to/foo"
                ),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion(phrase = "foo.com", true),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion(phrase = "bar.com", true),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion(phrase = "baz.com", true)
            ),
            value.suggestions
        )
    }

    @Test
    fun whenReturnOneBookmarkAndOneFavoriteSuggestionsThenShowBothFavoriteFirst() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(Observable.just(emptyList()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(Single.just(listOf(BookmarkEntity(0, "title", "https://example.com", 0))))
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(listOf(Favorite(0, "title", "https://favexample.com", 1))))

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "favexample.com", "title", "https://favexample.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "example.com", "title", "https://example.com")
            ),
            value.suggestions
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
                    AutoCompleteServiceRawResult("baz.com", true)
                )
            )
        )
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "title foo", "https://foo.com?key=value", 0),
                    BookmarkEntity(0, "title foo", "https://foo.com", 0),
                    BookmarkEntity(0, "title bar", "https://bar.com", 0)
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(
                    phrase = "foo.com?key=value",
                    "title foo",
                    "https://foo.com?key=value"
                ),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title foo", "https://foo.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion(phrase = "example.com", false),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion(phrase = "bar.com", true),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion(phrase = "baz.com", true)
            ),
            value.suggestions
        )
    }

    @Test
    fun whenBookmarkTitleStartsWithQueryThenScoresHigher() {
        whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "the title example", "https://example.com", 0),
                    BookmarkEntity(0, "the title foo", "https://foo.com/path/to/foo", 0),
                    BookmarkEntity(0, "title bar", "https://bar.com", 0),
                    BookmarkEntity(0, "the title foo", "https://foo.com", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("title").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "bar.com", "title bar", "https://bar.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(
                    phrase = "example.com",
                    "the title example",
                    "https://example.com"
                ),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenSingleTokenQueryAndBookmarkDomainStartsWithItThenScoreHigher() {
        whenever(mockAutoCompleteService.autoComplete("foo")).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "title example", "https://example.com", 0),
                    BookmarkEntity(0, "title bar", "https://bar.com", 0),
                    BookmarkEntity(0, "title foo", "https://foo.com", 0),
                    BookmarkEntity(0, "title baz", "https://baz.com", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("foo").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "foo.com", "title foo", "https://foo.com"),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenSingleTokenQueryAndBookmarkReturnsDuplicatedItemsThenDedup() {
        whenever(mockAutoCompleteService.autoComplete("cnn")).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "CNN international", "https://cnn.com", 0),
                    BookmarkEntity(0, "CNN international", "https://cnn.com", 0),
                    BookmarkEntity(0, "CNN international - world", "https://cnn.com/world", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("cnn").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "cnn.com", "CNN international", "https://cnn.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(
                    phrase = "cnn.com/world",
                    "CNN international - world",
                    "https://cnn.com/world"
                ),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenSingleTokenQueryEndsWithSlashThenIgnoreItWhileMatching() {
        whenever(mockAutoCompleteService.autoComplete("reddit.com/")).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "Reddit", "https://reddit.com", 0),
                    BookmarkEntity(0, "Reddit - duckduckgo", "https://reddit.com/r/duckduckgo", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("reddit.com/").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "reddit.com", "Reddit", "https://reddit.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(
                    phrase = "reddit.com/r/duckduckgo",
                    "Reddit - duckduckgo",
                    "https://reddit.com/r/duckduckgo"
                ),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenSingleTokenQueryEndsWithMultipleSlashThenIgnoreThemWhileMatching() {
        whenever(mockAutoCompleteService.autoComplete("reddit.com///")).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "Reddit", "https://reddit.com", 0),
                    BookmarkEntity(0, "Reddit - duckduckgo", "https://reddit.com/r/duckduckgo", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("reddit.com///").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "reddit.com", "Reddit", "https://reddit.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(
                    phrase = "reddit.com/r/duckduckgo",
                    "Reddit - duckduckgo",
                    "https://reddit.com/r/duckduckgo"
                ),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenSingleTokenQueryContainsMultipleSlashThenIgnoreThemWhileMatching() {
        whenever(mockAutoCompleteService.autoComplete("reddit.com/r//")).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "Reddit", "https://reddit.com", 0),
                    BookmarkEntity(0, "Reddit - duckduckgo", "https://reddit.com/r/duckduckgo", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("reddit.com/r//").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(
                    phrase = "reddit.com/r/duckduckgo",
                    "Reddit - duckduckgo",
                    "https://reddit.com/r/duckduckgo"
                ),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenSingleTokenQueryDomainContainsWwwThenResultMathUrl() {
        whenever(mockAutoCompleteService.autoComplete("reddit")).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "Reddit", "https://www.reddit.com", 0),
                    BookmarkEntity(0, "duckduckgo", "https://www.reddit.com/r/duckduckgo", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete("reddit").test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "www.reddit.com", "Reddit", "https://www.reddit.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(
                    phrase = "www.reddit.com/r/duckduckgo",
                    "duckduckgo",
                    "https://www.reddit.com/r/duckduckgo"
                ),
            ),
            value.suggestions
        )
    }

    @Test
    fun whenMultipleTokenQueryAndNoTokenMatchThenReturnEmpty() {
        val query = "example title foo"
        whenever(mockAutoCompleteService.autoComplete(query)).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "title example", "https://example.com", 0),
                    BookmarkEntity(0, "title bar", "https://bar.com", 0),
                    BookmarkEntity(0, "the title foo", "https://foo.com", 0),
                    BookmarkEntity(0, "title baz", "https://baz.com", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete(query).test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(listOf<AutoComplete.AutoCompleteSuggestion>(), value.suggestions)
    }

    @Test
    fun whenMultipleTokenQueryAndMultipleMatchesThenReturnCorrectScore() {
        val query = "title foo"
        whenever(mockAutoCompleteService.autoComplete(query)).thenReturn(Observable.just(listOf()))
        whenever(mockBookmarksDao.bookmarksObservable()).thenReturn(
            Single.just(
                listOf(
                    BookmarkEntity(0, "title example", "https://example.com", 0),
                    BookmarkEntity(0, "title bar", "https://bar.com", 0),
                    BookmarkEntity(0, "the title foo", "https://foo.com", 0),
                    BookmarkEntity(0, "title foo baz", "https://baz.com", 0),
                )
            )
        )
        whenever(mockFavoritesRepository.favoritesObservable()).thenReturn(Single.just(emptyList()))

        val result = testee.autoComplete(query).test()
        val value = result.values()[0] as AutoCompleteResult

        assertEquals(
            listOf(
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "baz.com", "title foo baz", "https://baz.com"),
                AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion(phrase = "foo.com", "the title foo", "https://foo.com"),
            ),
            value.suggestions
        )
    }

    private fun favorite(
        id: Long = 0,
        title: String = "title",
        url: String = "https://example.com",
        position: Int = 1
    ) = Favorite(id, title, url, position)
}
