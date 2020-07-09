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

package com.duckduckgo.app.location

import android.webkit.GeolocationPermissions
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.location.data.host
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

interface GeoLocationPermissions {
    fun allow(domain: String)
    fun deny(domain: String)
    suspend fun clearAll()
}

class GeoLocationPermissionsManager @Inject constructor(
    private val permissionsRepository: LocationPermissionsRepository,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository
) : GeoLocationPermissions {

    override fun allow(domain: String) {
        val geolocationPermissions = GeolocationPermissions.getInstance()
        geolocationPermissions.allow(domain)
    }

    override fun deny(domain: String) {
        val geolocationPermissions = GeolocationPermissions.getInstance()
        geolocationPermissions.clear(domain)
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            val geolocationPermissions = GeolocationPermissions.getInstance()
            val permissions = permissionsRepository.getLocationPermissionsSync()
            val fireproofed = fireproofWebsiteRepository.getFireproofWebsitesSync()
            Timber.i("GeoLocationsPermissionsManager: $permissions, fireproofed: $fireproofed")
            permissions.forEach {
                if (!fireproofWebsiteRepository.isDomainFireproofed(it.host())) {
                    geolocationPermissions.clear(it.domain)
                    permissionsRepository.removeLocationPermission(it.domain)
                    Timber.i("GeoLocationsPermissionsManager: Remove permission from ${it.domain}")
                }
            }
        }

    }
}
