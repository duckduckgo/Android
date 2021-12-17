/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.location.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationPermissionsDao {

    @Query("select * from locationPermissions") fun allPermissions(): List<LocationPermissionEntity>

    @Query("select * from locationPermissions")
    fun allPermissionsEntities(): LiveData<List<LocationPermissionEntity>>

    @Query("select * from locationPermissions WHERE domain = :domain")
    fun getPermission(domain: String): LocationPermissionEntity?

    @Query("select count(*) from locationPermissions WHERE domain LIKE :domain")
    fun permissionEntitiesCountByDomain(domain: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(locationPermissionEntity: LocationPermissionEntity): Long

    @Delete fun delete(locationPermissionEntity: LocationPermissionEntity): Int
}
