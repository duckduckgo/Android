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

package com.duckduckgo.location.permissions.impl

import androidx.lifecycle.LiveData
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.location.permissions.api.LocationPermissionEntity
import com.duckduckgo.location.permissions.api.LocationPermissionType
import com.duckduckgo.location.permissions.api.LocationPermissionsDao
import com.duckduckgo.location.permissions.api.LocationPermissionsRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
class LocationPermissionsRepositoryImpl @Inject constructor(
    private val locationPermissionsDao: LocationPermissionsDao,
    private val faviconManager: Lazy<FaviconManager>,
    private val dispatchers: DispatcherProvider,
) : LocationPermissionsRepository {

    override fun getLocationPermissionsSync(): List<LocationPermissionEntity> = locationPermissionsDao.allPermissions()
    override fun getLocationPermissionsAsync(): LiveData<List<LocationPermissionEntity>> = locationPermissionsDao.allPermissionsEntities()
    override fun getLocationPermissionsFlow(): Flow<List<LocationPermissionEntity>> = locationPermissionsDao.allPermissionsAsFlow()

    override suspend fun savePermission(
        domain: String,
        permission: LocationPermissionType,
    ): LocationPermissionEntity? {
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

    override suspend fun getDomainPermission(domain: String): LocationPermissionEntity? {
        return withContext(dispatchers.io()) {
            locationPermissionsDao.getPermission(domain)
        }
    }

    override suspend fun deletePermission(domain: String) {
        withContext(dispatchers.io()) {
            val entity = locationPermissionsDao.getPermission(domain)
            entity?.let {
                faviconManager.get().deletePersistedFavicon(domain)
                locationPermissionsDao.delete(it)
            }
        }
    }

    override suspend fun permissionEntitiesCountByDomain(domain: String): Int {
        return withContext(dispatchers.io()) {
            locationPermissionsDao.permissionEntitiesCountByDomain(domain)
        }
    }

    override fun savePermissionEntity(entity: LocationPermissionEntity) {
        locationPermissionsDao.insert(entity)
    }
}
