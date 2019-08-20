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

import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class AutoCompleteApiTest {

    @Mock
    private lateinit var mockAutoCompleteService: AutoCompleteService

    @Mock
    private lateinit var mockBookmarksDao: BookmarksDao

    private lateinit var testee: AutoCompleteApi

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = AutoCompleteApi(mockAutoCompleteService, mockBookmarksDao)
    }

    @Test
    fun whenQueryIsNotBlankThenTryToGetBookmarksFromDAO() {
        whenever(mockAutoCompleteService.autoComplete("foo")).thenReturn(Observable.just(emptyList()))
        whenever(mockBookmarksDao.bookmarksByQuery(anyString())).thenReturn(Single.just(emptyList()))

        testee.autoComplete("foo")

        verify(mockBookmarksDao).bookmarksByQuery("%foo%")
    }

    @Test
    fun whenQueryIsBlankThenReturnAnEmptyList() {
        val result = testee.autoComplete("").test()
        val value = result.values()[0] as AutoCompleteApi.AutoCompleteResult

        assertTrue(value.suggestions.isEmpty())
    }

    @Test
    fun whenReturnBookmarkSuggestionsThenPhraseIsSameAsURL() {
        whenever(mockAutoCompleteService.autoComplete("foo")).thenReturn(Observable.just(emptyList()))
        whenever(mockBookmarksDao.bookmarksByQuery(anyString())).thenReturn(Single.just(listOf(BookmarkEntity(0, "title", "https://example.com"))))

        val result = testee.autoComplete("foo").test()
        val value = result.values()[0] as AutoCompleteApi.AutoCompleteResult

        assertSame("https://example.com", value.suggestions[0].phrase)
    }
}