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
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.DeleteFavoriteConfirmation
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.DeleteSavedSiteConfirmation
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.ShowEditSavedSiteDialog
import com.duckduckgo.app.browser.remotemessage.CommandActionMapper
import com.duckduckgo.app.browser.viewstate.SavedSiteChangedViewState
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.DeleteBookmarkListener
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
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
class NewTabLegacyPageViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val remoteMessagingModel: RemoteMessageModel,
    private val playStoreUtils: PlayStoreUtils,
    private val savedSitesRepository: SavedSitesRepository,
    private val syncEngine: SyncEngine,
    private val commandActionMapper: CommandActionMapper,
    private val dismissedCtaDao: DismissedCtaDao,
) : ViewModel(), DefaultLifecycleObserver, EditSavedSiteListener, DeleteBookmarkListener {

    data class ViewState(
        val message: RemoteMessage? = null,
        val newMessage: Boolean = false,
        val onboardingComplete: Boolean = false,
        val favourites: List<Favorite> = emptyList(),
    )

    private data class ViewStateSnapshot(
        val favourites: List<Favorite>,
        val remoteMessage: RemoteMessage?,
    )

    sealed class Command {
        data object DismissMessage : Command()
        data class LaunchPlayStore(val appPackage: String) : Command()
        data class SubmitUrl(val url: String) : Command()
        data object LaunchDefaultBrowser : Command()
        data object LaunchAppTPOnboarding : Command()
        data class SharePromoLinkRMF(
            val url: String,
            val shareTitle: String,
        ) : Command()

        data class LaunchScreen(
            val screen: String,
            val payload: String,
        ) : Command()

        class ShowEditSavedSiteDialog(val savedSiteChangedViewState: SavedSiteChangedViewState) : Command()
        class DeleteFavoriteConfirmation(val savedSite: SavedSite) : Command()
        class DeleteSavedSiteConfirmation(val savedSite: SavedSite) : Command()
    }

    private var lastRemoteMessageSeen: RemoteMessage? = null
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
                    if (favorites.isNotEmpty()) {
                        syncEngine.triggerSync(FEATURE_READ)
                    }
                    favorites.filter { it.id !in hiddenIds.favorites }
                }
                .combine(remoteMessagingModel.getActiveMessages()) { filteredFavourites, activeMessage ->
                    ViewStateSnapshot(filteredFavourites, activeMessage)
                }
                .flowOn(dispatchers.io())
                .onEach { snapshot ->
                    val newMessage = snapshot.remoteMessage?.id != lastRemoteMessageSeen?.id
                    if (newMessage) {
                        lastRemoteMessageSeen = snapshot.remoteMessage
                    }

                    withContext(dispatchers.io()) {
                        val onboardingComplete = dismissedCtaDao.exists(CtaId.DAX_END)
                        _viewState.emit(
                            viewState.value.copy(
                                message = snapshot.remoteMessage,
                                newMessage = newMessage,
                                favourites = snapshot.favourites,
                                onboardingComplete = onboardingComplete,
                            ),
                        )
                    }
                }
                .flowOn(dispatchers.main())
                .launchIn(viewModelScope)
        }
    }

    fun onMessageShown() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            remoteMessagingModel.onMessageShown(message)
        }
    }

    fun onMessageCloseButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            remoteMessagingModel.onMessageDismissed(message)
        }
    }

    fun onMessagePrimaryButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onPrimaryActionClicked(message) ?: return@launch
            val tabCommand = commandActionMapper.asNewTabCommand(action)
            command.send(tabCommand)
        }
    }

    fun onMessageSecondaryButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onSecondaryActionClicked(message) ?: return@launch
            val tabCommand = commandActionMapper.asNewTabCommand(action)
            command.send(tabCommand)
        }
    }

    fun onMessageActionButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onActionClicked(message) ?: return@launch
            val tabCommand = commandActionMapper.asNewTabCommand(action)
            command.send(tabCommand)
        }
    }

    fun openPlayStore(appPackage: String) {
        playStoreUtils.launchPlayStore(appPackage)
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

    fun onQuickAccessListChanged(newList: List<QuickAccessFavorite>) {
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

    override fun onSavedSiteDeleted(savedSite: SavedSite) {
        onDeleteSavedSiteRequested(savedSite)
    }

    fun onDeleteSavedSiteRequested(savedSite: SavedSite) {
        hide(savedSite, DeleteSavedSiteConfirmation(savedSite))
    }
}
