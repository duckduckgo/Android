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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.BookmarkFolderItem
import com.duckduckgo.app.bookmarks.model.BookmarksRepository
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.*
import junit.framework.TestCase.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString

@ExperimentalCoroutinesApi
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

    private val bookmarksRepository: BookmarksRepository = mock()

    private val viewStateObserver: Observer<BookmarkFoldersViewModel.ViewState> = mock()
    private val commandObserver: Observer<BookmarkFoldersViewModel.Command> = mock()

    private val viewStateCaptor: ArgumentCaptor<BookmarkFoldersViewModel.ViewState> = ArgumentCaptor.forClass(BookmarkFoldersViewModel.ViewState::class.java)
    private val commandCaptor: ArgumentCaptor<BookmarkFoldersViewModel.Command> = ArgumentCaptor.forClass(BookmarkFoldersViewModel.Command::class.java)

    private val folderStructure = listOf(
        BookmarkFolderItem(1, BookmarkFolder(1, "folder", 0), true),
        BookmarkFolderItem(1, BookmarkFolder(2, "a folder", 0), false)
    )

    private val testee: BookmarkFoldersViewModel by lazy {
        val model = BookmarkFoldersViewModel(bookmarksRepository, coroutineRule.testDispatcherProvider)
        model.viewState.observeForever(viewStateObserver)
        model.command.observeForever(commandObserver)
        model
    }

    @Before
    fun before() = coroutineRule.runBlocking {
        whenever(bookmarksRepository.getFlatFolderStructure(anyLong(), any(), anyString())).thenReturn(folderStructure)
    }

    @Test
    fun whenFetchBookmarkFoldersThenCallRepoAndUpdateViewState() = coroutineRule.runBlocking {
        val selectedFolderId = 0L
        val rootFolderName = "Bookmarks"
        val folder = BookmarkFolder(2, "a folder", 1)

        testee.fetchBookmarkFolders(selectedFolderId, rootFolderName, folder)

        verify(bookmarksRepository).getFlatFolderStructure(selectedFolderId, folder, rootFolderName)
        verify(viewStateObserver, times(2)).onChanged(viewStateCaptor.capture())

        assertEquals(emptyList<BookmarkFolderItem>(), viewStateCaptor.allValues[0].folderStructure)
        assertEquals(folderStructure, viewStateCaptor.allValues[1].folderStructure)
    }

    @Test
    fun whenItemSelectedThenIssueSelectFolderCommand() = coroutineRule.runBlocking {
        val folder = BookmarkFolder(2, "a folder", 1)

        testee.onItemSelected(folder)

        verify(commandObserver).onChanged(commandCaptor.capture())

        assertEquals(folder, (commandCaptor.value as BookmarkFoldersViewModel.Command.SelectFolder).selectedBookmarkFolder)
    }
}
