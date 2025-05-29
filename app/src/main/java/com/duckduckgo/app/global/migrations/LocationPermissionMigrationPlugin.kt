/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.global.migrations

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.location.data.LocationPermissionType.ALLOW_ALWAYS
import com.duckduckgo.app.location.data.LocationPermissionType.DENY_ALWAYS
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.migrations.MigrationPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.site.permissions.api.SitePermissionsManager.LocationPermissionRequest
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class LocationPermissionMigrationPlugin @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val sitePermissionsRepository: SitePermissionsRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MigrationPlugin {

    override val version: Int = 2

    override fun run() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!settingsDataStore.appLocationPermissionMigrated) {
                sitePermissionsRepository.askLocationEnabled = settingsDataStore.appLocationPermission
                logcat { "Location permissions migrated: location permission set to ${sitePermissionsRepository.askLocationEnabled}" }
                val locationPermissions = locationPermissionsRepository.getLocationPermissionsSync()
                val alwaysAllowedPermissions = locationPermissions.filter { it.permission == ALLOW_ALWAYS }
                val alwaysDeniedPermissions = locationPermissions.filter { it.permission == DENY_ALWAYS }
                alwaysAllowedPermissions.forEach { permission ->
                    sitePermissionsRepository.sitePermissionPermanentlySaved(
                        permission.domain,
                        LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION,
                        SitePermissionAskSettingType.ALLOW_ALWAYS,
                    )
                }
                alwaysDeniedPermissions.forEach { permission ->
                    sitePermissionsRepository.sitePermissionPermanentlySaved(
                        permission.domain,
                        LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION,
                        SitePermissionAskSettingType.DENY_ALWAYS,
                    )
                }
                settingsDataStore.appLocationPermissionMigrated = true
                logcat { "Location permissions migrated: ALLOW ALWAYS ${alwaysAllowedPermissions.size} DENY ALWAYS ${alwaysDeniedPermissions.size}." }
            }
        }
    }
}
