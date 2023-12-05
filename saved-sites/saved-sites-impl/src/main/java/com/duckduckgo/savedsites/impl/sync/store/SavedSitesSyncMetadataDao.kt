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

package com.duckduckgo.savedsites.impl.sync.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SavedSitesSyncMetadataDao {

    @Query("select * from saved_sites_sync_meta where `folderId` = :folderId")
    fun get(folderId: String): SavedSitesSyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addOrUpdate(entity: SavedSitesSyncMetadataEntity): Long

    @Transaction
    fun addOrUpdate(entities: List<SavedSitesSyncMetadataEntity>) {
        entities.forEach {
            addOrUpdate(it)
        }
    }

    @Query("update saved_sites_sync_meta set `children` = :children where `folderId` = :folderId")
    fun updateChildren(
        folderId: String,
        children: String,
    )

    @Transaction
    fun confirmAllChildren() {
        confirmChildren()
        removeAllRequests()
    }

    @Query("update saved_sites_sync_meta set `children` = request where `request` not null")
    fun confirmChildren()

    @Query("update saved_sites_sync_meta set `children` = request where `folderId` = :folderId")
    fun confirmChildren(folderId: String)

    @Transaction
    fun confirmChildren(folders: List<String>) {
        folders.forEach {
            confirmChildren(it)
        }
        confirmChildren()
        removeAllRequests()
    }

    @Query("update saved_sites_sync_meta set `request` = null")
    fun removeAllRequests()

    @Query("Delete from saved_sites_sync_meta")
    fun removeAll()

    @Query("delete from saved_sites_sync_meta where `folderId` = :folderId")
    fun remove(folderId: String)
}

@Entity(tableName = "saved_sites_sync_meta")
data class SavedSitesSyncMetadataEntity(
    @PrimaryKey val folderId: String,
    var children: String, // JSON representation of list of children confirmed by the BE
    var request: String, // JSON representation of list of children sent to the BE
)
