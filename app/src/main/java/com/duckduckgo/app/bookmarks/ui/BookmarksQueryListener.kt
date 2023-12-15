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
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
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
            viewModel.viewState.value?.bookmarks?.let { bookmarks ->
                val filteredBookmarks = filterBookmarks(newText, bookmarks)
                bookmarksAdapter.setItems(filteredBookmarks, false, true)
            }
        }
    }

    fun cancelSearch() {
        searchJob.cancel()
    }

    private fun filterBookmarks(
        query: String,
        bookmarks: List<Any>,
    ): List<BookmarksAdapter.BookmarksItemTypes> {
        val lowercaseQuery = query.lowercase(Locale.getDefault())
        return bookmarks.filter {
            when (it) {
                is Bookmark -> {
                    val lowercaseTitle = it.title.lowercase(Locale.getDefault())
                    lowercaseTitle.contains(lowercaseQuery) || it.url.contains(lowercaseQuery)
                }
                is BookmarkFolder -> {
                    val lowercaseTitle = it.name.lowercase(Locale.getDefault())
                    lowercaseTitle.contains(lowercaseQuery)
                }
                else -> false
            }
        }.map {
            when (it) {
                is Bookmark -> BookmarksAdapter.BookmarkItem(it)
                is BookmarkFolder -> BookmarksAdapter.BookmarkFolderItem(it)
                else -> throw IllegalStateException("Unknown bookmarks item type")
            }
        }
    }

    companion object {
        private const val DEBOUNCE_PERIOD = 400L
    }
}
