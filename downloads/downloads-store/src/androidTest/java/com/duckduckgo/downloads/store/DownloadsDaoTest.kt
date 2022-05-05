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

package com.duckduckgo.downloads.store

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.downloads.store.DownloadStatus.FINISHED
import com.duckduckgo.downloads.store.DownloadStatus.STARTED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DownloadsDaoTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: DownloadsDatabase
    private lateinit var dao: DownloadsDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DownloadsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.downloadsDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenDownloadItemAddedThenItIsInList() = runTest {
        val downloadItem = oneItem()

        dao.insert(downloadItem)

        val list = dao.getDownloads()
        assertEquals(listOf(downloadItem), list)
    }

    @Test
    fun whenDownloadItemsAddedThenTheyAreInTheList() = runTest {
        val downloadItems = listOf(
            oneItem(),
            otherItem()
        )

        dao.insertAll(downloadItems)

        val list = dao.getDownloads()
        assertEquals(downloadItems, list)
    }

    @Test
    fun whenDownloadItemUpdatedByIdThenItIsInListWithNewValue() = runTest {
        val downloadItem = oneItem().copy(downloadStatus = STARTED, contentLength = 0L)
        val updatedStatus = FINISHED
        val updatedContentLength = 11111L
        dao.insert(downloadItem)

        dao.update(
            downloadId = downloadItem.downloadId,
            downloadStatus = updatedStatus,
            contentLength = updatedContentLength
        )

        val list = dao.getDownloads()
        val actualItem = list.first()
        assertEquals(updatedStatus, actualItem.downloadStatus)
        assertEquals(updatedContentLength, actualItem.contentLength)
    }

    @Test
    fun whenDownloadItemUpdatedByFileNameThenItIsInListWithNewValue() = runTest {
        val downloadItem = oneItem().copy(downloadStatus = STARTED, contentLength = 0L, downloadId = 0L)
        val updatedStatus = FINISHED
        val updatedContentLength = 11111L
        dao.insert(downloadItem)

        dao.update(
            fileName = downloadItem.fileName,
            downloadStatus = updatedStatus,
            contentLength = updatedContentLength
        )

        val list = dao.getDownloads()
        val actualItem = list.first()
        assertEquals(updatedStatus, actualItem.downloadStatus)
        assertEquals(updatedContentLength, actualItem.contentLength)
    }

    @Test
    fun whenDownloadItemDeletedThenItIsNoLongerInTheList() = runTest {
        val downloadItem = oneItem()
        dao.insert(downloadItem)

        dao.delete(downloadItem.id)

        val list = dao.getDownloads()
        assertTrue(list.isEmpty())
    }

    @Test
    fun whenDownloadItemsDeletedThenTheyAreNoLongerInTheList() = runTest {
        val downloadItems = listOf(
            oneItem(),
            otherItem()
        )
        dao.insertAll(downloadItems)

        dao.delete()

        val list = dao.getDownloads()
        assertTrue(list.isEmpty())
    }

    @Test
    fun whenDownloadItemsRetrievedThenTheCorrectItemIsReturned() = runTest {
        val itemToRetrieve = oneItem()
        val downloadItems = listOf(
            otherItem(),
            itemToRetrieve
        )
        dao.insertAll(downloadItems)

        val actualItem = dao.getDownloadItem(itemToRetrieve.downloadId)

        assertEquals(itemToRetrieve, actualItem)
    }

    private fun oneItem() =
        DownloadEntity(
            id = 1L,
            downloadId = 10L,
            downloadStatus = FINISHED,
            fileName = "file.jpg",
            contentLength = 100L,
            createdAt = "2022-02-21T10:56:22",
            filePath = "/"
        )

    private fun otherItem() =
        DownloadEntity(
            id = 2L,
            downloadId = 20L,
            downloadStatus = FINISHED,
            fileName = "other-file.jpg",
            contentLength = 120L,
            createdAt = "2022-02-21T10:56:22",
            filePath = "/"
        )
}
