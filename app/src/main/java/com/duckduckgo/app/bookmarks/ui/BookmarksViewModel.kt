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

import android.net.Uri
import androidx.lifecycle.*
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFoldersDao
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.*
import com.duckduckgo.app.bookmarks.service.ExportSavedSitesResult
import com.duckduckgo.app.bookmarks.service.ImportSavedSitesResult
import com.duckduckgo.app.bookmarks.service.SavedSitesManager
import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel.Command.*
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.AddBookmarkFolderDialogFragment.AddBookmarkFolderListener
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.DeleteBookmarkFolderConfirmationFragment.DeleteBookmarkFolderListener
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.EditBookmarkFolderDialogFragment.EditBookmarkFolderListener
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class BookmarksViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val bookmarkFoldersRepository: BookmarkFoldersRepository,
    private val bookmarksDao: BookmarksDao,
    private val bookmarkFoldersDao: BookmarkFoldersDao,
    private val faviconManager: FaviconManager,
    private val savedSitesManager: SavedSitesManager,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider
) : EditSavedSiteListener, AddBookmarkFolderListener, EditBookmarkFolderListener, DeleteBookmarkFolderListener, ViewModel() {

    data class ViewState(
        val enableSearch: Boolean = false,
        val bookmarks: List<Bookmark> = emptyList(),
        val favorites: List<Favorite> = emptyList(),
        val bookmarkFolders: List<BookmarkFolder> = emptyList(),
        val branchToDelete: BookmarkFolderBranch = BookmarkFolderBranch(emptyList(), emptyList())
    )

    sealed class Command {
        class OpenSavedSite(val savedSite: SavedSite) : Command()
        class ConfirmDeleteSavedSite(val savedSite: SavedSite) : Command()
        class ShowEditSavedSite(val savedSite: SavedSite) : Command()
        class OpenBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class ShowEditBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class DeleteBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class ConfirmDeleteBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        data class ImportedSavedSites(val importSavedSitesResult: ImportSavedSitesResult) : Command()
        data class ExportedSavedSites(val exportSavedSitesResult: ExportSavedSitesResult) : Command()
    }

    companion object {
        private const val MIN_ITEMS_FOR_SEARCH = 1
        private const val NO_PARENT_ID = -1L
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        viewState.value = ViewState()
        viewModelScope.launch {
            favoritesRepository.favorites().collect {
                onFavoritesChanged(it)
            }
        }
    }

    override fun onSavedSiteEdited(savedSite: SavedSite) {
        when (savedSite) {
            is Bookmark -> {
                viewModelScope.launch(dispatcherProvider.io()) {
                    editBookmark(savedSite)
                }
            }
            is Favorite -> {
                viewModelScope.launch(dispatcherProvider.io()) {
                    editFavorite(savedSite)
                }
            }
        }
    }

    fun onSelected(savedSite: SavedSite) {
        if (savedSite is Favorite) {
            pixel.fire(AppPixelName.FAVORITE_BOOKMARKS_ITEM_PRESSED)
        }
        command.value = OpenSavedSite(savedSite)
    }

    fun onEditSavedSiteRequested(savedSite: SavedSite) {
        command.value = ShowEditSavedSite(savedSite)
    }

    fun onDeleteSavedSiteRequested(savedSite: SavedSite) {
        delete(savedSite)
        command.value = ConfirmDeleteSavedSite(savedSite)
    }

    private fun delete(savedSite: SavedSite) {
        when (savedSite) {
            is Bookmark -> {
                viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
                    faviconManager.deletePersistedFavicon(savedSite.url)
                    bookmarksDao.delete(BookmarkEntity(savedSite.id, savedSite.title, savedSite.url, savedSite.parentId))
                }
            }
            is Favorite -> {
                viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
                    favoritesRepository.delete(savedSite)
                }
            }
        }
    }

    fun insert(savedSite: SavedSite) {
        when (savedSite) {
            is Bookmark -> {
                viewModelScope.launch(dispatcherProvider.io()) {
                    bookmarksDao.insert(BookmarkEntity(title = savedSite.title, url = savedSite.url, parentId = savedSite.parentId))
                }
            }
            is Favorite -> {
                viewModelScope.launch(dispatcherProvider.io()) {
                    favoritesRepository.insert(savedSite)
                }
            }
        }
    }

    fun importBookmarks(uri: Uri) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val result = savedSitesManager.import(uri)
            withContext(dispatcherProvider.main()) {
                command.value = ImportedSavedSites(result)
            }
        }
    }

    fun exportSavedSites(selectedFile: Uri) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val result = savedSitesManager.export(selectedFile)
            withContext(dispatcherProvider.main()) {
                command.value = ExportedSavedSites(result)
            }
        }
    }

    private suspend fun editBookmark(bookmark: Bookmark) {
        withContext(dispatcherProvider.io()) {
            bookmarksDao.update(BookmarkEntity(bookmark.id, bookmark.title, bookmark.url, bookmark.parentId))
        }
    }

    private suspend fun editFavorite(favorite: Favorite) {
        withContext(dispatcherProvider.io()) {
            favoritesRepository.update(favorite)
        }
    }

    private fun onFavoritesChanged(favorites: List<Favorite>) {
        viewState.value = viewState.value?.copy(favorites = favorites)
    }

    fun onBookmarkFolderSelected(bookmarkFolder: BookmarkFolder) {
        command.value = OpenBookmarkFolder(bookmarkFolder)
    }

    fun fetchBookmarksAndFolders(parentId: Long = NO_PARENT_ID) {
        if (parentId == NO_PARENT_ID) {
            fetchAllItems()
        } else {
            fetchItemsByParentId(parentId)
        }
    }

    private fun fetchAllItems() {
        viewModelScope.launch {
            bookmarksDao.getBookmarks().combine(bookmarkFoldersDao.getBookmarkFolders()) {
                bookmarks: List<BookmarkEntity>, folders: List<BookmarkFolder> ->

                val mappedBookmarks = bookmarks.map {
                    Bookmark(it.id, it.title ?: "", it.url, it.parentId)
                }
                onBookmarkItemsChanged(mappedBookmarks, folders)
            }.collect()
        }
    }

    private fun fetchItemsByParentId(parentId: Long) {
        viewModelScope.launch {
            bookmarksDao.getBookmarksByParentId(parentId).combine(bookmarkFoldersDao.getBookmarkFoldersByParentId(parentId)) {
                bookmarks: List<BookmarkEntity>, folders: List<BookmarkFolder> ->

                val mappedBookmarks = bookmarks.map {
                    Bookmark(it.id, it.title ?: "", it.url, it.parentId)
                }
                onBookmarkItemsChanged(mappedBookmarks, folders)
            }.collect()
        }
    }

    override fun onBookmarkFolderAdded(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io()) {
            bookmarkFoldersRepository.insert(bookmarkFolder)
        }
    }

    fun onEditBookmarkFolderRequested(bookmarkFolder: BookmarkFolder) {
        command.value = ShowEditBookmarkFolder(bookmarkFolder)
    }

    override fun onBookmarkFolderUpdated(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io()) {
            bookmarkFoldersRepository.update(bookmarkFolder)
        }
    }

    override fun onBookmarkFolderDeleted(bookmarkFolder: BookmarkFolder) {
        deleteFolder(bookmarkFolder)
        command.value = ConfirmDeleteBookmarkFolder(bookmarkFolder)
    }

    fun onDeleteBookmarkFolderRequested(bookmarkFolder: BookmarkFolder) {
        if (bookmarkFolder.numFolders + bookmarkFolder.numBookmarks == 0) {
            onBookmarkFolderDeleted(bookmarkFolder)
        } else {
            command.value = DeleteBookmarkFolder(bookmarkFolder)
        }
    }

    private fun deleteFolder(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
            val branchToDelete = bookmarkFoldersRepository.getBookmarkFolderBranch(bookmarkFolder)

            viewState.postValue(
                viewState.value?.copy(
                    branchToDelete = branchToDelete
                )
            )
            bookmarkFoldersRepository.deleteFolderBranch(branchToDelete)
        }
    }

    private fun onBookmarkItemsChanged(bookmarks: List<Bookmark>, bookmarkFolders: List<BookmarkFolder>) {
        viewState.value = viewState.value?.copy(
            bookmarks = bookmarks,
            bookmarkFolders = bookmarkFolders,
            enableSearch = bookmarks.size + bookmarkFolders.size >= MIN_ITEMS_FOR_SEARCH
        )
    }

    fun insertRecentlyDeletedBookmarksAndFolders() {
        viewState.value?.let { viewState ->
            viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
                bookmarkFoldersRepository.insertFolderBranch(viewState.branchToDelete)
            }
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class BookmarksViewModelFactory @Inject constructor(
    private val favoritesRepository: Provider<FavoritesRepository>,
    private val bookmarkFoldersRepository: Provider<BookmarkFoldersRepository>,
    private val bookmarksDao: Provider<BookmarksDao>,
    private val bookmarkFoldersDao: Provider<BookmarkFoldersDao>,
    private val faviconManager: Provider<FaviconManager>,
    private val savedSitesManager: Provider<SavedSitesManager>,
    private val pixel: Provider<Pixel>,
    private val dispatcherProvider: Provider<DispatcherProvider>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BookmarksViewModel::class.java) -> (
                    BookmarksViewModel(
                        favoritesRepository.get(),
                        bookmarkFoldersRepository.get(),
                        bookmarksDao.get(),
                        bookmarkFoldersDao.get(),
                        faviconManager.get(),
                        savedSitesManager.get(),
                        pixel.get(),
                        dispatcherProvider.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}
