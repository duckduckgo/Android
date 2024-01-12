/*
 * Copyright (c) 2023 DuckDuckGo
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
import com.duckduckgo.app.bookmarks.ui.BookmarksAdapter.BookmarkFolderItem
import com.duckduckgo.app.bookmarks.ui.BookmarksAdapter.BookmarkItem
import com.duckduckgo.app.bookmarks.ui.BookmarksAdapter.BookmarksItemTypes
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BookmarksQueryListener(
    private val viewModel: BookmarksViewModel,
    private val bookmarksAdapter: BookmarksAdapter,
) {

    private var searchJob = ConflatedJob()

    fun onQueryTextChange(newText: String) {
        searchJob += viewModel.viewModelScope.launch {
            delay(DEBOUNCE_PERIOD)
            viewModel.viewState.value = viewModel.viewState.value?.copy(
                searchQuery = newText,
            )
            val favorites = viewModel.viewState.value?.favorites
            viewModel.viewState.value?.bookmarkItems?.let { bookmarks ->
                val filteredBookmarks = filterBookmarks(newText, bookmarks, favorites)
                bookmarksAdapter.setItems(filteredBookmarks, false, true)
            }
        }
    }

    fun cancelSearch() {
        searchJob.cancel()
    }

    private fun filterBookmarks(
        query: String,
        bookmarks: List<BookmarksItemTypes>,
        favorites: List<Favorite>?,
    ): List<BookmarksItemTypes> {
        val lowercaseQuery = query.lowercase(Locale.getDefault())
        return bookmarks.filter {
            when (it) {
                is BookmarkItem -> {
                    val lowercaseTitle = it.bookmark.title.lowercase(Locale.getDefault())
                    lowercaseTitle.contains(lowercaseQuery) || it.bookmark.url.contains(lowercaseQuery)
                }
                is BookmarkFolderItem -> {
                    val lowercaseTitle = it.bookmarkFolder.name.lowercase(Locale.getDefault())
                    lowercaseTitle.contains(lowercaseQuery)
                }
                else -> false
            }
        }.map {
            when (it) {
                is BookmarkItem -> {
                    val isFavorite = favorites?.any { favorite -> favorite.id == it.bookmark.id } ?: false
                    BookmarkItem(it.bookmark.copy(isFavorite = isFavorite))
                }
                is BookmarkFolderItem -> it
                else -> throw IllegalStateException("Unknown bookmarks item type")
            }
        }
    }

    companion object {
        private const val DEBOUNCE_PERIOD = 400L
    }
}
