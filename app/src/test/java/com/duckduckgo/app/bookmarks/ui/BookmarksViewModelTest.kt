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
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.model.*
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem
import com.duckduckgo.savedsites.api.models.FolderBranch
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSites
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.service.SavedSitesManager
import com.duckduckgo.sync.api.engine.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
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
    private val syncEngine: SyncEngine = mock()
    private val pixel: Pixel = mock()

    private val bookmark =
        SavedSite.Bookmark(id = "bookmark1", title = "title", url = "www.example.com", parentId = SavedSitesNames.BOOKMARKS_ROOT, "timestamp")
    private val favorite = SavedSite.Favorite(id = "favorite1", title = "title", url = "www.example.com", position = 0, lastModified = "timestamp")
    private val bookmarkFolder = BookmarkFolder(id = "folder1", name = "folder", parentId = SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
    private val bookmarkFolderItem = BookmarkFolderItem(0, bookmarkFolder, true)

    private val testee: BookmarksViewModel by lazy {
        val model = BookmarksViewModel(
            savedSitesRepository,
            faviconManager,
            savedSitesManager,
            pixel,
            syncEngine,
            coroutineRule.testDispatcherProvider,
        )
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() = runTest {
        whenever(savedSitesRepository.getFavorites()).thenReturn(flowOf())

        whenever(savedSitesRepository.getSavedSites(anyString())).thenReturn(
            flowOf(
                SavedSites(
                    listOf(favorite),
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
        testee.onBookmarkEdited(bookmark, "folder1")

        verify(savedSitesRepository).updateBookmark(bookmark, "folder1")
    }

    @Test
    fun whenFavoriteEditedThenRepositoryUpdated() = runTest {
        testee.onFavouriteEdited(favorite)

        verify(savedSitesRepository).updateFavourite(favorite)
    }

    @Test
    fun whenBookmarkDeletedThenConfirmDeleteSavedSiteCommandAndRepositoryIsUpdated() = runTest {
        testee.onSavedSiteDeleted(bookmark)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.value)
        assertTrue(commandCaptor.value is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
        verify(faviconManager).deletePersistedFavicon(bookmark.url)
        verify(savedSitesRepository).delete(bookmark)
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
    fun whenBookmarkFolderSelectedThenIssueOpenBookmarkFolderCommand() {
        testee.onBookmarkFolderSelected(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.OpenBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenFetchBookmarksAndFoldersThenUpdateStateWithCollectedBookmarksAndFolders() = runTest {
        val parentId = "folder1"

        testee.fetchBookmarksAndFolders(parentId = parentId)

        verify(savedSitesRepository).getSavedSites(parentId)

        verify(viewStateObserver, times(2)).onChanged(viewStateCaptor.capture())

        assertEquals(emptyList<BookmarkEntity>(), viewStateCaptor.allValues[0].bookmarks)
        assertEquals(emptyList<BookmarkFolder>(), viewStateCaptor.allValues[0].bookmarkFolders)
        assertEquals(false, viewStateCaptor.allValues[0].enableSearch)

        assertEquals(listOf(favorite), viewStateCaptor.allValues[1].favorites)
        assertEquals(listOf(bookmark), viewStateCaptor.allValues[1].bookmarks)
        assertEquals(listOf(bookmarkFolder, bookmarkFolder, bookmarkFolder), viewStateCaptor.allValues[1].bookmarkFolders)
        assertEquals(true, viewStateCaptor.allValues[1].enableSearch)
    }

    @Test
    fun whenFetchEverythingThenUpdateStateWithData() = runTest {
        whenever(savedSitesRepository.getFavoritesSync()).thenReturn(listOf(favorite))
        whenever(savedSitesRepository.getBookmarksTree()).thenReturn(listOf(bookmark, bookmark, bookmark))
        whenever(savedSitesRepository.getFolderTree(SavedSitesNames.BOOKMARKS_ROOT, null)).thenReturn(listOf(bookmarkFolderItem, bookmarkFolderItem))

        testee.fetchAllBookmarksAndFolders()

        verify(savedSitesRepository).getFavoritesSync()
        verify(savedSitesRepository).getBookmarksTree()
        verify(savedSitesRepository).getFolderTree(SavedSitesNames.BOOKMARKS_ROOT, null)

        verify(viewStateObserver, times(2)).onChanged(viewStateCaptor.capture())

        assertEquals(emptyList<Bookmark>(), viewStateCaptor.allValues[0].bookmarks)
        assertEquals(emptyList<BookmarkFolder>(), viewStateCaptor.allValues[0].bookmarkFolders)
        assertEquals(false, viewStateCaptor.allValues[0].enableSearch)

        assertEquals(listOf(favorite), viewStateCaptor.allValues[1].favorites)
        assertEquals(listOf(bookmark, bookmark, bookmark), viewStateCaptor.allValues[1].bookmarks)
        assertEquals(listOf(bookmarkFolder, bookmarkFolder), viewStateCaptor.allValues[1].bookmarkFolders)
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
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", 0, 0, "timestamp")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        whenever(savedSitesRepository.deleteFolderBranch(any())).thenReturn(folderBranch)

        testee.onDeleteBookmarkFolderRequested(bookmarkFolder)

        verify(savedSitesRepository).deleteFolderBranch(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).bookmarkFolder)
        assertEquals(folderBranch, (commandCaptor.value as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).folderBranch)
    }

    @Test
    fun whenDeleteNonEmptyBookmarkFolderRequestedThenIssueDeleteBookmarkFolderCommand() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", 0, 0, "timestamp")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        whenever(savedSitesRepository.deleteFolderBranch(any())).thenReturn(folderBranch)

        val nonEmptyBookmarkFolder = bookmarkFolder.copy(numBookmarks = 1)
        testee.onDeleteBookmarkFolderRequested(nonEmptyBookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(nonEmptyBookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.DeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenInsertRecentlyDeletedBookmarksAndFoldersThenInsertCachedFolderBranch() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", 0, 0, "timestamp")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        testee.insertDeletedFolderBranch(folderBranch)

        verify(savedSitesRepository).insertFolderBranch(folderBranch)
    }

    @Test
    fun whenOnBookmarkFoldersActivityResultCalledThenOpenSavedSiteCommandSent() {
        val savedSiteUrl = "https://www.example.com"

        testee.onBookmarkFoldersActivityResult(savedSiteUrl)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(savedSiteUrl, (commandCaptor.value as BookmarksViewModel.Command.OpenSavedSite).savedSiteUrl)
    }
}
