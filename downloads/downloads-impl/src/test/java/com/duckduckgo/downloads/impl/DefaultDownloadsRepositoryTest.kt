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

package com.duckduckgo.downloads.impl

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.store.DownloadEntity
import com.duckduckgo.downloads.store.DownloadStatus.FINISHED
import com.duckduckgo.downloads.store.DownloadStatus.STARTED
import com.duckduckgo.downloads.store.DownloadsDao
import com.duckduckgo.downloads.store.DownloadsDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class DefaultDownloadsRepositoryTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockDb: DownloadsDatabase = mock()
    private val mockDao: DownloadsDao = mock()
    private lateinit var repository: DownloadsRepository

    @Before
    fun before() {
        whenever(mockDb.downloadsDao()).thenReturn(mockDao)

        repository = DefaultDownloadsRepository(mockDb)
    }

    @Test
    fun whenInsertDownloadItemThenInsertCalled() = runTest {
        val item = oneItem()
        val entity = oneEntity()

        repository.insert(item)

        verify(mockDb.downloadsDao()).insert(entity)
    }

    @Test
    fun whenInsertAllDownloadItemsThenInsertAllCalled() = runTest {
        val firstItem = oneItem()
        val secondItem = otherItem()
        val firstEntity = oneEntity()
        val secondEntity = otherEntity()

        repository.insertAll(listOf(firstItem, secondItem))

        verify(mockDb.downloadsDao()).insertAll(listOf(firstEntity, secondEntity))
    }

    @Test
    fun whenUpdateDownloadItemByIdWithDownloadStatusAndContentLengthThenUpdateCalledWithSameParams() =
        runTest {
            val item = oneItem()
            val updatedStatus = FINISHED
            val updatedContentLength = 1111111L

            repository.update(
                downloadId = item.downloadId,
                downloadStatus = updatedStatus,
                contentLength = updatedContentLength
            )

            verify(mockDb.downloadsDao()).update(
                downloadId = item.downloadId,
                downloadStatus = updatedStatus,
                contentLength = updatedContentLength
            )
        }

    @Test
    fun whenUpdateDownloadItemByFileNameWithDownloadStatusAndContentLengthThenUpdateCalledWithSameParams() =
        runTest {
            val item = oneItem().copy(downloadId = 0L)
            val updatedStatus = FINISHED
            val updatedContentLength = 1111111L

            repository.update(
                fileName = item.fileName,
                downloadStatus = updatedStatus,
                contentLength = updatedContentLength
            )

            verify(mockDb.downloadsDao()).update(
                fileName = item.fileName,
                downloadStatus = updatedStatus,
                contentLength = updatedContentLength
            )
        }

    @Test
    fun whenDeleteDownloadItemThenDeleteCalled() = runTest {
        val item = oneItem()

        repository.delete(item.id)

        verify(mockDb.downloadsDao()).delete(item.id)
    }

    @Test
    fun whenDeleteListOfDownloadItemsThenDeleteCalled() = runTest {
        val firstItem = oneItem()
        val secondItem = otherItem()

        repository.delete(listOf(firstItem.downloadId, secondItem.downloadId))

        verify(mockDb.downloadsDao()).delete(listOf(firstItem.downloadId, secondItem.downloadId))
    }

    @Test
    fun whenDeleteAllDownloadItemsThenDeleteWithNoParamsCalled() = runTest {
        repository.deleteAll()

        verify(mockDb.downloadsDao()).delete()
    }

    @Test
    fun whenGetDownloadItemCalledThenGetDownloadItemCalled() = runTest {
        val item = oneItem()
        val entity = oneEntity()
        whenever(mockDb.downloadsDao().getDownloadItem(item.downloadId)).thenReturn(entity)

        repository.getDownloadItem(item.downloadId)

        verify(mockDb.downloadsDao()).getDownloadItem(item.downloadId)
    }

    @Test
    fun whenGetDownloadItemsCalledThenGetDownloadsCalled() = runTest {
        whenever(mockDb.downloadsDao().getDownloads()).thenReturn(listOf(oneEntity()))

        repository.getDownloads()

        verify(mockDb.downloadsDao()).getDownloads()
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
