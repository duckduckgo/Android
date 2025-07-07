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

import android.net.Uri
import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
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
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.savedsites.impl.SavedSitesPixelParameters
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarkFolderItem
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarkItem
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarksItemTypes
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ConfirmDeleteSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.DeleteBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ExportedSavedSites
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ImportedSavedSites
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.LaunchAddFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.LaunchBookmarkExport
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.LaunchBookmarkImport
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.OpenBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.OpenSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowBrowserMenu
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowEditBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowEditSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowFaviconsPrompt
import com.duckduckgo.savedsites.impl.dialogs.AddBookmarkFolderDialogFragment.AddBookmarkFolderListener
import com.duckduckgo.savedsites.impl.dialogs.EditBookmarkFolderDialogFragment.EditBookmarkFolderListener
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.DeleteBookmarkListener
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.savedsites.impl.store.BookmarksDataStore
import com.duckduckgo.savedsites.impl.store.SortingMode
import com.duckduckgo.savedsites.impl.store.SortingMode.MANUAL
import com.duckduckgo.savedsites.impl.store.SortingMode.NAME
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.api.favicons.FaviconsFetchingPrompt
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class BookmarksViewModel @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val faviconManager: FaviconManager,
    private val savedSitesManager: SavedSitesManager,
    private val pixel: Pixel,
    private val syncEngine: SyncEngine,
    private val faviconsFetchingPrompt: FaviconsFetchingPrompt,
    private val bookmarksDataStore: BookmarksDataStore,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : EditSavedSiteListener, AddBookmarkFolderListener, EditBookmarkFolderListener, DeleteBookmarkListener, ViewModel() {

    data class ViewState(
        val enableSearch: Boolean = false,
        val bookmarkItems: List<BookmarksItemTypes>? = null,
        val favorites: List<Favorite> = emptyList(),
        val searchQuery: String = "",
        val canShowPromo: Boolean = false,
        val sortingMode: SortingMode = SortingMode.MANUAL,
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
        data object LaunchBookmarkExport : Command()
        data object LaunchAddFolder : Command()
        data object ShowFaviconsPrompt : Command()
        data object LaunchSyncSettings : Command()
        data object ReevalutePromotions : Command()
        data class ShowBrowserMenu(
            val buttonsDisabled: Boolean,
            val sortingMode: SortingMode,
        ) : Command()
    }

    companion object {
        private const val MIN_ITEMS_FOR_SEARCH = 5
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    private val hiddenIds = MutableStateFlow(HiddenBookmarksIds())

    data class HiddenBookmarksIds(val items: List<String> = emptyList())

    private val _sortedItems = MutableStateFlow<List<BookmarksItemTypes>>(emptyList())
    val sortedItems = _sortedItems.asStateFlow()

    init {
        viewState.value = ViewState()
        viewModelScope.launch(dispatcherProvider.io()) {
            syncEngine.triggerSync(FEATURE_READ)
        }
        pixel.fire(
            SavedSitesPixelName.MENU_ACTION_BOOKMARKS_PRESSED_DAILY.pixelName,
            parameters = mapOf(SavedSitesPixelParameters.SORT_MODE to bookmarksDataStore.getSortingMode().name),
            type = Daily(),
        )
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
            logcat { "Bookmark: $bookmark from $oldFolderId" }
            savedSitesRepository.updateBookmark(bookmark, oldFolderId, updateFavorite)
        }
    }

    override fun onFavoriteAdded() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED)
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED_DAILY, type = Daily())
    }

    override fun onFavoriteRemoved() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED)
    }

    override fun onSavedSiteDeleted(savedSite: SavedSite) {
        onDeleteSavedSiteRequested(savedSite)
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CONFIRMED)
    }

    override fun onSavedSiteDeleteCancelled() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CANCELLED)
    }

    override fun onSavedSiteDeleteRequested() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CLICKED)
    }

    fun onSelected(savedSite: SavedSite) {
        if (savedSite is Favorite) {
            pixel.fire(SavedSitesPixelName.FAVORITE_BOOKMARKS_ITEM_PRESSED)
        }
        pixel.fire(SavedSitesPixelName.BOOKMARK_LAUNCHED)
        pixel.fire(SavedSitesPixelName.BOOKMARK_LAUNCHED_DAILY, type = Daily())
        command.value = OpenSavedSite(savedSite.url)
    }

    fun onEditSavedSiteRequested(savedSite: SavedSite) {
        command.value = ShowEditSavedSite(savedSite)
        pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_EDIT_BOOKMARK_CLICKED)
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
                    items = hiddenIds.value.items - savedSite.id,
                ),
            )
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
        viewModelScope.launch(dispatcherProvider.io()) {
            if (faviconsFetchingPrompt.shouldShow()) {
                withContext(dispatcherProvider.main()) {
                    command.value = ShowFaviconsPrompt
                }
            }

            savedSitesRepository.getSavedSites(parentId)
                .combine(hiddenIds) { savedSites, hiddenIds ->
                    val filteredBookmarks = savedSites.bookmarks.filter {
                        when (it) {
                            is Bookmark -> it.id !in hiddenIds.items
                            is BookmarkFolder -> it.id !in hiddenIds.items
                            else -> false
                        }
                    }
                    savedSites.copy(
                        bookmarks = filteredBookmarks,
                        favorites = savedSites.favorites.filter { it.id !in hiddenIds.items },
                    )
                }.collect {
                    onSavedSitesItemsChanged(it.favorites, it.bookmarks)
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
            onSavedSitesItemsChanged(favorites, bookmarks + folders)
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
            hiddenIds.emit(hiddenIds.value.copy(items = hiddenIds.value.items - bookmarkFolder.id))
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
            hiddenIds.emit(
                hiddenIds.value.copy(
                    items = hiddenIds.value.items + savedSite.id,
                ),
            )
        }
    }

    fun hide(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io()) {
            hiddenIds.emit(hiddenIds.value.copy(items = hiddenIds.value.items + bookmarkFolder.id))
        }
        command.postValue(ConfirmDeleteBookmarkFolder(bookmarkFolder))
    }

    private suspend fun onSavedSitesItemsChanged(
        favorites: List<Favorite>,
        bookmarksAndFolders: List<Any>,
    ) {
        val bookmarkItems = bookmarksAndFolders.mapNotNull { bookmark ->
            when (bookmark) {
                is Bookmark -> {
                    val isFavorite = favorites.any { favorite -> favorite.id == bookmark.id }
                    BookmarkItem(bookmark.copy(isFavorite = isFavorite))
                }

                is BookmarkFolder -> BookmarkFolderItem(bookmark)
                else -> null
            }
        }

        val sortingMode = bookmarksDataStore.getSortingMode()
        _sortedItems.value = sortElements(bookmarkItems, sortingMode)
        withContext(dispatcherProvider.main()) {
            viewState.value = viewState.value?.copy(
                favorites = favorites,
                bookmarkItems = bookmarkItems,
                enableSearch = bookmarkItems.size >= MIN_ITEMS_FOR_SEARCH,
                sortingMode = sortingMode,
            )
        }

        showSyncPromotionIfEligible()
    }

    fun sortElements(
        bookmarkItems: List<BookmarksItemTypes>,
        sortingMode: SortingMode,
    ): List<BookmarksItemTypes> {
        return when (sortingMode) {
            MANUAL -> bookmarkItems
            NAME -> {
                bookmarkItems.sortedWith(BookmarksNameSortingComparator())
            }
        }
    }

    fun onBookmarkFoldersActivityResult(savedSiteUrl: String) {
        command.value = OpenSavedSite(savedSiteUrl)
    }

    fun updateBookmarks(
        bookmarksAndFolders: List<String>,
        parentId: String,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            logcat { "Bookmarks: parentId: $parentId, updateBookmarks $bookmarksAndFolders" }
            savedSitesRepository.updateFolderRelation(parentId, bookmarksAndFolders)
        }
    }

    fun addFavorite(bookmark: Bookmark) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.insertFavorite(bookmark.id, bookmark.url, bookmark.title)
            pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_ADD_FAVORITE_CLICKED)
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
            pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_REMOVE_FAVORITE_CLICKED)
        }
    }

    fun onFaviconsFetchingEnabled(
        fetchingEnabled: Boolean,
        currentFolderId: String,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            faviconsFetchingPrompt.onPromptAnswered(fetchingEnabled)
        }
        if (fetchingEnabled) {
            forceFaviconsRefresh(currentFolderId)
        }
    }

    /**
     * By sending an empty state and then the current folder we force the adapter to bind its elements
     * We need to do this so the [FaviconManager] can load the favicons again
     */
    private fun forceFaviconsRefresh(currentFolderId: String) {
        val currentState = viewState.value!!
        viewState.value = currentState.copy(
            favorites = emptyList(),
            bookmarkItems = emptyList(),
            enableSearch = currentState.enableSearch,
            sortingMode = bookmarksDataStore.getSortingMode(),
        )
        fetchBookmarksAndFolders(currentFolderId)
    }

    fun onBookmarkItemDeletedFromOverflowMenu() {
        pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_DELETE_BOOKMARK_CLICKED)
    }

    suspend fun onSearchQueryUpdated(newText: String) {
        withContext(dispatcherProvider.main()) {
            viewState.value = viewState.value?.copy(searchQuery = newText)
            showSyncPromotionIfEligible()
        }
    }

    private suspend fun showSyncPromotionIfEligible() {
        val userIsSearching = viewState.value?.searchQuery?.isNotEmpty() == true

        val canShowPromo = when {
            userIsSearching -> false
            else -> true
        }

        withContext(dispatcherProvider.main()) {
            viewState.value = viewState.value?.copy(canShowPromo = canShowPromo)
        }
    }

    fun userReturnedFromSyncSettings() {
        viewModelScope.launch(dispatcherProvider.io()) {
            showSyncPromotionIfEligible()
        }
    }

    fun onPromotionDismissed() {
        viewModelScope.launch(dispatcherProvider.io()) {
            showSyncPromotionIfEligible()
        }
    }

    fun onBrowserMenuPressed() {
        val buttonsDisabled = viewState.value?.bookmarkItems?.isEmpty() ?: true
        val sortingMode = viewState.value?.sortingMode ?: MANUAL
        command.value = ShowBrowserMenu(buttonsDisabled, sortingMode)
    }

    fun onSortingModeSelected(mode: SortingMode) {
        viewModelScope.launch(dispatcherProvider.io()) {
            bookmarksDataStore.setSortingMode(mode)
            val bookmarkItems = viewState.value?.bookmarkItems
            val sortedBookmarks = sortElements(bookmarkItems ?: emptyList(), mode)
            _sortedItems.value = sortedBookmarks
            withContext(dispatcherProvider.main()) {
                viewState.value = viewState.value?.copy(
                    sortingMode = bookmarksDataStore.getSortingMode(),
                )
            }
            when (mode) {
                NAME -> pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_SORT_NAME_CLICKED)
                MANUAL -> pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_SORT_MANUAL_CLICKED)
            }
        }
    }

    fun onImportBookmarksClicked() {
        pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_IMPORT_CLICKED)
        command.value = LaunchBookmarkImport
    }

    fun onExportBookmarksClicked() {
        pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_EXPORT_CLICKED)
        command.value = LaunchBookmarkExport
    }

    fun onAddFolderClicked() {
        pixel.fire(SavedSitesPixelName.BOOKMARK_MENU_ADD_FOLDER_CLICKED)
        command.value = LaunchAddFolder
    }
}
