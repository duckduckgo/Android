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

package com.duckduckgo.savedsites.impl.bookmarks

import android.annotation.SuppressLint
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.ImportFromGoogle
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
import com.duckduckgo.savedsites.api.models.SavedSitesNames.BOOKMARKS_ROOT
import com.duckduckgo.savedsites.api.service.SavedSitesManager
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarkItem
import com.duckduckgo.savedsites.impl.store.BookmarksDataStore
import com.duckduckgo.savedsites.impl.store.SortingMode.MANUAL
import com.duckduckgo.savedsites.impl.store.SortingMode.NAME
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.favicons.FaviconsFetchingPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*

@SuppressLint("DenyListedApi")
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

    private val importFromGoogle: ImportFromGoogle = mock()

    private val commandCaptor = argumentCaptor<BookmarksViewModel.Command>()
    private val viewStateCaptor = argumentCaptor<BookmarksViewModel.ViewState>()

    private val commandObserver: Observer<BookmarksViewModel.Command> = mock()

    private val viewStateObserver: Observer<BookmarksViewModel.ViewState> = mock()
    private val savedSitesRepository: SavedSitesRepository = mock()
    private val faviconManager: FaviconManager = mock()
    private val savedSitesManager: SavedSitesManager = mock()
    private val syncEngine: SyncEngine = mock()
    private val pixel: Pixel = mock()
    private val faviconsFetchingPrompt: FaviconsFetchingPrompt = mock()
    private val bookmarksDataStore: BookmarksDataStore = mock()

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

    private val favoritesFlow = MutableStateFlow(listOf(favorite))
    private val savedSitesFlow = MutableStateFlow(
        SavedSites(
            listOf(favorite),
            listOf(bookmark, bookmarkFolder, bookmarkFolder, bookmarkFolder),
        ),
    )

    private val testee: BookmarksViewModel by lazy {
        val model = BookmarksViewModel(
            savedSitesRepository,
            faviconManager,
            savedSitesManager,
            pixel,
            syncEngine,
            faviconsFetchingPrompt,
            bookmarksDataStore,
            coroutineRule.testDispatcherProvider,
            importFromGoogle,
            coroutineRule.testScope,
        )
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() = runTest {
        whenever(savedSitesRepository.getFavorites()).thenReturn(favoritesFlow)

        whenever(savedSitesRepository.getSavedSites(anyString())).thenReturn(savedSitesFlow)

        whenever(bookmarksDataStore.getSortingMode()).thenReturn(NAME)
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
        assertNotNull(commandCaptor.lastValue)
        assertTrue(commandCaptor.lastValue is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
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
        assertNotNull(commandCaptor.lastValue)
        assertTrue(commandCaptor.lastValue is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
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
    fun whenSavedSiteSelectedThenOpenCommandAndPixelFired() {
        testee.onSelected(bookmark)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.lastValue)
        assertTrue(commandCaptor.lastValue is BookmarksViewModel.Command.OpenSavedSite)
        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_LAUNCHED)
        verify(pixel, never()).fire(SavedSitesPixelName.FAVORITE_BOOKMARKS_ITEM_PRESSED)
    }

    @Test
    fun whenFavoriteSelectedThenPixelSent() {
        testee.onSelected(favorite)

        verify(pixel).fire(SavedSitesPixelName.FAVORITE_BOOKMARKS_ITEM_PRESSED)
        verify(pixel).fire(SavedSitesPixelName.FAVORITE_BOOKMARKS_ITEM_PRESSED)
        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_LAUNCHED)
    }

    @Test
    fun whenOnEditSavedSiteRequestedThenShowEditSavedSiteCommandSentAndPixelFired() {
        testee.onEditSavedSiteRequested(bookmark)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.lastValue)
        assertTrue(commandCaptor.lastValue is BookmarksViewModel.Command.ShowEditSavedSite)
        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_MENU_EDIT_BOOKMARK_CLICKED)
    }

    @Test
    fun whenDeleteRequestedThenConfirmCommand() {
        testee.onDeleteSavedSiteRequested(bookmark)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertNotNull(commandCaptor.lastValue)
        assertTrue(commandCaptor.lastValue is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
    }

    @Test
    fun whenBookmarkFolderSelectedThenIssueOpenBookmarkFolderCommand() {
        testee.onBookmarkFolderSelected(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.lastValue as BookmarksViewModel.Command.OpenBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenFetchBookmarksAndFoldersThenUpdateStateWithCollectedBookmarksAndFolders() = runTest {
        val parentId = "folder1"

        testee.fetchBookmarksAndFolders(parentId = parentId)

        verify(savedSitesRepository).getSavedSites(parentId)

        verify(viewStateObserver, times(5)).onChanged(viewStateCaptor.capture())

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

        verify(viewStateObserver, times(5)).onChanged(viewStateCaptor.capture())

        assertEquals(null, viewStateCaptor.firstValue.bookmarkItems)
        assertEquals(false, viewStateCaptor.firstValue.enableSearch)

        with(viewStateCaptor.lastValue) {
            assertEquals(listOf(favorite), this.favorites)
            assertEquals(bookmark, (this.bookmarkItems!![0] as BookmarksAdapter.BookmarkItem).bookmark)
            assertEquals(bookmark, (this.bookmarkItems!![1] as BookmarksAdapter.BookmarkItem).bookmark)
            assertEquals(bookmark, (this.bookmarkItems!![2] as BookmarksAdapter.BookmarkItem).bookmark)
            assertEquals(bookmarkFolder, (this.bookmarkItems!![3] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder)
            assertEquals(bookmarkFolder, (this.bookmarkItems!![4] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder)
            assertEquals(true, this.enableSearch)
        }
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
        assertEquals(bookmarkFolder, (commandCaptor.lastValue as BookmarksViewModel.Command.ShowEditBookmarkFolder).bookmarkFolder)
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
        assertEquals(bookmarkFolder, (commandCaptor.lastValue as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenDeleteFolderRequestedThenCommandIssued() = runTest {
        val bookmarkFolder = BookmarkFolder(id = "folder1", name = "folder", parentId = SavedSitesNames.BOOKMARKS_ROOT, 1, 1, "timestamp")
        testee.onDeleteBookmarkFolderRequested(bookmarkFolder)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(bookmarkFolder, (commandCaptor.lastValue as BookmarksViewModel.Command.DeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenDeleteBookmarkFolderUndoThenRepositoryNotUpdated() = runTest {
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
        assertEquals(bookmarkFolder, (commandCaptor.lastValue as BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder).bookmarkFolder)
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
        assertEquals(nonEmptyBookmarkFolder, (commandCaptor.lastValue as BookmarksViewModel.Command.DeleteBookmarkFolder).bookmarkFolder)
    }

    @Test
    fun whenOnBookmarkFoldersActivityResultCalledThenOpenSavedSiteCommandSent() {
        val savedSiteUrl = "https://www.example.com"

        testee.onBookmarkFoldersActivityResult(savedSiteUrl)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(savedSiteUrl, (commandCaptor.lastValue as BookmarksViewModel.Command.OpenSavedSite).savedSiteUrl)
    }

    @Test
    fun whenUpdateBookmarksCalledThenUpdateFolderRelation() {
        val parentId = "folderId"
        val bookmarksAndFolders = listOf("bookmark1", "folder1")

        testee.updateBookmarks(bookmarksAndFolders, parentId)

        verify(savedSitesRepository).updateFolderRelation(parentId, bookmarksAndFolders)
    }

    @Test
    fun whenAddFavoriteCalledThenInsertFavoriteAndPixelFired() {
        testee.addFavorite(bookmark)

        verify(savedSitesRepository).insertFavorite(bookmark.id, bookmark.url, bookmark.title)
        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_MENU_ADD_FAVORITE_CLICKED)
    }

    @Test
    fun whenRemoveFavoriteCalledThenDeleteFavoriteAndPixelFired() {
        testee.removeFavorite(bookmark)

        verify(savedSitesRepository).delete(
            Favorite(
                id = bookmark.id,
                title = bookmark.title,
                url = bookmark.url,
                lastModified = bookmark.lastModified,
                position = 0,
            ),
        )
        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_MENU_REMOVE_FAVORITE_CLICKED)
    }

    @Test
    fun whenOnFavoriteAddedThePixelSent() {
        testee.onFavoriteAdded()

        verify(pixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED)
    }

    @Test
    fun whenOnFavoriteRemovedThePixelSent() {
        testee.onFavoriteRemoved()

        verify(pixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED)
    }

    @Test
    fun whenOnSavedSiteDeletedThenConfirmDeleteSavedSiteCommandSentAndPixelFired() {
        testee.onSavedSiteDeleted(bookmark)

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is BookmarksViewModel.Command.ConfirmDeleteSavedSite)
        verify(pixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CONFIRMED)
    }

    @Test
    fun whenOnSavedSiteDeleteCancelledThenPixelFired() {
        testee.onSavedSiteDeleteCancelled()

        verify(pixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CANCELLED)
    }

    @Test
    fun whenOnSavedSiteDeleteRequestedThenPixelFired() {
        testee.onSavedSiteDeleteRequested()

        verify(pixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CLICKED)
    }

    @Test
    fun whenManualSortingModeSelectedThenDataStored() = runTest {
        testee.onSortingModeSelected(MANUAL)
        verify(bookmarksDataStore).setSortingMode(MANUAL)
    }

    @Test
    fun whenNameSortingModeSelectedThenDataStored() = runTest {
        testee.onSortingModeSelected(NAME)
        verify(bookmarksDataStore).setSortingMode(NAME)
    }

    @Test
    fun whenSortingByNameSelectedThenListIsSorted() = runTest {
        val folderNews = BookmarkFolder(id = "folderA", name = "News", parentId = SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val folderSports = BookmarkFolder(id = "folderB", name = "Sports", parentId = SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val bookmarkAs = Bookmark(id = "bookmarkA", title = "As", url = "www.example.com", parentId = SavedSitesNames.BOOKMARKS_ROOT, "timestamp")
        val bookmarkCnn = Bookmark(id = "bookmarCnn", title = "Cnn", url = "www.example.com", parentId = SavedSitesNames.BOOKMARKS_ROOT, "timestamp")
        val bookmarkReddit = Bookmark(
            id = "bookmarReddit",
            title = "Reddit",
            url = "www.example.com",
            parentId = SavedSitesNames.BOOKMARKS_ROOT,
            "timestamp",
        )
        val bookmarkTheGuardian = Bookmark(
            id = "bookmarT",
            title = "The Guardian",
            url = "www.example.com",
            parentId = SavedSitesNames.BOOKMARKS_ROOT,
            "timestamp",
        )

        savedSitesFlow.value = SavedSites(
            emptyList(),
            listOf(bookmarkAs, bookmarkReddit, bookmarkCnn, bookmarkTheGuardian, folderSports, folderNews),
        )
        testee.fetchBookmarksAndFolders(BOOKMARKS_ROOT)

        testee.onSortingModeSelected(NAME)

        val sortedElements = testee.itemsToDisplay.value
        assertEquals((sortedElements[0] as BookmarkItem).bookmark, bookmarkAs)
        assertEquals((sortedElements[1] as BookmarkItem).bookmark, bookmarkCnn)
        assertEquals((sortedElements[2] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder, folderNews)
        assertEquals((sortedElements[3] as BookmarkItem).bookmark, bookmarkReddit)
        assertEquals((sortedElements[4] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder, folderSports)
        assertEquals((sortedElements[5] as BookmarkItem).bookmark, bookmarkTheGuardian)
    }

    @Test
    fun whenSortingManualSelectedThenListIsSorted() = runTest {
        val folderNews = BookmarkFolder(id = "folderA", name = "News", parentId = SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val folderSports = BookmarkFolder(id = "folderB", name = "Sports", parentId = SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val bookmarkAs = Bookmark(id = "bookmarkA", title = "As", url = "www.example.com", parentId = SavedSitesNames.BOOKMARKS_ROOT, "timestamp")
        val bookmarkCnn = Bookmark(id = "bookmarCnn", title = "Cnn", url = "www.example.com", parentId = SavedSitesNames.BOOKMARKS_ROOT, "timestamp")
        val bookmarkReddit = Bookmark(
            id = "bookmarReddit",
            title = "Reddit",
            url = "www.example.com",
            parentId = SavedSitesNames.BOOKMARKS_ROOT,
            "timestamp",
        )
        val bookmarkTheGuardian = Bookmark(
            id = "bookmarT",
            title = "The Guardian",
            url = "www.example.com",
            parentId = SavedSitesNames.BOOKMARKS_ROOT,
            "timestamp",
        )

        savedSitesFlow.value = SavedSites(
            emptyList(),
            listOf(bookmarkAs, bookmarkReddit, bookmarkCnn, bookmarkTheGuardian, folderSports, folderNews),
        )
        testee.fetchBookmarksAndFolders(BOOKMARKS_ROOT)

        testee.onSortingModeSelected(MANUAL)

        val sortedElements = testee.itemsToDisplay.value
        assertEquals((sortedElements[0] as BookmarkItem).bookmark, bookmarkAs)
        assertEquals((sortedElements[1] as BookmarkItem).bookmark, bookmarkReddit)
        assertEquals((sortedElements[2] as BookmarkItem).bookmark, bookmarkCnn)
        assertEquals((sortedElements[3] as BookmarkItem).bookmark, bookmarkTheGuardian)
        assertEquals((sortedElements[4] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder, folderSports)
        assertEquals((sortedElements[5] as BookmarksAdapter.BookmarkFolderItem).bookmarkFolder, folderNews)
    }

    @Test
    fun whenBrowserMenuPressedAndBookmarksEmptyThenCommandSent() {
        whenever(savedSitesRepository.getSavedSites(anyString())).thenReturn(
            flowOf(SavedSites(emptyList(), emptyList())),
        )

        testee.fetchBookmarksAndFolders(BOOKMARKS_ROOT)

        testee.onBrowserMenuPressed()

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(NAME, (commandCaptor.lastValue as BookmarksViewModel.Command.ShowBrowserMenu).sortingMode)
        assertEquals(true, (commandCaptor.lastValue as BookmarksViewModel.Command.ShowBrowserMenu).buttonsDisabled)
    }

    @Test
    fun whenBrowserMenuPressedAndBookmarksNotEmptyThenCommandSent() {
        testee.fetchBookmarksAndFolders(BOOKMARKS_ROOT)

        testee.onBrowserMenuPressed()

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(NAME, (commandCaptor.lastValue as BookmarksViewModel.Command.ShowBrowserMenu).sortingMode)
        assertEquals(false, (commandCaptor.lastValue as BookmarksViewModel.Command.ShowBrowserMenu).buttonsDisabled)
    }

    @Test
    fun whenBrowserMenuPressedAndManualSortingModeThenCommandSent() {
        whenever(bookmarksDataStore.getSortingMode()).thenReturn(MANUAL)
        testee.fetchBookmarksAndFolders(BOOKMARKS_ROOT)

        testee.onBrowserMenuPressed()

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(MANUAL, (commandCaptor.lastValue as BookmarksViewModel.Command.ShowBrowserMenu).sortingMode)
        assertEquals(false, (commandCaptor.lastValue as BookmarksViewModel.Command.ShowBrowserMenu).buttonsDisabled)
    }

    @Test
    fun whenImportBookmarksClickedThenPixelSent() {
        testee.onImportBookmarksClicked()

        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_MENU_IMPORT_CLICKED)
    }

    @Test
    fun whenImportBookmarksClickedAndFeatureEnabledThenShowDialog() = runTest {
        whenever(importFromGoogle.getBookmarksImportLaunchIntent()).thenReturn(Intent())
        testee.onImportBookmarksClicked()

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(BookmarksViewModel.Command.ShowBookmarkImportDialog, commandCaptor.lastValue)
    }

    @Test
    fun whenImportBookmarksClickedAndFeatureDisabledThenLaunchFileImport() = runTest {
        whenever(importFromGoogle.getBookmarksImportLaunchIntent()).thenReturn(null)
        testee.onImportBookmarksClicked()

        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(BookmarksViewModel.Command.LaunchBookmarkImportFile, commandCaptor.lastValue)
    }

    @Test
    fun whenExportBookmarksClickedThenPixelAndCommandSent() {
        testee.onExportBookmarksClicked()

        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_MENU_EXPORT_CLICKED)
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(BookmarksViewModel.Command.LaunchBookmarkExport, commandCaptor.lastValue)
    }

    @Test
    fun whenAddFolderClickedThenPixelAndCommandSent() {
        testee.onAddFolderClicked()

        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_MENU_ADD_FOLDER_CLICKED)
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(BookmarksViewModel.Command.LaunchAddFolder, commandCaptor.lastValue)
    }
}
