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

package com.duckduckgo.site.permissions.store.sitepermissionsallowed

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SitePermissionsAllowedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(sitePermissionAllowed: SitePermissionAllowedEntity): Long

    @Query("select * from site_permission_allowed")
    fun getAllSitesPermissionsAllowedAsFlow(): Flow<List<SitePermissionAllowedEntity>>

    @Query("select * from site_permission_allowed where domain = :domain and tabId = :tabId and permissionAllowed = :permissionAllowed")
    suspend fun getSitePermissionAllowed(domain: String, tabId: String, permissionAllowed: String): SitePermissionAllowedEntity?

    @Delete
    fun delete(sitePermissionsEntity: SitePermissionAllowedEntity): Int

    @Query("delete from site_permission_allowed")
    fun deleteAll()

    @Query("delete from site_permission_allowed where domain = :domain")
    fun deleteAllowedSitesForDomain(domain: String): Int
}
