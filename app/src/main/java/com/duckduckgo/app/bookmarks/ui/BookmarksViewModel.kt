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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.support.annotation.WorkerThread
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel.Command.ConfirmDeleteBookmark
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel.Command.OpenBookmark
import com.duckduckgo.app.global.SingleLiveEvent

class BookmarksViewModel(val dao: BookmarksDao): ViewModel() {

    data class ViewState(val showBookmarks: Boolean = false,
                         val bookmarks: List<BookmarkEntity> = emptyList())

    sealed class Command {

        class OpenBookmark(val bookmark: BookmarkEntity) : Command()
        class ConfirmDeleteBookmark(val bookmark: BookmarkEntity) : Command()

    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val bookmarks: LiveData<List<BookmarkEntity>> = dao.bookmarks()
    private val bookmarksObserver = Observer<List<BookmarkEntity>> { onBookmarksChanged(it!!) }

    init {
        viewState.value = ViewState()
        bookmarks.observeForever(bookmarksObserver)
    }

    override fun onCleared() {
        super.onCleared()
        bookmarks.removeObserver(bookmarksObserver)
    }

    private fun onBookmarksChanged(bookmarks: List<BookmarkEntity>) {
        viewState.value = viewState.value?.copy(showBookmarks = bookmarks.isNotEmpty(), bookmarks = bookmarks)
    }

    fun onSelected(bookmark: BookmarkEntity) {
        command.value = OpenBookmark(bookmark)
    }

    fun onDeleteRequested(bookmark: BookmarkEntity) {
        command.value = ConfirmDeleteBookmark(bookmark)
    }

    @WorkerThread
    fun delete(bookmark: BookmarkEntity) {
        dao.delete(bookmark)
    }

}