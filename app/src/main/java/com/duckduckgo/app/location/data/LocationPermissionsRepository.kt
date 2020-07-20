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
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocationPermissionsRepository @Inject constructor(
    private val locationPermissionsDao: LocationPermissionsDao,
    private val dispatchers: DispatcherProvider
) {

    fun getLocationPermissionsSync(): List<LocationPermissionEntity> = locationPermissionsDao.allPermissions()
    fun getLocationPermissionsAsync(): LiveData<List<LocationPermissionEntity>> = locationPermissionsDao.allPermissionsEntities()

    suspend fun savePermission(domain: String, permission: LocationPermissionType): LocationPermissionEntity? {
        val locationPermissionEntity = LocationPermissionEntity(domain = domain, permission = permission)
        val id = withContext(dispatchers.io()) {
            locationPermissionsDao.insert(locationPermissionEntity)
        }
        return if (id >= 0) {
            locationPermissionEntity
        } else {
            null
        }
    }

    suspend fun getDomainPermission(domain: String): LocationPermissionEntity? {
        return withContext(dispatchers.io()) {
            locationPermissionsDao.getPermission(domain)
        }
    }

    suspend fun hasUserGivenPermissionTo(domain: String): Boolean {
        return withContext(dispatchers.io()) {
            val domainPermission = locationPermissionsDao.getPermission(domain)
            domainPermission != null
        }
    }

    suspend fun deletePermission(domain: String) {
        withContext(dispatchers.io()) {
            val entity = locationPermissionsDao.getPermission(domain)
            entity?.let { locationPermissionsDao.delete(entity) }
        }
    }

}
