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
import com.duckduckgo.app.global.UriString
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocationPermissionsRepository @Inject constructor(
    private val locationPermissionsDao: LocationPermissionsDao,
    private val dispatchers: DispatcherProvider
) {

    fun getLocationPermissions(): LiveData<List<LocationPermissionEntity>> = locationPermissionsDao.allPermissionsEntities()

    suspend fun saveLocationPermission(domain: String, permission: LocationPermissionType): LocationPermissionEntity? {
        if (!UriString.isValidDomain(domain)) return null

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

    suspend fun getDomainPermission(domain: String): LocationPermissionType {
        return locationPermissionsDao.getPermission(domain)!!.permission
    }

    suspend fun hasUserGivenPermissionTo(domain: String): Boolean {
        val domainPermission = locationPermissionsDao.getPermission(domain) ?: return false
        return true
    }

    suspend fun removeLocationPermission(domain: String) {
        withContext(dispatchers.io()) {
            val entity = locationPermissionsDao.getPermission(domain)
            entity?.let { locationPermissionsDao.delete(entity) }

        }
    }
}
