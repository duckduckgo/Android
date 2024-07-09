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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import junit.framework.TestCase.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*

class BookmarkFoldersViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val savedSitesRepository: SavedSitesRepository = mock()

    private val viewStateObserver: Observer<BookmarkFoldersViewModel.ViewState> = mock()
    private val commandObserver: Observer<BookmarkFoldersViewModel.Command> = mock()

    private val viewStateCaptor = argumentCaptor<BookmarkFoldersViewModel.ViewState>()
    private val commandCaptor = argumentCaptor<BookmarkFoldersViewModel.Command>()

    private val folderStructure = mutableListOf(
        BookmarkFolderItem(1, BookmarkFolder("folder1", "folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp"), true),
        BookmarkFolderItem(1, BookmarkFolder("folder2", "a folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp"), false),
    )

    private val testee: BookmarkFoldersViewModel by lazy {
        val model = BookmarkFoldersViewModel(savedSitesRepository, coroutineRule.testDispatcherProvider)
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() = runTest {
        whenever(savedSitesRepository.getFolderTree(anyString(), any())).thenReturn(folderStructure)
    }

    @Test
    fun whenFetchBookmarkFoldersThenCallRepoAndUpdateViewState() = runTest {
        val selectedFolderId = SavedSitesNames.BOOKMARKS_ROOT
        val rootFolderName = "Bookmarks"
        val folder = BookmarkFolder("folder2", "a folder", "folder1", 0, 0, "timestamp")

        testee.fetchBookmarkFolders(selectedFolderId, folder)

        verify(savedSitesRepository).getFolderTree(selectedFolderId, folder)
        verify(viewStateObserver, times(2)).onChanged(viewStateCaptor.capture())

        assertEquals(emptyList<BookmarkFolderItem>(), viewStateCaptor.allValues[0].folderStructure)
        assertEquals(folderStructure, viewStateCaptor.allValues[1].folderStructure)
    }

    @Test
    fun whenItemSelectedThenIssueSelectFolderCommand() = runTest {
        val folder = BookmarkFolder("folder2", "a folder", "folder1", 0, 0, "timestamp")

        testee.onItemSelected(folder)

        verify(commandObserver).onChanged(commandCaptor.capture())

        assertEquals(folder, (commandCaptor.lastValue as BookmarkFoldersViewModel.Command.SelectFolder).selectedBookmarkFolder)
    }

    @Test
    fun newFolderAddedThenCallRepoAndUpdateViewState() = runTest {
        val newFolder = BookmarkFolder("folder3", "new folder", "folder1", 0, 0, "timestamp")
        val selectedFolderId = SavedSitesNames.BOOKMARKS_ROOT

        testee.newFolderAdded(selectedFolderId, newFolder)
        folderStructure.add(BookmarkFolderItem(1, newFolder))

        verify(savedSitesRepository).getFolderTree(selectedFolderId, newFolder)
        verify(viewStateObserver, times(2)).onChanged(viewStateCaptor.capture())

        assertEquals(emptyList<BookmarkFolderItem>(), viewStateCaptor.allValues[0].folderStructure)
        assertEquals(folderStructure, viewStateCaptor.allValues[1].folderStructure)
    }
}
