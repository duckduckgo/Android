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

import androidx.appcompat.widget.SearchView
import com.duckduckgo.app.bookmarks.db.BookmarkEntity

class BookmarksEntityQueryListener(
    val bookmarks: List<BookmarkEntity>?,
    val adapter: BookmarksActivity.BookmarksAdapter
) : SearchView.OnQueryTextListener {

    override fun onQueryTextChange(newText: String): Boolean {
        if (bookmarks != null) {
            //adapter.bookmarks = filter(newText, bookmarks)
            adapter.notifyDataSetChanged()
        }
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    private fun filter(query: String, bookmarks: List<BookmarkEntity>): List<BookmarkEntity> {
        val lowercaseQuery = query.toLowerCase()
        return bookmarks.filter {
            val lowercaseTitle = it.title?.toLowerCase()
            lowercaseTitle?.contains(lowercaseQuery) == true || it.url.contains(lowercaseQuery)
        }
    }
}
