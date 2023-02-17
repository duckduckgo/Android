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
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFolderEntity
import com.duckduckgo.app.bookmarks.model.*
import com.duckduckgo.app.bookmarks.service.SavedSitesManager
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*

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
    private val savedSitesRepository: SavedSitesRepository = mock()
    private val faviconManager: FaviconManager = mock()
    private val savedSitesManager: SavedSitesManager = mock()
    private val pixel: Pixel = mock()

    private val bookmark = SavedSite.Bookmark(id = "bookmark1", title = "title", url = "www.example.com", parentId = Relation.BOOMARKS_ROOT)
    private val favorite = SavedSite.Favorite(id = "favorite1", title = "title", url = "www.example.com", position = 0)
    private val bookmarkFolder = BookmarkFolder(id = "folder1", name = "folder", parentId = Relation.BOOMARKS_ROOT)
    private val bookmarkEntity = Entity(entityId = bookmark.id, title = bookmark.title, url = bookmark.url, type = BOOKMARK)

    private val testee: BookmarksViewModel by lazy {
        val model = BookmarksViewModel(
            savedSitesRepository,
            faviconManager,
            savedSitesManager,
            pixel,
            coroutineRule.testDispatcherProvider,
        )
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() = runTest {
        whenever(savedSitesRepository.getFavorites()).thenReturn(flowOf())

        whenever(savedSitesRepository.getFolderContent(anyString())).thenReturn(
            flowOf(
                Pair(
                    listOf(bookmark),
                    listOf(bookmarkFolder, bookmarkFolder, bookmarkFolder),
                ),
            ),
        )
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
        testee.command.removeObserver(commandObserver)
    }

    @Test
    fun whenBookmarkInsertedThenDaoUpdated() = runTest {
        testee.insert(bookmark)

        verify(savedSitesRepository).insert(bookmark)
    }

    @Test
    fun whenFavoriteInsertedThenRepositoryUpdated() = runTest {
        testee.insert(favorite)

        verify(savedSitesRepository).insert(favorite)
    }

    @Test
    fun whenBookmarkDeleteRequestedThenDaoUpdated() = runTest {
        testee.onDeleteSavedSiteRequested(bookmark)

        verify(faviconManager).deletePersistedFavicon(bookmark.url)
        verify(savedSitesRepository).delete(bookmark)
    }

    @Test
    fun whenFavoriteDeleteRequestedThenDeleteFromRepository() = runTest {
        testee.onDeleteSavedSiteRequested(favorite)

        verify(savedSitesRepository).delete(favorite)
    }

    @Test
    fun whenBookmarkEditedThenDaoUpdated() = runTest {
        testee.onSavedSiteEdited(bookmark)

        verify(savedSitesRepository).update(bookmark)
    }

    @Test
    fun whenFavoriteEditedThenRepositoryUpdated() = runTest {
        testee.onSavedSiteEdited(favorite)

        verify(savedSitesRepository).update(favorite)
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
        whenever(savedSitesRepository.getFavorites()).thenReturn(
            flow {
                emit(emptyList())
                emit(listOf(favorite))
            },
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
        val parentId = "folder1"

        testee.fetchBookmarksAndFolders(parentId = parentId)

        verify(savedSitesRepository).getFolderContent(parentId)

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

        verify(savedSitesRepository).insert(bookmarkFolder)
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

        verify(savedSitesRepository).update(bookmarkFolder)
    }

    @Test
    fun whenDeleteEmptyBookmarkFolderRequestedThenDeleteFolderAndIssueConfirmDeleteBookmarkFolderCommand() = runTest {
        val bookmarkFolderBranch = BookmarkFolderBranch(
            listOf(bookmarkEntity),
            listOf(Entity(bookmarkFolder.id, bookmarkFolder.name, bookmarkFolder.parentId, type = FOLDER)),
        )
        whenever(savedSitesRepository.deleteFolderBranch(any())).thenReturn(bookmarkFolderBranch)

        testee.onDeleteBookmarkFolderRequested(bookmarkFolder)

        verify(savedSitesRepository).deleteFolderBranch(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).bookmarkFolder)
        assertEquals(bookmarkFolderBranch, (commandCaptor.value as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).folderBranch)
    }

    @Test
    fun whenDeleteNonEmptyBookmarkFolderRequestedThenIssueDeleteBookmarkFolderCommand() = runTest {
        val bookmarkFolderBranch = BookmarkFolderBranch(
            listOf(bookmarkEntity),
            listOf(Entity(bookmarkFolder.id, bookmarkFolder.name, bookmarkFolder.parentId, type = FOLDER)),
        )
        whenever(savedSitesRepository.deleteFolderBranch(any())).thenReturn(bookmarkFolderBranch)

        val nonEmptyBookmarkFolder = bookmarkFolder.copy(numBookmarks = 1)
        testee.onDeleteBookmarkFolderRequested(nonEmptyBookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(nonEmptyBookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.DeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenInsertRecentlyDeletedBookmarksAndFoldersThenInsertCachedFolderBranch() = runTest {
        val bookmarkFolderBranch = BookmarkFolderBranch(
            listOf(bookmarkEntity),
            listOf(Entity(bookmarkFolder.id, bookmarkFolder.name, bookmarkFolder.parentId, type = FOLDER)),
        )

        testee.insertDeletedFolderBranch(bookmarkFolderBranch)

        verify(savedSitesRepository).insertFolderBranch(bookmarkFolderBranch)
    }
}
