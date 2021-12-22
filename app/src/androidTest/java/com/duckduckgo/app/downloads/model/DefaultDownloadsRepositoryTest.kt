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

package com.duckduckgo.app.downloads.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.downloads.db.DownloadEntity
import com.duckduckgo.app.downloads.db.DownloadsDao
import com.duckduckgo.app.downloads.model.DownloadStatus.FINISHED
import com.duckduckgo.app.downloads.model.DownloadStatus.STARTED
import com.duckduckgo.app.global.db.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DefaultDownloadsRepositoryTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var downloadsDao: DownloadsDao
    private lateinit var repository: DownloadsRepository

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        downloadsDao = db.downloadsDao()
        repository = DefaultDownloadsRepository(downloadsDao)
    }

    @Test
    fun whenInsertDownloadItemThenReturnId() = runTest {
        val item = oneItem()

        val id = repository.insert(item)

        assertEquals(1, id)
    }

    @Test
    fun whenInsertDownloadItemThenPopulateDB() = runTest {
        val item = oneItem()
        val expectedEntity = oneEntity()

        repository.insert(item)

        assertEquals(listOf(expectedEntity), downloadsDao.getDownloads())
    }

    @Test
    fun whenInsertAllDownloadItemsThenPopulateDB() = runTest {
        val firstItem = oneItem()
        val secondItem = otherItem()
        val expectedFirstEntity = oneEntity()
        val expectedSecondEntity = otherEntity()

        repository.insertAll(listOf(firstItem, secondItem))

        val result = downloadsDao.getDownloads()
        assertTrue(result.size == 2)
        assertTrue(result.contains(expectedFirstEntity))
        assertTrue(result.contains(expectedSecondEntity))
    }

    @Test
    fun whenUpdateDownloadItemByIdWithDownloadStatusAndContentLengthThenUpdateInDB() = runTest {
        val item = oneItem()
        repository.insert(item)

        val updatedStatus = FINISHED
        val updatedContentLength = 1111111L
        repository.update(downloadId = item.downloadId, downloadStatus = updatedStatus, contentLength = updatedContentLength)

        val items = downloadsDao.getDownloads()
        assertTrue(items.size == 1)
        assertEquals(
            DownloadEntity(
                id = item.id,
                downloadId = item.downloadId,
                downloadStatus = updatedStatus,
                fileName = item.fileName,
                contentLength = updatedContentLength,
                filePath = item.filePath,
                createdAt = item.createdAt
            ),
            items.first()
        )
    }

    @Test
    fun whenUpdateDownloadItemByFileNameWithDownloadStatusAndContentLengthThenUpdateInDB() = runTest {
        val item = oneItem().copy(downloadId = 0L)
        repository.insert(item)

        val updatedStatus = FINISHED
        val updatedContentLength = 1111111L
        repository.update(fileName = item.fileName, downloadStatus = updatedStatus, contentLength = updatedContentLength)

        val items = downloadsDao.getDownloads()
        assertTrue(items.size == 1)
        assertEquals(
            DownloadEntity(
                id = item.id,
                downloadId = item.downloadId,
                downloadStatus = updatedStatus,
                fileName = item.fileName,
                contentLength = updatedContentLength,
                filePath = item.filePath,
                createdAt = item.createdAt
            ),
            items.first()
        )
    }

    @Test
    fun whenDeleteDownloadItemThenRemoveFromDB() = runTest {
        val item = oneItem()
        repository.insert(item)

        repository.delete(item.id)

        assertTrue(downloadsDao.getDownloads().isEmpty())
    }

    @Test
    fun whenDeleteAllDownloadItemsThenRemoveFromDB() = runTest {
        val firstItem = oneItem()
        val secondItem = otherItem()
        repository.insert(firstItem)
        repository.insert(secondItem)

        repository.deleteAll()

        assertTrue(downloadsDao.getDownloads().isEmpty())
    }

    @Test
    fun whenGetDownloadItemCalledThenItemReturnedFromDB() = runTest {
        val item = oneItem()
        repository.insert(item)

        val result = repository.getDownloadItem(item.downloadId)

        assertEquals(item, result)
    }

    @Test
    fun whenGetDownloadItemsCalledThenItemsReturnedFromDB() = runTest {
        val firstItem = oneItem()
        val secondItem = otherItem()
        repository.insert(firstItem)
        repository.insert(secondItem)

        val result = repository.getDownloads()

        assertTrue(result.size == 2)
        assertTrue(result.contains(firstItem))
        assertTrue(result.contains(secondItem))
    }

    private fun oneItem() = DownloadItem(
        id = 1L,
        downloadId = 10L,
        downloadStatus = STARTED,
        fileName = "file.jpg",
        contentLength = 100L,
        createdAt = "2022-02-04",
        filePath = "/"
    )

    private fun otherItem() = DownloadItem(
        id = 2L,
        downloadId = 20L,
        downloadStatus = STARTED,
        fileName = "other-file.jpg",
        contentLength = 120L,
        createdAt = "2022-02-06",
        filePath = "/"
    )

    private fun oneEntity() = DownloadEntity(
        id = 1L,
        downloadId = 10L,
        downloadStatus = STARTED,
        fileName = "file.jpg",
        contentLength = 100L,
        createdAt = "2022-02-04",
        filePath = "/"
    )

    private fun otherEntity() = DownloadEntity(
        id = 2L,
        downloadId = 20L,
        downloadStatus = STARTED,
        fileName = "other-file.jpg",
        contentLength = 120L,
        createdAt = "2022-02-06",
        filePath = "/"
    )
}
