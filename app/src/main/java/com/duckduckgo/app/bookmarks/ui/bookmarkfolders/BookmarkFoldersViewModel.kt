/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.ui.bookmarkfolders

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.BookmarkFolderItem
import com.duckduckgo.app.bookmarks.model.BookmarksRepository
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.AddBookmarkFolderDialogFragment.AddBookmarkFolderListener
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersViewModel.Command.NewFolderCreatedUpdateTheStructure
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersViewModel.Command.SelectFolder
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class BookmarkFoldersViewModel @Inject constructor(
    val bookmarksRepository: BookmarksRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel(), AddBookmarkFolderListener {

    data class ViewState(
        val folderStructure: List<BookmarkFolderItem> = emptyList()
    )

    sealed class Command {
        class SelectFolder(val selectedBookmarkFolder: BookmarkFolder) : BookmarkFoldersViewModel.Command()
        object NewFolderCreatedUpdateTheStructure : BookmarkFoldersViewModel.Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        viewState.value = ViewState()
    }

    fun fetchBookmarkFolders(
        selectedFolderId: Long,
        rootFolderName: String,
        currentFolder: BookmarkFolder?
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val folderStructure = bookmarksRepository.getFlatFolderStructure(selectedFolderId, currentFolder, rootFolderName)
            onFolderStructureCreated(folderStructure)
        }
    }

    fun newFolderAdded(
        rootFolderName: String,
        selectedFolderId: Long,
        currentFolder: BookmarkFolder?
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val folderStructure = bookmarksRepository.getFlatFolderStructure(
                selectedFolderId,
                currentFolder,
                rootFolderName
            )
            onFolderStructureCreated(folderStructure)
        }
    }

    private fun onFolderStructureCreated(folderStructure: List<BookmarkFolderItem>) {
        viewState.postValue(viewState.value?.copy(folderStructure = folderStructure))
    }

    fun onItemSelected(bookmarkFolder: BookmarkFolder) {
        command.value = SelectFolder(bookmarkFolder)
    }

    override fun onBookmarkFolderAdded(bookmarkFolder: BookmarkFolder) {
        viewModelScope.launch(dispatcherProvider.io()) {
            bookmarksRepository.insert(bookmarkFolder)
        }
        command.value = NewFolderCreatedUpdateTheStructure
    }
}
