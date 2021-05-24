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
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.service.BookmarksManager
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
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

    private val captor: ArgumentCaptor<BookmarksViewModel.Command> = ArgumentCaptor.forClass(BookmarksViewModel.Command::class.java)
    private val commandObserver: Observer<BookmarksViewModel.Command> = mock()

    private val liveData = MutableLiveData<List<BookmarkEntity>>()
    private val viewStateObserver: Observer<BookmarksViewModel.ViewState> = mock()
    private val bookmarksDao: BookmarksDao = mock()
    private val favoritesRepository: FavoritesRepository = mock()
    private val faviconManager: FaviconManager = mock()
    private val bookmarksManager: BookmarksManager = mock()

    private val bookmark = SavedSite.Bookmark(id = 0, title = "title", url = "www.example.com")
    private val favorite = SavedSite.Favorite(id = 0, title = "title", url = "www.example.com", position = 0)
    private val bookmarkEntity = BookmarkEntity(id = bookmark.id, title = bookmark.title, url = bookmark.url)

    private val testee: BookmarksViewModel by lazy {
        val model = BookmarksViewModel(favoritesRepository, bookmarksDao, faviconManager, bookmarksManager, coroutineRule.testDispatcherProvider)
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() = coroutineRule.runBlocking {
        liveData.value = emptyList()
        whenever(bookmarksDao.getBookmarks()).thenReturn(liveData)
        whenever(favoritesRepository.favorites()).thenReturn(flowOf())
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
        testee.command.removeObserver(commandObserver)
    }

    @Test
    fun whenBookmarkInsertedThenDaoUpdated() {
        testee.insert(bookmark)

        verify(bookmarksDao).insert(bookmarkEntity)
    }

    @Test
    fun whenFavoriteInsertedThenRepositoryUpdated() = coroutineRule.runBlocking {
        testee.insert(favorite)

        verify(favoritesRepository).insert(favorite)
    }

    @Test
    fun whenBookmarkDeletedThenDaoUpdated() = coroutineRule.runBlocking {
        testee.delete(bookmark)

        verify(faviconManager).deletePersistedFavicon(bookmark.url)
        verify(bookmarksDao).delete(bookmarkEntity)
    }

    @Test
    fun whenFavoriteDeletedThenDeleteFromRepository() = coroutineRule.runBlocking {
        testee.delete(favorite)

        verify(favoritesRepository).delete(favorite)
    }

    @Test
    fun whenBookmarkEditedThenDaoUpdated() = coroutineRule.runBlocking {
        testee.onSavedSiteEdited(bookmark)

        verify(bookmarksDao).update(bookmarkEntity)
    }

    @Test
    fun whenFavoriteEditedThenRepositoryUpdated() = coroutineRule.runBlocking {
        testee.onSavedSiteEdited(favorite)

        verify(favoritesRepository).update(favorite)
    }

    @Test
    fun whenSavedSiteSelectedThenOpenCommand() {
        testee.onSelected(bookmark)

        verify(commandObserver).onChanged(captor.capture())
        assertNotNull(captor.value)
        assertTrue(captor.value is BookmarksViewModel.Command.OpenSavedSite)
    }

    @Test
    fun whenDeleteRequestedThenConfirmCommand() {
        testee.onDeleteSavedSiteRequested(bookmark)

        verify(commandObserver).onChanged(captor.capture())
        assertNotNull(captor.value)
        assertTrue(captor.value is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
    }

    @Test
    fun whenBookmarksChangedThenObserverNotified() {
        testee
        val captor: ArgumentCaptor<BookmarksViewModel.ViewState> = ArgumentCaptor.forClass(BookmarksViewModel.ViewState::class.java)
        verify(viewStateObserver).onChanged(captor.capture())
        assertNotNull(captor.value)
        assertNotNull(captor.value.bookmarks)
    }

    @Test
    fun whenFavoritesChangedThenObserverNotified() = coroutineRule.runBlocking {
        whenever(favoritesRepository.favorites()).thenReturn(
            flow {
                emit(emptyList<SavedSite.Favorite>())
                emit(listOf(favorite))
            }
        )
        testee
        val captor: ArgumentCaptor<BookmarksViewModel.ViewState> = ArgumentCaptor.forClass(BookmarksViewModel.ViewState::class.java)
        verify(viewStateObserver).onChanged(captor.capture())
        assertNotNull(captor.value)
        assertEquals(1, captor.value.favorites.size)
    }
}
