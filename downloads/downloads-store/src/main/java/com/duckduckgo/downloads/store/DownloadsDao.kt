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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(downloadItem: DownloadEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloadItems: List<DownloadEntity>)

    @Query("update downloads set downloadStatus = :downloadStatus, contentLength = :contentLength where downloadId =:downloadId")
    suspend fun update(downloadId: Long, downloadStatus: Int, contentLength: Long)

    @Query(
        """update downloads set downloadStatus = :downloadStatus, contentLength = :contentLength where id =
        (select id from downloads where downloadId = 0 and fileName = :fileName order by createdAt desc limit 1)"""
    )
    suspend fun update(fileName: String, downloadStatus: Int, contentLength: Long)

    @Query("delete from downloads where id = :id")
    suspend fun delete(id: Long)

    @Query("delete from downloads where downloadId in (:downloadIdList)")
    suspend fun delete(downloadIdList: List<Long>)

    @Query("delete from downloads")
    suspend fun delete()

    @Query("select * from downloads order by createdAt desc")
    fun getDownloadsAsFlow(): Flow<List<DownloadEntity>>

    @Query("select * from downloads order by createdAt desc")
    suspend fun getDownloads(): List<DownloadEntity>

    @Query("select * from downloads where downloadId = :downloadId")
    suspend fun getDownloadItem(downloadId: Long): DownloadEntity?
}
