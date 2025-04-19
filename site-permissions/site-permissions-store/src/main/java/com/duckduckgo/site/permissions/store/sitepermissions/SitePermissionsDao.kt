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

package com.duckduckgo.site.permissions.store.sitepermissions

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SitePermissionsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(sitePermissionsEntity: SitePermissionsEntity): Long

    @Query("select * from site_permissions")
    fun getAllSitesPermissions(): List<SitePermissionsEntity>

    @Query("select * from site_permissions")
    fun getAllSitesPermissionsAsFlow(): Flow<List<SitePermissionsEntity>>

    @Query("select * from site_permissions where domain = :domain")
    suspend fun getSitePermissionsByDomain(domain: String): SitePermissionsEntity?

    @Delete
    fun delete(sitePermissionsEntity: SitePermissionsEntity): Int

    @Query("delete from site_permissions")
    fun deleteAll()
}
