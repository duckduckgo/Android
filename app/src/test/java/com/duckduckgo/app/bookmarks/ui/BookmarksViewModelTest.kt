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
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem
import com.duckduckgo.savedsites.api.models.FolderBranch
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSites
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.service.SavedSitesManager
import com.duckduckgo.sync.api.engine.SyncEngine
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
        Bookmark(
            id = "bookmark1",
            title = "title",
            url = "www.example.com",
            parentId = SavedSitesNames.BOOKMARKS_ROOT,
            "timestamp",
            isFavorite = true,
        )
    private val favorite = Favorite(id = "bookmark1", title = "title", url = "www.example.com", position = 0, lastModified = "timestamp")
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
            coroutineRule.testScope,
        )
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() = runTest {
        whenever(savedSitesRepository.getFavorites()).thenReturn(flowOf(listOf(favorite)))

        whenever(savedSitesRepository.getSavedSites(anyString())).thenReturn(
            flowOf(
                SavedSites(
                    listOf(favorite),
                    listOf(bookmark, bookmarkFolder, bookmarkFolder, bookmarkFolder),
                ),
            ),
        )

        testee.fetchBookmarksAndFolders(SavedSitesNames.BOOKMARKS_ROOT)
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
        testee.command.removeObserver(commandObserver)
    }

    @Test
    fun whenBookmarkDeleteUndoThenRepositoryNotUpdated() = runTest {
        testee.onDeleteSavedSiteRequested(bookmark)
        testee.undoDelete(bookmark)

        verify(savedSitesRepository, never()).delete(bookmark)
        verify(faviconManager, never()).deletePersistedFavicon(bookmark.url)
    }

    @Test
    fun whenBookmarkDeleteThenRepositoryUpdated() = runTest {
        testee.onDeleteSavedSiteRequested(bookmark)
        testee.onDeleteSavedSiteSnackbarDismissed(bookmark)

        verify(faviconManager).deletePersistedFavicon(bookmark.url)
        verify(savedSitesRepository).delete(bookmark)
    }

    @Test
    fun whenBookmarkDeleteRequestedThenConfirmCommandSent() = runTest {
        testee.onDeleteSavedSiteRequested(bookmark)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.value)
        assertTrue(commandCaptor.value is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
    }

    @Test
    fun whenFavoriteDeleteUndoThenRepositoryNotUpdated() = runTest {
        testee.onDeleteSavedSiteRequested(favorite)
        testee.undoDelete(favorite)

        verify(savedSitesRepository, never()).delete(favorite)
        verify(faviconManager, never()).deletePersistedFavicon(favorite.url)
    }

    @Test
    fun whenFavoriteDeleteThenRepositoryUpdated() = runTest {
        testee.onDeleteSavedSiteRequested(favorite)
        testee.onDeleteSavedSiteSnackbarDismissed(favorite)

        verify(savedSitesRepository).delete(favorite)
        verify(faviconManager, never()).deletePersistedFavicon(favorite.url)
    }

    @Test
    fun whenFavoriteDeleteRequestedThenConfirmCommandSent() = runTest {
        testee.onDeleteSavedSiteRequested(favorite)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.value)
        assertTrue(commandCaptor.value is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
    }

    @Test
    fun whenBookmarkEditedThenDaoUpdated() = runTest {
        testee.onBookmarkEdited(bookmark, "folder1", true)

        verify(savedSitesRepository).updateBookmark(bookmark, "folder1", true)
    }

    @Test
    fun whenFavoriteEditedThenRepositoryUpdated() = runTest {
        testee.onFavouriteEdited(favorite)

        verify(savedSitesRepository).updateFavourite(favorite)
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

        verify(viewStateObserver, times(3)).onChanged(viewStateCaptor.capture())

        assertEquals(null, viewStateCaptor.allValues[0].bookmarkItems)
        assertEquals(false, viewStateCaptor.allValues[0].enableSearch)

        assertEquals(listOf(favorite), viewStateCaptor.allValues[1].favorites)
        assertEquals(bookmark, (viewStateCaptor.allValues[1].bookmarkItems!![0] as BookmarksAdapter.BookmarkItem).bookmark)
        assertEquals(bookmarkFolder, (viewStateCaptor.allValues[1].bookmarkItems!![1] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder)
        assertEquals(bookmarkFolder, (viewStateCaptor.allValues[1].bookmarkItems!![2] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder)
        assertEquals(bookmarkFolder, (viewStateCaptor.allValues[1].bookmarkItems!![3] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder)
        assertEquals(false, viewStateCaptor.allValues[1].enableSearch)
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

        verify(viewStateObserver, times(3)).onChanged(viewStateCaptor.capture())

        assertEquals(null, viewStateCaptor.allValues[0].bookmarkItems)
        assertEquals(false, viewStateCaptor.allValues[0].enableSearch)

        assertEquals(listOf(favorite), viewStateCaptor.allValues[2].favorites)
        assertEquals(bookmark, (viewStateCaptor.allValues[2].bookmarkItems!![0] as BookmarksAdapter.BookmarkItem).bookmark)
        assertEquals(bookmark, (viewStateCaptor.allValues[2].bookmarkItems!![1] as BookmarksAdapter.BookmarkItem).bookmark)
        assertEquals(bookmark, (viewStateCaptor.allValues[2].bookmarkItems!![2] as BookmarksAdapter.BookmarkItem).bookmark)
        assertEquals(bookmarkFolder, (viewStateCaptor.allValues[2].bookmarkItems!![3] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder)
        assertEquals(bookmarkFolder, (viewStateCaptor.allValues[2].bookmarkItems!![4] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder)
        assertEquals(true, viewStateCaptor.allValues[2].enableSearch)
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
    fun whenDeleteEmptyFolderRequestedThenCommandIssued() = runTest {
        testee.onDeleteBookmarkFolderRequested(bookmarkFolder)
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenDeleteFolderRequestedThenCommandIssued() = runTest {
        val bookmarkFolder = BookmarkFolder(id = "folder1", name = "folder", parentId = SavedSitesNames.BOOKMARKS_ROOT, 1, 1, "timestamp")
        testee.onDeleteBookmarkFolderRequested(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.DeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenDeleteBookmarkFolderUndoThenReposistoryNotUpdated() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", 0, 0, "timestamp")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        whenever(savedSitesRepository.deleteFolderBranch(any())).thenReturn(folderBranch)

        testee.onDeleteBookmarkFolderRequested(bookmarkFolder)
        testee.undoDelete(bookmarkFolder)

        verify(savedSitesRepository, never()).deleteFolderBranch(bookmarkFolder)
    }

    @Test
    fun whenDeleteBookmarkFolderConfirmedThenDeleteFolderAndIssueConfirmDeleteBookmarkFolderCommand() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", 0, 0, "timestamp")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        whenever(savedSitesRepository.deleteFolderBranch(any())).thenReturn(folderBranch)

        testee.onDeleteBookmarkFolderRequested(bookmarkFolder)
        testee.onDeleteBookmarkFolderSnackbarDismissed(bookmarkFolder)

        verify(savedSitesRepository).deleteFolderBranch(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.value as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenDeleteBookmarkFolderRequestedThenIssueDeleteBookmarkFolderCommand() = runTest {
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
    fun whenOnBookmarkFoldersActivityResultCalledThenOpenSavedSiteCommandSent() {
        val savedSiteUrl = "https://www.example.com"

        testee.onBookmarkFoldersActivityResult(savedSiteUrl)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(savedSiteUrl, (commandCaptor.value as BookmarksViewModel.Command.OpenSavedSite).savedSiteUrl)
    }
}
