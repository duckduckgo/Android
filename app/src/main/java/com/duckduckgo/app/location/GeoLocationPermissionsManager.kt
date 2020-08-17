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

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.webkit.GeolocationPermissions
import androidx.core.location.LocationManagerCompat
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.view.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface GeoLocationPermissions {
    fun isDeviceLocationEnabled(): Boolean
    fun allow(domain: String)
    fun clear(domain: String)
    suspend fun clearAll()
    suspend fun clearAllButFireproofed()
}

class GeoLocationPermissionsManager @Inject constructor(
    private val context: Context,
    private val permissionsRepository: LocationPermissionsRepository,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val dispatchers: DispatcherProvider
) : GeoLocationPermissions {

    override fun isDeviceLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    override fun allow(domain: String) {
        val geolocationPermissions = GeolocationPermissions.getInstance()
        geolocationPermissions.allow(domain)
    }

    override fun clear(domain: String) {
        val geolocationPermissions = GeolocationPermissions.getInstance()
        geolocationPermissions.clear(domain)
    }

    override suspend fun clearAll() {
        withContext(dispatchers.io()) {
            val geolocationPermissions = GeolocationPermissions.getInstance()
            val permissions = permissionsRepository.getLocationPermissionsSync()
            permissions.forEach {
                geolocationPermissions.clear(it.domain)
                permissionsRepository.deletePermission(it.domain)
            }
        }
    }

    override suspend fun clearAllButFireproofed() {
        withContext(dispatchers.io()) {
            val geolocationPermissions = GeolocationPermissions.getInstance()
            val permissions = permissionsRepository.getLocationPermissionsSync()
            permissions.forEach {
                if (!fireproofWebsiteRepository.isDomainFireproofed(it.domain.websiteFromGeoLocationsApiOrigin())) {
                    geolocationPermissions.clear(it.domain)
                    permissionsRepository.deletePermission(it.domain)
                }
            }
        }
    }
}
