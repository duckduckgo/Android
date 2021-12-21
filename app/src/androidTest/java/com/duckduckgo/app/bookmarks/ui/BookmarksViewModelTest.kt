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
import androidx.lifecycle.Observer
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFolderEntity
import com.duckduckgo.app.bookmarks.model.*
import com.duckduckgo.app.bookmarks.service.SavedSitesManager
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong

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

    private val commandCaptor: ArgumentCaptor<BookmarksViewModel.Command> = ArgumentCaptor.forClass(BookmarksViewModel.Command::class.java)
    private val viewStateCaptor: ArgumentCaptor<BookmarksViewModel.ViewState> = ArgumentCaptor.forClass(BookmarksViewModel.ViewState::class.java)

    private val commandObserver: Observer<BookmarksViewModel.Command> = mock()

    private val viewStateObserver: Observer<BookmarksViewModel.ViewState> = mock()
    private val favoritesRepository: FavoritesRepository = mock()
    private val bookmarksRepository: BookmarksRepository = mock()
    private val faviconManager: FaviconManager = mock()
    private val savedSitesManager: SavedSitesManager = mock()
    private val pixel: Pixel = mock()

    private val bookmark = SavedSite.Bookmark(id = 0, title = "title", url = "www.example.com", parentId = 0)
    private val favorite = SavedSite.Favorite(id = 0, title = "title", url = "www.example.com", position = 0)
    private val bookmarkFolder = BookmarkFolder(id = 1, name = "folder", parentId = 0)
    private val bookmarkEntity = BookmarkEntity(id = bookmark.id, title = bookmark.title, url = bookmark.url, parentId = 0)

    private val testee: BookmarksViewModel by lazy {
        val model = BookmarksViewModel(favoritesRepository, bookmarksRepository, faviconManager, savedSitesManager, pixel, coroutineRule.testDispatcherProvider)
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() = runTest {
        whenever(favoritesRepository.favorites()).thenReturn(flowOf())

        whenever(bookmarksRepository.fetchBookmarksAndFolders(anyLong())).thenReturn(flowOf(Pair(listOf(bookmark), listOf(bookmarkFolder, bookmarkFolder, bookmarkFolder))))
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
        testee.command.removeObserver(commandObserver)
    }

    @Test
    fun whenBookmarkInsertedThenDaoUpdated() = runTest {
        testee.insert(bookmark)

        verify(bookmarksRepository).insert(bookmark)
    }

    @Test
    fun whenFavoriteInsertedThenRepositoryUpdated() = runTest {
        testee.insert(favorite)

        verify(favoritesRepository).insert(favorite)
    }

    @Test
    fun whenBookmarkDeleteRequestedThenDaoUpdated() = runTest {
        testee.onDeleteSavedSiteRequested(bookmark)

        verify(faviconManager).deletePersistedFavicon(bookmark.url)
        verify(bookmarksRepository).delete(bookmark)
    }

    @Test
    fun whenFavoriteDeleteRequestedThenDeleteFromRepository() = runTest {
        testee.onDeleteSavedSiteRequested(favorite)

        verify(favoritesRepository).delete(favorite)
    }

    @Test
    fun whenBookmarkEditedThenDaoUpdated() = runTest {
        testee.onSavedSiteEdited(bookmark)

        verify(bookmarksRepository).update(bookmark)
    }

    @Test
    fun whenFavoriteEditedThenRepositoryUpdated() = runTest {
        testee.onSavedSiteEdited(favorite)

        verify(favoritesRepository).update(favorite)
    }

    @Test
    fun whenSavedSiteSelectedThenOpenCommand() {
        testee.onSelected(bookmark)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.value)
        assertTrue(commandCaptor.value is BookmarksViewModel.Command.OpenSavedSite)
    }

    @Test
    fun whenFavoriteSelectedThenPixelSent() {
        testee.onSelected(favorite)

        verify(pixel).fire(AppPixelName.FAVORITE_BOOKMARKS_ITEM_PRESSED)
    }

    @Test
    fun whenDeleteRequestedThenConfirmCommand() {
        testee.onDeleteSavedSiteRequested(bookmark)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.value)
        assertTrue(commandCaptor.value is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
    }

    @Test
    fun whenBookmarksChangedThenObserverNotified() {
        testee
        verify(viewStateObserver).onChanged(viewStateCaptor.capture())
        assertNotNull(viewStateCaptor.value)
        assertNotNull(viewStateCaptor.value.bookmarks)
    }

    @Test
    fun whenFavoritesChangedThenObserverNotified() = runTest {
        whenever(favoritesRepository.favorites()).thenReturn(
            flow {
                emit(emptyList<SavedSite.Favorite>())
                emit(listOf(favorite))
            }
        )
        testee
        verify(viewStateObserver).onChanged(viewStateCaptor.capture())
        assertNotNull(viewStateCaptor.value)
        assertEquals(1, viewStateCaptor.value.favorites.size)
    }

    @Test
    fun whenBookmarkFolderSelectedThenIssueOpenBookmarkFolderCommand() {
        testee.onBookmarkFolderSelected(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.OpenBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenFetchBookmarksAndFoldersThenUpdateStateWithCollectedBookmarksAndFolders() = runTest {
        val parentId = 1L

        testee.fetchBookmarksAndFolders(parentId = parentId)

        verify(bookmarksRepository).fetchBookmarksAndFolders(parentId)

        verify(viewStateObserver, times(2)).onChanged(viewStateCaptor.capture())

        assertEquals(emptyList<BookmarkEntity>(), viewStateCaptor.allValues[0].bookmarks)
        assertEquals(emptyList<BookmarkFolder>(), viewStateCaptor.allValues[0].bookmarkFolders)
        assertEquals(false, viewStateCaptor.allValues[0].enableSearch)

        assertEquals(listOf(bookmark), viewStateCaptor.allValues[1].bookmarks)
        assertEquals(listOf(bookmarkFolder, bookmarkFolder, bookmarkFolder), viewStateCaptor.allValues[1].bookmarkFolders)
        assertEquals(true, viewStateCaptor.allValues[1].enableSearch)
    }

    @Test
    fun whenBookmarkFolderAddedThenCallInsertOnRepository() = runTest {
        testee.onBookmarkFolderAdded(bookmarkFolder)

        verify(bookmarksRepository).insert(bookmarkFolder)
    }

    @Test
    fun whenEditBookmarkFolderThenIssueShowEditBookmarkFolderCommand() {
        testee.onEditBookmarkFolderRequested(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.ShowEditBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenBookmarkFolderUpdatedThenCallUpdateOnRepository() = runTest {
        testee.onBookmarkFolderUpdated(bookmarkFolder)

        verify(bookmarksRepository).update(bookmarkFolder)
    }

    @Test
    fun whenDeleteEmptyBookmarkFolderRequestedThenDeleteFolderAndIssueConfirmDeleteBookmarkFolderCommand() = runTest {
        val bookmarkFolderBranch = BookmarkFolderBranch(
            listOf(bookmarkEntity),
            listOf(BookmarkFolderEntity(bookmarkFolder.id, bookmarkFolder.name, bookmarkFolder.parentId))
        )
        whenever(bookmarksRepository.deleteFolderBranch(any())).thenReturn(bookmarkFolderBranch)

        testee.onDeleteBookmarkFolderRequested(bookmarkFolder)

        verify(bookmarksRepository).deleteFolderBranch(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).bookmarkFolder)
        assertEquals(bookmarkFolderBranch, (commandCaptor.value as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).folderBranch)
    }

    @Test
    fun whenDeleteNonEmptyBookmarkFolderRequestedThenIssueDeleteBookmarkFolderCommand() = runTest {
        val bookmarkFolderBranch = BookmarkFolderBranch(
            listOf(bookmarkEntity),
            listOf(BookmarkFolderEntity(bookmarkFolder.id, bookmarkFolder.name, bookmarkFolder.parentId))
        )
        whenever(bookmarksRepository.deleteFolderBranch(any())).thenReturn(bookmarkFolderBranch)

        val nonEmptyBookmarkFolder = bookmarkFolder.copy(numBookmarks = 1)
        testee.onDeleteBookmarkFolderRequested(nonEmptyBookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(nonEmptyBookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.DeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenInsertRecentlyDeletedBookmarksAndFoldersThenInsertCachedFolderBranch() = runTest {
        val bookmarkFolderBranch = BookmarkFolderBranch(
            listOf(bookmarkEntity),
            listOf(BookmarkFolderEntity(bookmarkFolder.id, bookmarkFolder.name, bookmarkFolder.parentId))
        )

        testee.insertDeletedFolderBranch(bookmarkFolderBranch)

        verify(bookmarksRepository).insertFolderBranch(bookmarkFolderBranch)
    }
}
