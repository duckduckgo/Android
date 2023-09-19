/*
 * Copyright (c) 2019 DuckDuckGo
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

import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersAdapter
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class BookmarksEntityQueryListener(
    private val viewModel: BookmarksViewModel,
    private val bookmarksAdapter: BookmarksAdapter,
    private val bookmarkFoldersAdapter: BookmarkFoldersAdapter,
    private val dispatcherProvider: DispatcherProvider,
) {

    private var searchJob = ConflatedJob()

    fun onQueryTextChange(newText: String) {
        searchJob += viewModel.viewModelScope.launch(dispatcherProvider.io()) {
            delay(DEBOUNCE_PERIOD)
            viewModel.viewState.value?.bookmarks?.let { bookmarks ->
                viewModel.viewState.value?.bookmarkFolders?.let { bookmarkFolders ->
                    val filteredFolders = filterBookmarkFolders(newText, bookmarkFolders)
                    val filteredBookmarks = filterBookmarks(newText, bookmarks)
                    Timber.d("Bookmark: filter $filteredBookmarks")
                    bookmarksAdapter.setItems(filteredBookmarks, filteredFolders.isEmpty(), true)
                    bookmarkFoldersAdapter.bookmarkFolderItems = filteredFolders
                }
            }
        }
    }

    fun cancelSearch() {
        searchJob.cancel()
    }

    private fun filterBookmarks(
        query: String,
        bookmarks: List<SavedSite.Bookmark>,
    ): List<BookmarksAdapter.BookmarkItem> {
        val lowercaseQuery = query.lowercase(Locale.getDefault())
        return bookmarks.filter {
            val lowercaseTitle = it.title.lowercase(Locale.getDefault())
            lowercaseTitle.contains(lowercaseQuery) || it.url.contains(lowercaseQuery)
        }.map { BookmarksAdapter.BookmarkItem(it) }
    }

    private fun filterBookmarkFolders(
        query: String,
        bookmarkFolders: List<BookmarkFolder>,
    ): List<BookmarkFoldersAdapter.BookmarkFolderItem> {
        val lowercaseQuery = query.lowercase(Locale.getDefault())
        return bookmarkFolders.filter {
            val lowercaseTitle = it.name.lowercase(Locale.getDefault())
            lowercaseTitle.contains(lowercaseQuery)
        }.map { BookmarkFoldersAdapter.BookmarkFolderItem(it) }
    }

    companion object {
        private const val DEBOUNCE_PERIOD = 400L
    }
}
