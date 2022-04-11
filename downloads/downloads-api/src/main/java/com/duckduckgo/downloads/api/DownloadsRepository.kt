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

package com.duckduckgo.downloads.api

import com.duckduckgo.downloads.api.model.DownloadItem
import kotlinx.coroutines.flow.Flow

interface DownloadsRepository {
    suspend fun insert(downloadItem: DownloadItem): Long
    suspend fun insertAll(downloadItems: List<DownloadItem>)
    suspend fun update(downloadId: Long, downloadStatus: Int, contentLength: Long)
    suspend fun update(fileName: String, downloadStatus: Int, contentLength: Long)
    suspend fun delete(id: Long)
    suspend fun delete(downloadIdList: List<Long>)
    suspend fun deleteAll()
    suspend fun getDownloads(): List<DownloadItem>
    suspend fun getDownloadItem(downloadId: Long): DownloadItem?
    fun getDownloadsAsFlow(): Flow<List<DownloadItem>>
}
