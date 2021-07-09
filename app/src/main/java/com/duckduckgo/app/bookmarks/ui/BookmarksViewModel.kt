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
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.service.ExportSavedSitesResult
import com.duckduckgo.app.bookmarks.service.ImportSavedSitesResult
import com.duckduckgo.app.bookmarks.service.SavedSitesManager
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel.Command.*
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class BookmarksViewModel(
    private val favoritesRepository: FavoritesRepository,
    val dao: BookmarksDao,
    private val faviconManager: FaviconManager,
    private val savedSitesManager: SavedSitesManager,
    private val dispatcherProvider: DispatcherProvider
) : EditSavedSiteListener, ViewModel() {

    data class ViewState(
        val enableSearch: Boolean = false,
        val bookmarks: List<Bookmark> = emptyList(),
        val favorites: List<Favorite> = emptyList()
    )

    sealed class Command {
        class OpenSavedSite(val savedSite: SavedSite) : Command()
        class ConfirmDeleteSavedSite(val savedSite: SavedSite) : Command()
        class ShowEditSavedSite(val savedSite: SavedSite) : Command()
        data class ImportedSavedSites(val importSavedSitesResult: ImportSavedSitesResult) : Command()
        data class ExportedSavedSites(val exportSavedSitesResult: ExportSavedSitesResult) : Command()
    }

    companion object {
        private const val MIN_BOOKMARKS_FOR_SEARCH = 3
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val bookmarks: LiveData<List<Bookmark>> = dao.getBookmarks().map { bookmarks ->
        bookmarks.map { Bookmark(it.id, it.title ?: "", it.url) }
    }
    private val bookmarksObserver = Observer<List<Bookmark>> { onBookmarksChanged(it!!) }

    init {
        viewState.value = ViewState()
        bookmarks.observeForever(bookmarksObserver)
        viewModelScope.launch {
            favoritesRepository.favorites().collect {
                onFavoritesChanged(it)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bookmarks.removeObserver(bookmarksObserver)
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
                    dao.delete(BookmarkEntity(savedSite.id, savedSite.title, savedSite.url))
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
                    dao.insert(BookmarkEntity(title = savedSite.title, url = savedSite.url))
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
            dao.update(BookmarkEntity(bookmark.id, bookmark.title, bookmark.url))
        }
    }

    private suspend fun editFavorite(favorite: Favorite) {
        withContext(dispatcherProvider.io()) {
            favoritesRepository.update(favorite)
        }
    }

    private fun onBookmarksChanged(bookmarks: List<Bookmark>) {
        viewState.value = viewState.value?.copy(
            bookmarks = bookmarks,
            enableSearch = bookmarks.size > MIN_BOOKMARKS_FOR_SEARCH
        )
    }

    private fun onFavoritesChanged(favorites: List<Favorite>) {
        viewState.value = viewState.value?.copy(favorites = favorites)
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class BookmarksViewModelFactory @Inject constructor(
    private val favoritesRepository: Provider<FavoritesRepository>,
    private val dao: Provider<BookmarksDao>,
    private val faviconManager: Provider<FaviconManager>,
    private val savedSitesManager: Provider<SavedSitesManager>,
    private val dispatcherProvider: Provider<DispatcherProvider>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BookmarksViewModel::class.java) -> (
                    BookmarksViewModel(
                        favoritesRepository.get(),
                        dao.get(),
                        faviconManager.get(),
                        savedSitesManager.get(),
                        dispatcherProvider.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}
