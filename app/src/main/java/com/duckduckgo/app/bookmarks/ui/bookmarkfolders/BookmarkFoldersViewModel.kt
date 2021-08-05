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
import com.duckduckgo.app.bookmarks.db.BookmarkFoldersDao
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.BookmarkFolderItem
import com.duckduckgo.app.bookmarks.model.BookmarkFoldersRepository
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class BookmarkFoldersViewModel(
    val bookmarkFoldersRepository: BookmarkFoldersRepository,
    val bookmarkFoldersDao: BookmarkFoldersDao,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    data class ViewState(
        val folderStructure: List<BookmarkFolderItem> = emptyList(),
        val selectedBookmarkFolder: BookmarkFolder? = null
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    var lastPosition = 0

    init {
        viewState.value = ViewState()
    }

    private fun getBookmarkFolders(): List<BookmarkFolder> {
        return bookmarkFoldersDao.getBookmarkFolders().map {
            BookmarkFolder(it.id, it.name, it.parentId)
        }
    }

    fun fetchBookmarkFolders(selectedFolderId: Long, rootFolderName: String, currentFolder: BookmarkFolder?) {
        viewModelScope.launch(dispatcherProvider.io()) {
            var bookmarkFolders = getBookmarkFolders()

            currentFolder?.let {
                val bookmarkFolderBranch = bookmarkFoldersRepository.getBranchFolders(BookmarkFolder(id = currentFolder.id, name = currentFolder.name, parentId = currentFolder.parentId))
                bookmarkFolders = bookmarkFolders.minus(bookmarkFolderBranch)
            }

            var folderStructure = bookmarkFoldersRepository.buildFlatStructure(bookmarkFolders, selectedFolderId)
            folderStructure = addBookmarksAsRoot(folderStructure, rootFolderName, selectedFolderId)
            onFolderStructureCreated(folderStructure)
        }
    }

    private fun addBookmarksAsRoot(folderStructure: List<BookmarkFolderItem>, rootFolder: String, selectedFolderId: Long) =
        listOf(BookmarkFolderItem(0, BookmarkFolder(0, rootFolder, -1), isSelected = selectedFolderId == 0L)) + folderStructure

    private fun onFolderStructureCreated(folderStructure: List<BookmarkFolderItem>) {
        viewState.postValue(viewState.value?.copy(folderStructure = folderStructure))
    }

    fun onItemSelected(position: Int, bookmarkFolder: BookmarkFolder) {
        val folderStructure = viewState.value?.folderStructure?.toMutableList() ?: mutableListOf()
        folderStructure[lastPosition] = folderStructure[lastPosition].copy(isSelected = false)
        folderStructure[position] = folderStructure[position].copy(isSelected = true)

        viewState.postValue(viewState.value?.copy(folderStructure = folderStructure, selectedBookmarkFolder = bookmarkFolder))

        lastPosition = position
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class BookmarkFoldersViewModelFactory @Inject constructor(
    private val bookmarkFoldersRepository: Provider<BookmarkFoldersRepository>,
    private val bookmarkFoldersDao: Provider<BookmarkFoldersDao>,
    private val dispatcherProvider: Provider<DispatcherProvider>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BookmarkFoldersViewModel::class.java) -> (
                    BookmarkFoldersViewModel(
                        bookmarkFoldersRepository.get(),
                        bookmarkFoldersDao.get(),
                        dispatcherProvider.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}
