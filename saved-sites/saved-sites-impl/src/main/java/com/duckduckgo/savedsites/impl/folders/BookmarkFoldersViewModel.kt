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

package com.duckduckgo.savedsites.impl.folders

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem
import com.duckduckgo.savedsites.impl.dialogs.AddBookmarkFolderDialogFragment.AddBookmarkFolderListener
import com.duckduckgo.savedsites.impl.folders.BookmarkFoldersViewModel.Command.NewFolderCreatedUpdateTheStructure
import com.duckduckgo.savedsites.impl.folders.BookmarkFoldersViewModel.Command.SelectFolder
import javax.inject.Inject
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class BookmarkFoldersViewModel @Inject constructor(
    val savedSitesRepository: SavedSitesRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), AddBookmarkFolderListener {

    data class ViewState(
        val folderStructure: List<BookmarkFolderItem> = emptyList(),
    )

    sealed class Command {
        class SelectFolder(val selectedBookmarkFolder: BookmarkFolder) : Command()
        data object NewFolderCreatedUpdateTheStructure : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        viewState.value = ViewState()
    }

    fun fetchBookmarkFolders(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
    ) {
        logcat { "Saved sites: selectedFolderId $selectedFolderId" }
        logcat { "Saved sites: currentFolder $currentFolder" }
        viewModelScope.launch(dispatcherProvider.io()) {
            val folderStructure = savedSitesRepository.getFolderTree(selectedFolderId, currentFolder)
            onFolderStructureCreated(folderStructure)
        }
    }

    fun newFolderAdded(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val folderStructure = savedSitesRepository.getFolderTree(
                selectedFolderId,
                currentFolder,
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
            savedSitesRepository.insert(bookmarkFolder)
        }
        command.value = NewFolderCreatedUpdateTheStructure
    }
}
