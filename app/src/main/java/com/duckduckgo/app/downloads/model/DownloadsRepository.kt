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

package com.duckduckgo.app.downloads.model

import com.duckduckgo.app.downloads.db.DownloadEntity
import com.duckduckgo.app.downloads.db.DownloadsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface DownloadsRepository {
    suspend fun insert(downloadItem: DownloadItem): Long
    suspend fun insertAll(downloadItems: List<DownloadItem>)
    suspend fun update(downloadId: Long, downloadStatus: Int, contentLength: Long)
    suspend fun update(fileName: String, downloadStatus: Int, contentLength: Long)
    suspend fun delete(id: Long)
    suspend fun delete(downloadIdList: List<Long>)
    suspend fun deleteAll()
    suspend fun getDownloads(): List<DownloadItem>
    suspend fun getDownloadItem(downloadId: Long): DownloadItem
    fun getDownloadsAsFlow(): Flow<List<DownloadItem>>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultDownloadsRepository@Inject constructor(
    private val downloadsDao: DownloadsDao
) : DownloadsRepository {

    override suspend fun insert(downloadItem: DownloadItem): Long {
        return downloadsDao.insert(downloadItem.mapToDownloadEntity())
    }

    override suspend fun insertAll(downloadItems: List<DownloadItem>) {
        downloadsDao.insertAll(downloadItems.mapToDownloadEntities())
    }

    override suspend fun update(downloadId: Long, downloadStatus: Int, contentLength: Long) {
        downloadsDao.update(downloadId, downloadStatus, contentLength)
    }

    override suspend fun update(fileName: String, downloadStatus: Int, contentLength: Long) {
        downloadsDao.update(fileName, downloadStatus, contentLength)
    }

    override suspend fun delete(id: Long) {
        downloadsDao.delete(id)
    }

    override suspend fun delete(downloadIdList: List<Long>) {
        downloadsDao.delete(downloadIdList)
    }

    override suspend fun deleteAll() {
        downloadsDao.delete()
    }

    override suspend fun getDownloads(): List<DownloadItem> {
        return downloadsDao.getDownloads().mapToDownloadItems()
    }

    override suspend fun getDownloadItem(downloadId: Long): DownloadItem {
        return downloadsDao.getDownloadItem(downloadId).mapToDownloadItem()
    }

    override fun getDownloadsAsFlow(): Flow<List<DownloadItem>> {
        return downloadsDao.getDownloadsAsFlow().distinctUntilChanged().map { it.mapToDownloadItems() }
    }

    private fun DownloadEntity.mapToDownloadItem(): DownloadItem =
        DownloadItem(
            id = this.id,
            downloadId = this.downloadId,
            downloadStatus = this.downloadStatus,
            fileName = this.fileName,
            contentLength = this.contentLength,
            filePath = this.filePath,
            createdAt = this.createdAt,
        )

    private fun List<DownloadEntity>.mapToDownloadItems(): List<DownloadItem> =
        this.map { it.mapToDownloadItem() }

    private fun DownloadItem.mapToDownloadEntity(): DownloadEntity =
        DownloadEntity(
            id = this.id,
            downloadId = this.downloadId,
            downloadStatus = this.downloadStatus,
            fileName = this.fileName,
            contentLength = this.contentLength,
            filePath = this.filePath,
            createdAt = this.createdAt,
        )

    private fun List<DownloadItem>.mapToDownloadEntities(): List<DownloadEntity> =
        this.map { it.mapToDownloadEntity() }
}
