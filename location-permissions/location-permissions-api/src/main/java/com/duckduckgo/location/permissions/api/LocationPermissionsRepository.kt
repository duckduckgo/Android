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

package com.duckduckgo.location.permissions.api

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

interface LocationPermissionsRepository {
    fun getLocationPermissionsSync(): List<LocationPermissionEntity>
    fun getLocationPermissionsAsync(): LiveData<List<LocationPermissionEntity>>
    fun getLocationPermissionsFlow(): Flow<List<LocationPermissionEntity>>
    suspend fun savePermission(domain: String, permission: LocationPermissionType): LocationPermissionEntity?
    suspend fun getDomainPermission(domain: String): LocationPermissionEntity?
    suspend fun deletePermission(domain: String)
    suspend fun permissionEntitiesCountByDomain(domain: String): Int
    fun savePermissionEntity(entity: LocationPermissionEntity)
}
