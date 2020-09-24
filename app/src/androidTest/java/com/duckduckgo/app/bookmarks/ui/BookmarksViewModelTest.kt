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

package com.duckduckgo.app.bookmarks.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor

@ExperimentalCoroutinesApi
class BookmarksViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val liveData = MutableLiveData<List<BookmarkEntity>>()
    private val viewStateObserver: Observer<BookmarksViewModel.ViewState> = mock()
    private val commandObserver: Observer<BookmarksViewModel.Command> = mock()
    private val bookmarksDao: BookmarksDao = mock()
    private val faviconManager: FaviconManager = mock()

    private val bookmark = BookmarkEntity(title = "title", url = "www.example.com")

    private val testee: BookmarksViewModel by lazy {
        val model = BookmarksViewModel(bookmarksDao, faviconManager, coroutineRule.testDispatcherProvider)
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() {
        liveData.value = emptyList()
        whenever(bookmarksDao.bookmarks()).thenReturn(liveData)
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
        testee.command.removeObserver(commandObserver)
    }

    @Test
    fun whenBookmarkDeletedThenDaoUpdated() {
        testee.delete(bookmark)
        verify(bookmarksDao).delete(bookmark)
    }

    @Test
    fun whenBookmarkSelectedThenOpenCommand() {
        testee.onSelected(bookmark)
        val captor: ArgumentCaptor<BookmarksViewModel.Command> = ArgumentCaptor.forClass(BookmarksViewModel.Command::class.java)
        verify(commandObserver).onChanged(captor.capture())
        assertNotNull(captor.value)
        assertTrue(captor.value is BookmarksViewModel.Command.OpenBookmark)
    }

    @Test
    fun whenDeleteRequestedThenConfirmCommand() {
        testee.onDeleteRequested(bookmark)
        val captor: ArgumentCaptor<BookmarksViewModel.Command> = ArgumentCaptor.forClass(BookmarksViewModel.Command::class.java)
        verify(commandObserver).onChanged(captor.capture())
        assertNotNull(captor.value)
        assertTrue(captor.value is BookmarksViewModel.Command.ConfirmDeleteBookmark)
    }

    @Test
    fun whenBookmarksChangedThenObserverNotified() {
        testee
        val captor: ArgumentCaptor<BookmarksViewModel.ViewState> = ArgumentCaptor.forClass(BookmarksViewModel.ViewState::class.java)
        verify(viewStateObserver).onChanged(captor.capture())
        assertNotNull(captor.value)
        assertNotNull(captor.value.bookmarks)
    }
}
