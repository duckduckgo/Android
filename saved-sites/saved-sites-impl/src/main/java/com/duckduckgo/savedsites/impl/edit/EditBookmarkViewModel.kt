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

package com.duckduckgo.savedsites.impl.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class EditBookmarkViewModel @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val faviconManager: FaviconManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    data class ViewState(
        val savedSite: Bookmark? = null,
        val bookmarkFolder: BookmarkFolder? = null,
    )

    fun loadBookmark(bookmarkId: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val savedSite = savedSitesRepository.getBookmarkById(bookmarkId)
            if (savedSite != null) {
                val parentFolder = savedSitesRepository.getFolder(savedSite.parentId)
                withContext(dispatcherProvider.main()) {
                    _viewState.update { ViewState(savedSite, parentFolder) }
                }
            }
        }
    }

    fun onFavouriteEdited(favorite: Favorite) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.updateFavourite(favorite)
        }
    }

    fun onBookmarkEdited(
        bookmark: Bookmark,
        oldFolderId: String,
        updateFavorite: Boolean,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.updateBookmark(bookmark, oldFolderId, updateFavorite)
        }
    }

    fun delete(bookmark: Bookmark) {
        viewModelScope.launch(dispatcherProvider.io()) {
            faviconManager.deletePersistedFavicon(bookmark.url)
            savedSitesRepository.delete(bookmark, true)
        }
    }

    fun confirmChanges(
        bookmark: Bookmark,
        oldFolderId: String,
        updateFavorite: Boolean,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.updateBookmark(bookmark, oldFolderId, updateFavorite)
        }
    }

    enum class ValidationState {
        CHANGED,
        UNCHANGED,
        INVALID,
    }
}
