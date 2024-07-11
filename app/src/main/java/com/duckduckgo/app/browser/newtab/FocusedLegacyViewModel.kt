/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.newtab

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.BrowserTabViewModel.HiddenBookmarksIds
import com.duckduckgo.app.browser.newtab.FocusedLegacyViewModel.Command.DeleteFavoriteConfirmation
import com.duckduckgo.app.browser.newtab.FocusedLegacyViewModel.Command.DeleteSavedSiteConfirmation
import com.duckduckgo.app.browser.newtab.FocusedLegacyViewModel.Command.ShowEditSavedSiteDialog
import com.duckduckgo.app.browser.viewstate.SavedSiteChangedViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.DeleteBookmarkListener
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.EditSavedSiteListener
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class FocusedLegacyViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val savedSitesRepository: SavedSitesRepository,
    private val pixel: Pixel,
) : ViewModel(), DefaultLifecycleObserver, EditSavedSiteListener, DeleteBookmarkListener {

    data class ViewState(
        val favourites: List<Favorite> = emptyList(),
    )

    sealed class Command {
        class ShowEditSavedSiteDialog(val savedSiteChangedViewState: SavedSiteChangedViewState) : Command()
        class DeleteFavoriteConfirmation(val savedSite: SavedSite) : Command()
        class DeleteSavedSiteConfirmation(val savedSite: SavedSite) : Command()
    }

    val hiddenIds = MutableStateFlow(HiddenBookmarksIds())

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.getFavorites()
                .combine(hiddenIds) { favorites, hiddenIds ->
                    favorites.filter { it.id !in hiddenIds.favorites }
                }
                .flowOn(dispatchers.io())
                .onEach { favourites ->
                    withContext(dispatchers.main()) {
                        _viewState.emit(
                            viewState.value.copy(
                                favourites = favourites,
                            ),
                        )
                    }
                }
                .flowOn(dispatchers.main())
                .launchIn(viewModelScope)
        }
    }

    fun onEditSavedSiteRequested(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io()) {
            val bookmarkFolder =
                if (savedSite is SavedSite.Bookmark) {
                    getBookmarkFolder(savedSite)
                } else {
                    null
                }

            withContext(dispatchers.main()) {
                command.send(
                    ShowEditSavedSiteDialog(
                        SavedSiteChangedViewState(
                            savedSite,
                            bookmarkFolder,
                        ),
                    ),
                )
            }
        }
    }

    fun onDeleteFavoriteRequested(savedSite: SavedSite) {
        hide(savedSite, DeleteFavoriteConfirmation(savedSite))
    }

    private fun hide(
        savedSite: SavedSite,
        deleteCommand: Command,
    ) {
        viewModelScope.launch(dispatchers.io()) {
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
            withContext(dispatchers.main()) {
                command.send(deleteCommand)
            }
        }
    }

    fun onDeleteFavoriteSnackbarDismissed(savedSite: SavedSite) {
        delete(savedSite)
    }

    fun onDeleteSavedSiteSnackbarDismissed(savedSite: SavedSite) {
        delete(savedSite, true)
    }

    private fun delete(
        savedSite: SavedSite,
        deleteBookmark: Boolean = false,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.delete(savedSite, deleteBookmark)
        }
    }

    fun undoDelete(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io()) {
            hiddenIds.emit(
                hiddenIds.value.copy(
                    favorites = hiddenIds.value.favorites - savedSite.id,
                    bookmarks = hiddenIds.value.bookmarks - savedSite.id,
                ),
            )
        }
    }

    fun onQuickAccessListChanged(newList: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateWithPosition(newList.map { it.favorite })
        }
    }

    private suspend fun getBookmarkFolder(bookmark: SavedSite.Bookmark?): BookmarkFolder? {
        if (bookmark == null) return null
        return withContext(dispatchers.io()) {
            savedSitesRepository.getFolder(bookmark.parentId)
        }
    }

    override fun onFavouriteEdited(favorite: Favorite) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateFavourite(favorite)
        }
    }

    override fun onBookmarkEdited(
        bookmark: Bookmark,
        oldFolderId: String,
        updateFavorite: Boolean,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateBookmark(bookmark, oldFolderId, updateFavorite)
        }
    }

    override fun onFavoriteAdded() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED)
    }

    override fun onFavoriteRemoved() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED)
    }

    override fun onSavedSiteDeleted(savedSite: SavedSite) {
        onDeleteSavedSiteRequested(savedSite)
    }

    override fun onSavedSiteDeleteCancelled() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CANCELLED)
    }

    override fun onSavedSiteDeleteRequested() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CLICKED)
    }

    fun onDeleteSavedSiteRequested(savedSite: SavedSite) {
        hide(savedSite, DeleteSavedSiteConfirmation(savedSite))
    }
}
