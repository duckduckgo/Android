/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.store

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CredentialsSyncMetadataDao {

    @Query("select * from credentials_sync_meta where id = :id")
    fun getSyncId(id: Long): CredentialsSyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: CredentialsSyncMetadataEntity): Long

    @Query("select * from credentials_sync_meta where deleted_at >= :since")
    fun getRemovedIdsSince(since: String): List<CredentialsSyncMetadataEntity>

    @Delete
    fun delete(entity: CredentialsSyncMetadataEntity)

    @Query("select id from credentials_sync_meta where syncId = :syncId")
    fun getLocalId(syncId: String): Long?

    @Query("Delete from credentials_sync_meta where deleted_at <= :before")
    fun removeDeletedEntities(before: String)

    @Query("Delete from credentials_sync_meta where id = :id")
    fun removeEntityWithLocalId(id: Long)

    @Query("Delete from credentials_sync_meta where syncId = :syncId")
    fun removeEntityWithSyncId(syncId: String)
}
