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

package com.duckduckgo.downloads.store

import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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
class DefaultDownloadsRepository @Inject constructor(
    private val downloadsDatabase: DownloadsDatabase
) : DownloadsRepository {

    override suspend fun insert(downloadItem: DownloadItem): Long {
        return downloadsDatabase.downloadsDao().insert(downloadItem.mapToDownloadEntity())
    }

    override suspend fun insertAll(downloadItems: List<DownloadItem>) {
        downloadsDatabase.downloadsDao().insertAll(downloadItems.mapToDownloadEntities())
    }

    override suspend fun update(downloadId: Long, downloadStatus: Int, contentLength: Long) {
        downloadsDatabase.downloadsDao().update(downloadId, downloadStatus, contentLength)
    }

    override suspend fun update(fileName: String, downloadStatus: Int, contentLength: Long) {
        downloadsDatabase.downloadsDao().update(fileName, downloadStatus, contentLength)
    }

    override suspend fun delete(id: Long) {
        downloadsDatabase.downloadsDao().delete(id)
    }

    override suspend fun delete(downloadIdList: List<Long>) {
        downloadsDao.delete(downloadIdList)
    }

    override suspend fun deleteAll() {
        downloadsDatabase.downloadsDao().delete()
    }

    override suspend fun getDownloads(): List<DownloadItem> {
        return downloadsDatabase.downloadsDao().getDownloads().mapToDownloadItems()
    }

    override suspend fun getDownloadItem(downloadId: Long): DownloadItem {
        return downloadsDatabase.downloadsDao().getDownloadItem(downloadId).mapToDownloadItem()
    }

    override fun getDownloadsAsFlow(): Flow<List<DownloadItem>> {
        return downloadsDatabase.downloadsDao().getDownloadsAsFlow().distinctUntilChanged().map { it.mapToDownloadItems() }
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
