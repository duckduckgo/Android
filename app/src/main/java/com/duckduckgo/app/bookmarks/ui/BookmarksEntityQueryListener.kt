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

import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersAdapter

class BookmarksEntityQueryListener(
    private val viewModel: BookmarksViewModel,
    private val bookmarksAdapter: BookmarksAdapter,
    private val bookmarkFoldersAdapter: BookmarkFoldersAdapter
) {

    fun onQueryTextChange(newText: String): Boolean {
        viewModel.viewState.value?.bookmarks?.let { bookmarks ->
            viewModel.viewState.value?.bookmarkFolders?.let { bookmarkFolders ->
                val filteredFolders = filterBookmarkFolders(newText, bookmarkFolders)
                bookmarksAdapter.setItems(filterBookmarks(newText, bookmarks), filteredFolders.isEmpty())
                bookmarkFoldersAdapter.bookmarkFolderItems = filteredFolders
            }
        }
        return true
    }

    private fun filterBookmarks(
        query: String,
        bookmarks: List<SavedSite.Bookmark>
    ): List<BookmarksAdapter.BookmarkItem> {
        val lowercaseQuery = query.toLowerCase()
        return bookmarks.filter {
            val lowercaseTitle = it.title.toLowerCase()
            lowercaseTitle.contains(lowercaseQuery) || it.url.contains(lowercaseQuery)
        }.map { BookmarksAdapter.BookmarkItem(it) }
    }

    private fun filterBookmarkFolders(
        query: String,
        bookmarkFolders: List<BookmarkFolder>
    ): List<BookmarkFoldersAdapter.BookmarkFolderItem> {
        val lowercaseQuery = query.toLowerCase()
        return bookmarkFolders.filter {
            val lowercaseTitle = it.name.toLowerCase()
            lowercaseTitle.contains(lowercaseQuery)
        }.map { BookmarkFoldersAdapter.BookmarkFolderItem(it) }
    }
}
