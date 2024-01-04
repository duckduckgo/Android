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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val appDatabase: AppDatabase,
) : EditSavedSiteListener, AddBookmarkFolderListener, EditBookmarkFolderListener, DeleteBookmarkListener, ViewModel() {

    data class ViewState(
        val enableSearch: Boolean = false,
        val bookmarks: List<Any>? = null,
        val favorites: List<Favorite> = emptyList(),
        val searchQuery: String = "",
    )

    sealed class Command {
        class OpenSavedSite(val savedSiteUrl: String) : Command()
        class ConfirmDeleteSavedSite(val savedSite: SavedSite) : Command()
        class ShowEditSavedSite(val savedSite: SavedSite) : Command()
        class OpenBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class ShowEditBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class DeleteBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        class ConfirmDeleteBookmarkFolder(val bookmarkFolder: BookmarkFolder) : Command()
        data class ImportedSavedSites(val importSavedSitesResult: ImportSavedSitesResult) : Command()
        data class ExportedSavedSites(val exportSavedSitesResult: ExportSavedSitesResult) : Command()
        data object LaunchBookmarkImport : Command()
    }

    companion object {
        private const val MIN_ITEMS_FOR_SEARCH = 5
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    val hiddenIds = MutableStateFlow(HiddenBookmarksIds())
    data class HiddenBookmarksIds(val favorites: List<String> = emptyList(), val bookmarks: List<String> = emptyList())

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
        updateFavorite: Boolean,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            Timber.d("Bookmark: $bookmark from $oldFolderId")
            savedSitesRepository.updateBookmark(bookmark, oldFolderId, updateFavorite)
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
        command.value = ConfirmDeleteSavedSite(savedSite)
        hide(savedSite)
    }

    fun onDeleteSavedSiteSnackbarDismissed(savedSite: SavedSite) {
        delete(savedSite)
    }

    private fun delete(savedSite: SavedSite) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (savedSite is Bookmark) {
                faviconManager.deletePersistedFavicon(savedSite.url)
            }
            savedSitesRepository.delete(savedSite)
        }
    }

    fun undoDelete(savedSite: SavedSite) {
        viewModelScope.launch(dispatcherProvider.io()) {
            hiddenIds.emit(
                hiddenIds.value.copy(
                    favorites = hiddenIds.value.favorites - savedSite.id,
                    bookmarks = hiddenIds.value.bookmarks - savedSite.id,
                ),
            )
        }
    }

    fun launchBookmarkImport() {
        command.value = LaunchBookmarkImport
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
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.getSavedSites(parentId)
                .combine(hiddenIds) { savedSites, hiddenIds ->
                    val filteredBookmarks = savedSites.bookmarks.filter {
                        when (it) {
                            is Bookmark -> it.id !in hiddenIds.bookmarks
                            is BookmarkFolder -> it.id !in hiddenIds.bookmarks
                            else -> false
                        }
                    }
                    savedSites.copy(
                        bookmarks = filteredBookmarks,
                        favorites = savedSites.favorites.filter { it.id !in hiddenIds.favorites },
                    )
                }.collect {
                    withContext(dispatcherProvider.main()) {
                        onSavedSitesItemsChanged(it.favorites, it.bookmarks)
                    }
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
                onSavedSitesItemsChanged(favorites, bookmarks + folders)
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

    fun onDeleteBookmarkFolderRequested(bookmarkFolder: BookmarkFolder) {
        if (bookmarkFolder.numFolders + bookmarkFolder.numBookmarks == 0) {
            hide(bookmarkFolder)
        } else {
            command.value = DeleteBookmarkFolder(bookmarkFolder)
        }
    }

    fun onDeleteFolderAccepted(bookmarkFolder: BookmarkFolder) {
        hide(bookmarkFolder)
    }

    fun undoDelete(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io()) {
            hiddenIds.emit(hiddenIds.value.copy(bookmarks = hiddenIds.value.bookmarks - bookmarkFolder.id))
        }
    }

    fun onDeleteBookmarkFolderSnackbarDismissed(bookmarkFolder: BookmarkFolder) {
        delete(bookmarkFolder)
    }

    private fun delete(bookmarkFolder: BookmarkFolder) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.deleteFolderBranch(bookmarkFolder)
        }
    }

    private fun hide(savedSite: SavedSite) {
        viewModelScope.launch(dispatcherProvider.io()) {
            when (savedSite) {
                is Bookmark -> {
                    hiddenIds.emit(
                        hiddenIds.value.copy(
                            bookmarks = hiddenIds.value.bookmarks + savedSite.id,
                            favorites = hiddenIds.value.favorites + savedSite.id,
                        ),
                    )
                }
                is Favorite -> {
                    hiddenIds.emit(hiddenIds.value.copy(favorites = hiddenIds.value.favorites + savedSite.id))
                }
            }
        }
    }

    fun hide(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io()) {
            hiddenIds.emit(hiddenIds.value.copy(bookmarks = hiddenIds.value.bookmarks + bookmarkFolder.id))
        }
        command.postValue(ConfirmDeleteBookmarkFolder(bookmarkFolder))
    }

    private fun onSavedSitesItemsChanged(
        favorites: List<Favorite>,
        bookmarks: List<Any>,
    ) {
        viewState.value = viewState.value?.copy(
            favorites = favorites,
            bookmarks = bookmarks,
            enableSearch = bookmarks.size >= MIN_ITEMS_FOR_SEARCH,
        )
    }

    fun onBookmarkFoldersActivityResult(savedSiteUrl: String) {
        command.value = OpenSavedSite(savedSiteUrl)
    }

    fun updateBookmarks(bookmarksAndFolders: List<Any>) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val operations = mutableListOf<() -> Unit>()

            for (item in bookmarksAndFolders) {
                when (item) {
                    is Bookmark -> {
                        operations.add { savedSitesRepository.delete(item) }
                        operations.add { savedSitesRepository.insert(item) }
                        if (item.isFavorite) {
                            operations.add { savedSitesRepository.insertFavorite(item.id, item.url, item.title, item.lastModified) }
                        }
                    }
                    is BookmarkFolder -> {
                        operations.add { savedSitesRepository.updateFolderRelation(item) }
                    }
                }
            }

            appDatabase.runInTransaction {
                operations.forEach { it.invoke() }
            }
        }
    }

    fun addFavorite(bookmark: Bookmark) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.insertFavorite(bookmark.id, bookmark.url, bookmark.title, bookmark.lastModified)
        }
    }

    fun removeFavorite(bookmark: Bookmark) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.delete(
                Favorite(
                    id = bookmark.id,
                    title = bookmark.title,
                    url = bookmark.url,
                    lastModified = bookmark.lastModified,
                    position = 0,
                ),
            )
        }
    }
}
