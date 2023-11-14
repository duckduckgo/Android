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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel.Command.*
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment.DeleteBookmarkListener
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.AddBookmarkFolderDialogFragment.AddBookmarkFolderListener
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.EditBookmarkFolderDialogFragment.EditBookmarkFolderListener
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.FolderBranch
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.service.ExportSavedSitesResult
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesManager
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class BookmarksViewModel @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val faviconManager: FaviconManager,
    private val savedSitesManager: SavedSitesManager,
    private val pixel: Pixel,
    private val syncEngine: SyncEngine,
    private val dispatcherProvider: DispatcherProvider,
) : EditSavedSiteListener, AddBookmarkFolderListener, EditBookmarkFolderListener, DeleteBookmarkListener, ViewModel() {

    data class ViewState(
        val enableSearch: Boolean = false,
        val bookmarks: List<Bookmark> = emptyList(),
        val favorites: List<Favorite> = emptyList(),
        val bookmarkFolders: List<BookmarkFolder> = emptyList(),
    )

    sealed class Command {
        class OpenSavedSite(val savedSiteUrl: String) : Command()
        class ConfirmDeleteSavedSite(val savedSite: SavedSite) : Command()
        class ShowEditSavedSite(val savedSite: SavedSite) : Command()
        class OpenBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class ShowEditBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class DeleteBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class ConfirmDeleteBookmarkFolder(
            val bookmarkFolder: BookmarkFolder,
            val folderBranch: FolderBranch,
        ) : Command()

        data class ImportedSavedSites(val importSavedSitesResult: ImportSavedSitesResult) : Command()
        data class ExportedSavedSites(val exportSavedSitesResult: ExportSavedSitesResult) : Command()
    }

    companion object {
        private const val MIN_ITEMS_FOR_SEARCH = 1
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        viewState.value = ViewState()
        viewModelScope.launch(dispatcherProvider.io()) {
            syncEngine.triggerSync(FEATURE_READ)
        }
    }

    override fun onFavouriteEdited(favorite: Favorite) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.updateFavourite(favorite)
        }
    }

    override fun onBookmarkEdited(
        bookmark: Bookmark,
        oldFolderId: String,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            Timber.d("Bookmark: $bookmark from $oldFolderId")
            savedSitesRepository.updateBookmark(bookmark, oldFolderId)
        }
    }

    override fun onSavedSiteDeleted(savedSite: SavedSite) {
        onDeleteSavedSiteRequested(savedSite)
    }

    fun onSelected(savedSite: SavedSite) {
        if (savedSite is Favorite) {
            pixel.fire(AppPixelName.FAVORITE_BOOKMARKS_ITEM_PRESSED)
        }
        command.value = OpenSavedSite(savedSite.url)
    }

    fun onEditSavedSiteRequested(savedSite: SavedSite) {
        command.value = ShowEditSavedSite(savedSite)
    }

    fun onDeleteSavedSiteRequested(savedSite: SavedSite) {
        if (savedSite is Favorite) {
            command.value = ConfirmDeleteSavedSite(savedSite)
        } else {
            viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
                val favourite = savedSitesRepository.getFavorite(savedSite.url)
                withContext(dispatcherProvider.main()) {
                    if (favourite != null) {
                        // bookmark was also a favourite, so we return that one instead
                        command.value = ConfirmDeleteSavedSite(favourite)
                    } else {
                        command.value = ConfirmDeleteSavedSite(savedSite)
                    }
                }
            }
        }
        delete(savedSite)
    }

    private fun delete(savedSite: SavedSite) {
        viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
            faviconManager.deletePersistedFavicon(savedSite.url)
            savedSitesRepository.delete(savedSite)
        }
    }

    fun insert(savedSite: SavedSite) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.insert(savedSite)
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

    fun onBookmarkFolderSelected(bookmarkFolder: BookmarkFolder) {
        command.value = OpenBookmarkFolder(bookmarkFolder)
    }

    fun fetchBookmarksAndFolders(parentId: String) {
        viewModelScope.launch {
            savedSitesRepository.getSavedSites(parentId).collect {
                onSavedSitesItemsChanged(it.favorites, it.bookmarks, it.folders)
            }
        }
    }

    fun fetchAllBookmarksAndFolders() {
        viewModelScope.launch(dispatcherProvider.io()) {
            val favorites = savedSitesRepository.getFavoritesSync()
            val folders = savedSitesRepository.getFolderTree(SavedSitesNames.BOOKMARKS_ROOT, null)
                .map { it.bookmarkFolder }
                .filter { it.id != SavedSitesNames.BOOKMARKS_ROOT }
            val bookmarks = savedSitesRepository.getBookmarksTree()
            withContext(dispatcherProvider.main()) {
                onSavedSitesItemsChanged(favorites, bookmarks, folders)
            }
        }
    }

    override fun onBookmarkFolderAdded(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.insert(bookmarkFolder)
        }
    }

    fun onEditBookmarkFolderRequested(bookmarkFolder: BookmarkFolder) {
        command.value = ShowEditBookmarkFolder(bookmarkFolder)
    }

    override fun onBookmarkFolderUpdated(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.update(bookmarkFolder)
        }
    }

    override fun onDeleteBookmarkFolderRequestedFromEdit(bookmarkFolder: BookmarkFolder) {
        onDeleteBookmarkFolderRequested(bookmarkFolder)
    }

    fun onBookmarkFolderDeleted(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
            val folderBranch = savedSitesRepository.deleteFolderBranch(bookmarkFolder)
            command.postValue(ConfirmDeleteBookmarkFolder(bookmarkFolder, folderBranch))
        }
    }

    fun onDeleteBookmarkFolderRequested(bookmarkFolder: BookmarkFolder) {
        if (bookmarkFolder.numFolders + bookmarkFolder.numBookmarks == 0) {
            onBookmarkFolderDeleted(bookmarkFolder)
        } else {
            command.value = DeleteBookmarkFolder(bookmarkFolder)
        }
    }

    private fun onSavedSitesItemsChanged(
        favorites: List<Favorite>,
        bookmarks: List<Bookmark>,
        bookmarkFolders: List<BookmarkFolder>,
    ) {
        viewState.value = viewState.value?.copy(
            favorites = favorites,
            bookmarks = bookmarks,
            bookmarkFolders = bookmarkFolders,
            enableSearch = bookmarks.size + bookmarkFolders.size >= MIN_ITEMS_FOR_SEARCH,
        )
    }

    fun insertDeletedFolderBranch(folderBranch: FolderBranch) {
        viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
            savedSitesRepository.insertFolderBranch(folderBranch)
        }
    }

    fun onBookmarkFoldersActivityResult(savedSiteUrl: String) {
        command.value = OpenSavedSite(savedSiteUrl)
    }
}
