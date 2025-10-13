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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.downloads.DownloadViewItem.Empty
import com.duckduckgo.app.downloads.DownloadViewItem.Header
import com.duckduckgo.app.downloads.DownloadViewItem.Item
import com.duckduckgo.app.downloads.DownloadViewItem.NotifyMe
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.CancelDownload
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.DisplayMessage
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.DisplayUndoMessage
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.OpenFile
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.ShareFile
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.RealTimeDiffFormatter
import com.duckduckgo.common.utils.formatters.time.TimeDiffFormatter
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.store.DownloadStatus.FINISHED
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.duckduckgo.common.utils.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class DownloadsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDownloadsRepository: DownloadsRepository = mock()

    private val context: Context = mock()

    private val testee: DownloadsViewModel by lazy {
        val model =
            DownloadsViewModel(
                FakeTimeDiffFormatter(TODAY, RealTimeDiffFormatter(context)),
                mockDownloadsRepository,
                coroutineRule.testDispatcherProvider,
            )
        model
    }

    @Test
    fun whenNoDownloadsAndVisibilityChangedCalledWithValueFalseThenViewStateEmittedWithEmptyItem() = runTest {
        val visible = false
        val list = emptyList<DownloadItem>()
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))
        testee.onItemVisibilityChanged(visible)

        testee.viewState.test {
            val items = awaitItem().downloadItems
            assertEquals(1, items.size)
            assertTrue(items[0] is Empty)
        }
    }

    @Test
    fun whenNoDownloadsAndVisibilityChangedCalledWithValueTrueThenViewStateEmittedWithNotifyMeAndEmptyItems() = runTest {
        val visible = true
        val list = emptyList<DownloadItem>()
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))
        testee.onItemVisibilityChanged(visible)

        testee.viewState.test {
            val items = awaitItem().downloadItems
            assertEquals(2, items.size)
            assertTrue(items[0] is NotifyMe)
            assertTrue(items[1] is Empty)
        }
    }

    @Test
    fun whenOneDownloadAndVisibilityChangedCalledWithValueFalseThenViewStateEmittedWithHeaderAndOneItem() = runTest {
        val visible = false
        val list = listOf(oneItem())
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))
        testee.onItemVisibilityChanged(visible)

        testee.viewState.test {
            val items = awaitItem().downloadItems
            assertEquals(2, items.size)
            assertTrue(items[0] is Header)
            assertTrue(items[1] is Item)
            assertEquals(list[0].fileName, (items[1] as Item).downloadItem.fileName)
        }
    }

    @Test
    fun whenOneDownloadAndVisibilityChangedCalledWithValueTrueThenViewStateEmittedWithNotifyMeAndHeaderAndOneItem() = runTest {
        val visible = true
        val list = listOf(oneItem())
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))
        testee.onItemVisibilityChanged(visible)

        testee.viewState.test {
            val items = awaitItem().downloadItems
            assertEquals(3, items.size)
            assertTrue(items[0] is NotifyMe)
            assertTrue(items[1] is Header)
            assertTrue(items[2] is Item)
            assertEquals(list[0].fileName, (items[2] as Item).downloadItem.fileName)
        }
    }

    @Test
    fun whenMultipleDownloadsAndVisibilityChangedCalledWithValueFalseThenViewStateEmittedWithMultipleItemsAndHeaders() = runTest {
        val visible = false
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val today = LocalDateTime.parse(TODAY, formatter)
        val yesterday = today.minusDays(1)
        val sometimeDuringPastWeek = today.minusDays(6)
        val sometimeDuringPastMonth = today.minusDays(20)
        val sometimeBeforePastMonth = today.minusDays(40)
        val sometimeInThePreviousYear = today.minusDays(370)

        val downloadList = listOf(
            oneItem().copy(downloadId = 1L, createdAt = today.toString()),
            oneItem().copy(downloadId = 2L, createdAt = yesterday.toString()),
            oneItem().copy(downloadId = 3L, createdAt = sometimeDuringPastWeek.toString()),
            oneItem().copy(downloadId = 4L, createdAt = sometimeDuringPastMonth.toString()),
            oneItem().copy(downloadId = 5L, createdAt = sometimeBeforePastMonth.toString()),
            oneItem().copy(downloadId = 6L, createdAt = sometimeInThePreviousYear.toString()),
        )

        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(downloadList))
        // we need this because we passed in a mock context to the RealTimeDiffFormatter
        whenever(context.getString(CommonR.string.common_Today)).thenReturn("Today")
        whenever(context.getString(CommonR.string.common_Yesterday)).thenReturn("Yesterday")
        whenever(context.getString(CommonR.string.common_PastWeek)).thenReturn("Past Week")
        whenever(context.getString(CommonR.string.common_PastMonth)).thenReturn("Past Month")
        testee.onItemVisibilityChanged(visible)

        testee.viewState.test {
            val items = awaitItem().downloadItems
            assertEquals(12, items.size)

            assertTrue(items[0] is Header)
            assertTrue((items[0] as Header).text == "Today")
            assertTrue(items[1] is Item)
            assertEquals(downloadList[0].downloadId, (items[1] as Item).downloadItem.downloadId)

            assertTrue(items[2] is Header)
            assertTrue((items[2] as Header).text == "Yesterday")
            assertTrue(items[3] is Item)
            assertEquals(downloadList[1].downloadId, (items[3] as Item).downloadItem.downloadId)

            assertTrue(items[4] is Header)
            assertTrue((items[4] as Header).text == "Past Week")
            assertTrue(items[5] is Item)
            assertEquals(downloadList[2].downloadId, (items[5] as Item).downloadItem.downloadId)

            assertTrue(items[6] is Header)
            assertTrue((items[6] as Header).text == "Past Month")
            assertTrue(items[7] is Item)
            assertEquals(downloadList[3].downloadId, (items[7] as Item).downloadItem.downloadId)

            assertTrue(items[8] is Header)
            assertTrue((items[8] as Header).text.lowercase() == sometimeBeforePastMonth.month.name.lowercase())
            assertTrue(items[9] is Item)
            assertEquals(downloadList[4].downloadId, (items[9] as Item).downloadItem.downloadId)

            assertTrue(items[10] is Header)
            assertTrue((items[10] as Header).text == sometimeInThePreviousYear.year.toString())
            assertTrue(items[11] is Item)
            assertEquals(downloadList[5].downloadId, (items[11] as Item).downloadItem.downloadId)
        }
    }

    @Test
    fun whenDeleteAllCalledThenRepositoryDeleteAllCalledAndMessageCommandSent() = runTest {
        val itemsToDelete = listOf(oneItem())
        whenever(mockDownloadsRepository.getDownloads()).thenReturn(itemsToDelete)

        testee.deleteAllDownloadedItems()

        verify(mockDownloadsRepository).deleteAll()
        testee.commands().test {
            assertEquals(
                DisplayUndoMessage(messageId = R.string.downloadsAllFilesDeletedMessage, items = itemsToDelete),
                awaitItem(),
            )
        }
    }

    @Test
    fun whenDeleteCalledThenRepositoryDeleteCalledAndMessageCommandSent() = runTest {
        val item = oneItem()

        testee.delete(item)

        verify(mockDownloadsRepository).delete(item.downloadId)
        testee.commands().test {
            assertEquals(
                DisplayMessage(R.string.downloadsFileNotFoundErrorMessage),
                awaitItem(),
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

    @Test
    fun whenOnQueryTextChangeThenViewStateEmittedWithTwoFilteredItems() = runTest {
        val list = listOf(oneItem(), otherItem())
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))
        testee.onItemVisibilityChanged(false)

        testee.onQueryTextChange("other")

        testee.viewState.test {
            val items = awaitItem()
            assertEquals(3, items.downloadItems.size)
            assertEquals(2, items.filteredItems.size)
            assertTrue(items.filteredItems[0] is Header)
            assertTrue(items.filteredItems[1] is Item)
            assertEquals(list[1].fileName, (items.filteredItems[1] as Item).downloadItem.fileName)
        }
    }

    @Test
    fun whenOnQueryTextChangeThenViewStateEmittedWithZeroFilteredItems() = runTest {
        val list = listOf(oneItem(), otherItem())
        whenever(mockDownloadsRepository.getDownloadsAsFlow()).thenReturn(flowOf(list))
        testee.onItemVisibilityChanged(false)

        testee.onQueryTextChange("text_that_does_not_exist_in_list")

        testee.viewState.test {
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
                awaitItem(),
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
                awaitItem(),
            )
        }
    }

    @Test
    fun whenDeleteItemClickedThenItemDeletedAndMessageCommandSent() = runTest {
        val item = oneItem()

        testee.onDeleteItemClicked(item)

        verify(mockDownloadsRepository).delete(item.downloadId)
        testee.commands().test {
            assertEquals(
                DisplayUndoMessage(
                    messageId = R.string.downloadsFileDeletedMessage,
                    arg = item.fileName,
                    items = listOf(item),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun whenCancelItemClickedThenItemDeletedAndCancelDownloadCommandSent() = runTest {
        val item = oneItem()

        testee.onCancelItemClicked(item)

        verify(mockDownloadsRepository).delete(item.downloadId)
        testee.commands().test {
            assertEquals(
                CancelDownload(item),
                awaitItem(),
            )
        }
    }

    @Test
    fun whenRemoveFromDownloadManagerThenRemoveIt() = runTest {
        val downloadId = 1L

        testee.removeFromDownloadManager(downloadId)

        verify(mockDownloadsRepository).delete(downloadId)
    }

    private fun oneItem() =
        DownloadItem(
            downloadId = 10L,
            downloadStatus = FINISHED,
            fileName = "file.jpg",
            contentLength = 100L,
            createdAt = "2022-02-21T10:56:22",
            filePath = "/",
        )

    private fun otherItem() =
        DownloadItem(
            downloadId = 20L,
            downloadStatus = FINISHED,
            fileName = "other-file.jpg",
            contentLength = 120L,
            createdAt = "2022-02-21T10:56:22",
            filePath = "/",
        )

    companion object {
        private const val TODAY = "2022-11-02T13:00:00"
    }
}

private class FakeTimeDiffFormatter(
    today: String,
    private val realTimeDiffFormatter: RealTimeDiffFormatter,
) : TimeDiffFormatter by realTimeDiffFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val formattedToday = LocalDateTime.parse(today, formatter)

    override fun formatTimePassed(endLocalDateTime: LocalDateTime, startLocalDateTime: LocalDateTime): String {
        return realTimeDiffFormatter.formatTimePassed(
            endLocalDateTime = formattedToday,
            startLocalDateTime = startLocalDateTime,
        )
    }

    override fun formatTimePassedInDays(endLocalDateTime: LocalDateTime, startLocalDateTime: LocalDateTime): String {
        return realTimeDiffFormatter.formatTimePassedInDays(
            endLocalDateTime = formattedToday,
            startLocalDateTime = startLocalDateTime,
        )
    }

    override fun formatTimePassedInDaysWeeksMonthsYears(endLocalDateTime: LocalDateTime, startLocalDateTime: LocalDateTime): String {
        return realTimeDiffFormatter.formatTimePassedInDaysWeeksMonthsYears(
            endLocalDateTime = formattedToday,
            startLocalDateTime = startLocalDateTime,
        )
    }
}
