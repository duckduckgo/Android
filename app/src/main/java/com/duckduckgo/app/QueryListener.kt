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

package com.duckduckgo.app

import androidx.appcompat.widget.SearchView
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel

class QueryListener(
    val adapter: BookmarksActivity.BookmarksAdapter,
    val bookMarks: BookmarksViewModel
) : SearchView.OnQueryTextListener {


    override fun onQueryTextChange(newText: String): Boolean {
        adapter.bookmarks = filter(newText, bookMarks.viewState.value?.bookmarks)!!
        adapter.notifyDataSetChanged()
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    private fun filter(query: String, bookMarks: List<BookmarkEntity>?): List<BookmarkEntity>? {
        val toLowerCaseQuery = query.toLowerCase()
        return bookMarks?.filter { it.title?.toLowerCase()!!.contains(toLowerCaseQuery) }
    }
}