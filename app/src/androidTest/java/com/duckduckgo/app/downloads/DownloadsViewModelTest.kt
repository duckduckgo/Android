/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.downloads

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.downloads.DownloadViewItem.Empty
import com.duckduckgo.app.downloads.DownloadViewItem.Header
import com.duckduckgo.app.downloads.DownloadViewItem.Item
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.DisplayMessage
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.DisplayUndoMessage
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.OpenFile
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.ShareFile
import com.duckduckgo.app.downloads.model.DownloadItem
import com.duckduckgo.app.downloads.model.DownloadStatus.FINISHED
import com.duckduckgo.app.downloads.model.DownloadsRepository
import com.duckduckgo.app.global.formatters.time.TimeDiffFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class DownloadsViewModelTest {

    @get:Rule var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val schedulers = InstantSchedulersRule()

    @ExperimentalCoroutinesApi @get:Rule var coroutineRule = CoroutineTestRule()

    @Mock private lateinit var mockDownloadsRepository: DownloadsRepository

    private val testee: DownloadsViewModel by lazy {
        val model =
            DownloadsViewModel(
                TimeDiffFormatter(InstrumentationRegistry.getInstrumentation().targetContext),
                mockDownloadsRepository,
                coroutineRule.testDispatcherProvider
            )
        model
    }

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
    }

    @Test fun whenDownloadsCalledThenViewStateEmitted() = runTest {
        val list = listOf(oneItem())
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))

        testee.downloads()

        testee.viewState().test {
            val items = awaitItem().downloadItems
            assertEquals(2, items.size)
            assertTrue(items[0] is Header)
            assertTrue(items[1] is Item)
            assertEquals(list[0].fileName, (items[1] as Item).downloadItem.fileName)
        }
    }

    @Test fun whenDeleteAllCalledThenRepositoryDeleteAllCalledAndMessageCommandSent() = runTest {
        val itemsToDelete = listOf(oneItem())
        whenever(mockDownloadsRepository.getDownloads()).thenReturn(itemsToDelete)

        testee.deleteAllDownloadedItems()

        verify(mockDownloadsRepository).deleteAll()
        testee.commands().test {
            assertEquals(
                DisplayUndoMessage(messageId = R.string.downloadsAllFilesDeletedMessage, items = itemsToDelete),
                awaitItem()
            )
        }
    }

    @Test
    fun whenDeleteCalledThenRepositoryDeleteCalledAndMessageCommandSent() = runTest {
        val item = oneItem()

        testee.delete(item)

        verify(mockDownloadsRepository).delete(item.id)
        testee.commands().test {
            assertEquals(
                DisplayMessage(R.string.downloadsFileNotFoundErrorMessage),
                awaitItem()
            )
        }
    }

    @Test
    fun whenInsertCalledThenRepositoryInsertAllCalled() = runTest {
        val firstItem = oneItem()
        val secondItem = otherItem()

        testee.insert(listOf(firstItem, secondItem))

        verify(mockDownloadsRepository).insertAll(listOf(firstItem, secondItem))
    }

    @Test fun whenOnQueryTextChangeThenViewStateEmittedWithTwoFilteredItems() = runTest {
        val list = listOf(oneItem(), otherItem())
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))
        testee.downloads()

        testee.onQueryTextChange("other")

        testee.viewState().test {
            val items = awaitItem()
            assertEquals(3, items.downloadItems.size)
            assertEquals(2, items.filteredItems.size)
            assertTrue(items.filteredItems[0] is Header)
            assertTrue(items.filteredItems[1] is Item)
            assertEquals(list[1].fileName, (items.filteredItems[1] as Item).downloadItem.fileName)
        }
    }

    @Test fun whenOnQueryTextChangeThenViewStateEmittedWithZeroFilteredItems() = runTest {
        val list = listOf(oneItem(), otherItem())
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))
        testee.downloads()

        testee.onQueryTextChange("text_that_does_not_exist_in_list")

        testee.viewState().test {
            val items = awaitItem()
            assertEquals(3, items.downloadItems.size)
            assertEquals(1, items.filteredItems.size)
            assertTrue(items.filteredItems[0] is Empty)
        }
    }

    @Test
    fun whenItemClickedThenOpenFileCommandSent() = runTest {
        val item = oneItem()

        testee.onItemClicked(item)

        testee.commands().test {
            assertEquals(
                OpenFile(item),
                awaitItem()
            )
        }
    }

    @Test
    fun whenItemShareClickedThenShareFileCommandSent() = runTest {
        val item = oneItem()

        testee.onShareItemClicked(item)

        testee.commands().test {
            assertEquals(
                ShareFile(item),
                awaitItem()
            )
        }
    }

    @Test
    fun whenDeleteItemClickedThenItemDeletedAndMessageCommandSent() = runTest {
        val item = oneItem()

        testee.onDeleteItemClicked(item)

        verify(mockDownloadsRepository).delete(item.id)
        testee.commands().test {
            assertEquals(
                DisplayUndoMessage(
                    messageId = R.string.downloadsFileDeletedMessage,
                    arg = item.fileName,
                    items = listOf(item)
                ),
                awaitItem()
            )
        }
    }

    private fun oneItem() =
        DownloadItem(
            id = 1L,
            downloadId = 10L,
            downloadStatus = FINISHED,
            fileName = "file.jpg",
            contentLength = 100L,
            createdAt = "2022-02-21T10:56:22",
            filePath = "/"
        )

    private fun otherItem() =
        DownloadItem(
            id = 2L,
            downloadId = 20L,
            downloadStatus = FINISHED,
            fileName = "other-file.jpg",
            contentLength = 120L,
            createdAt = "2022-02-21T10:56:22",
            filePath = "/"
        )
}
