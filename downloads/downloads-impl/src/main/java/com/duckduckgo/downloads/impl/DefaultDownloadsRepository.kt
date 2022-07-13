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

import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.store.DownloadEntity
import com.duckduckgo.downloads.store.DownloadStatus
import com.duckduckgo.downloads.store.DownloadsDatabase
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultDownloadsRepository @Inject constructor(
    private val downloadsDatabase: DownloadsDatabase,
    private val urlFileDownloadCallManager: UrlFileDownloadCallManager,
) : DownloadsRepository {

    override suspend fun insert(downloadItem: DownloadItem): Long {
        return downloadsDatabase.downloadsDao().insert(downloadItem.mapToDownloadEntity())
    }

    override suspend fun insertAll(downloadItems: List<DownloadItem>) {
        downloadsDatabase.downloadsDao().insertAll(downloadItems.mapToDownloadEntities())
    }

    override suspend fun update(downloadId: Long, downloadStatus: Int, contentLength: Long) {
        downloadsDatabase.downloadsDao().update(downloadId, downloadStatus, contentLength)
        if (downloadStatus != DownloadStatus.STARTED) {
            urlFileDownloadCallManager.remove(downloadId)
        }
    }

    override suspend fun update(fileName: String, downloadStatus: Int, contentLength: Long) {
        downloadsDatabase.downloadsDao().update(fileName, downloadStatus, contentLength)
    }

    override suspend fun delete(downloadId: Long) {
        downloadsDatabase.downloadsDao().getDownloadItem(downloadId)?.let {
            File(it.filePath).delete()
        }
        downloadsDatabase.downloadsDao().delete(downloadId)
        urlFileDownloadCallManager.remove(downloadId)
    }

    override suspend fun delete(downloadIdList: List<Long>) {
        downloadsDatabase.downloadsDao().delete(downloadIdList)
        downloadIdList.forEach { urlFileDownloadCallManager.remove(it) }
    }

    override suspend fun deleteAll() {
        downloadsDatabase.downloadsDao().getDownloads().forEach {
            File(it.filePath).delete()
            urlFileDownloadCallManager.remove(it.downloadId)
        }
        downloadsDatabase.downloadsDao().delete()
    }

    override suspend fun getDownloads(): List<DownloadItem> {
        return downloadsDatabase.downloadsDao().getDownloads().mapToDownloadItems()
    }

    override suspend fun getDownloadItem(downloadId: Long): DownloadItem? {
        return downloadsDatabase.downloadsDao().getDownloadItem(downloadId)?.mapToDownloadItem()
    }

    override fun getDownloadsAsFlow(): Flow<List<DownloadItem>> {
        return downloadsDatabase.downloadsDao().getDownloadsAsFlow().distinctUntilChanged().map { it.mapToDownloadItems() }
    }

    private fun DownloadEntity.mapToDownloadItem(): DownloadItem =
        DownloadItem(
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
