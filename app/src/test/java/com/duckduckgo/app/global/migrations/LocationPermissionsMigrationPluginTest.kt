/*
 * Copyright (c) 2021 DuckDuckGo
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

import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType.ALLOW_ALWAYS
import com.duckduckgo.app.location.data.LocationPermissionType.DENY_ALWAYS
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.site.permissions.api.SitePermissionsManager.LocationPermissionRequest
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class LocationPermissionsMigrationPluginTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private var settingsDataStore: SettingsDataStore = mock()
    private var locationPermissionsRepository: LocationPermissionsRepository = mock()
    private var sitePermissionsRepository: SitePermissionsRepository = mock()

    private lateinit var testee: LocationPermissionMigrationPlugin

    @Before
    fun before() {
        testee = LocationPermissionMigrationPlugin(
            settingsDataStore,
            locationPermissionsRepository,
            sitePermissionsRepository,
            TestScope(),
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenMigrationIsNeededAndRanThenMigrationStateStored() {
        whenever(settingsDataStore.appLocationPermissionMigrated).thenReturn(false)

        testee.run()

        verify(settingsDataStore).appLocationPermissionMigrated = true
    }

    @Test
    fun whenMigrationIsNeededAndRanThenLocationPermissionMigrated() {
        whenever(settingsDataStore.appLocationPermissionMigrated).thenReturn(false)
        whenever(settingsDataStore.appLocationPermission).thenReturn(false)

        testee.run()

        verify(sitePermissionsRepository).askLocationEnabled = false
    }

    @Test
    fun whenMigrationNotNeededAThenNothingHappens() {
        whenever(settingsDataStore.appLocationPermissionMigrated).thenReturn(true)

        testee.run()

        verifyNoInteractions(locationPermissionsRepository)
    }

    @Test
    fun whenAllowedPermissionsPresentThenCanBeMigrated() {
        whenever(settingsDataStore.appLocationPermissionMigrated).thenReturn(false)
        whenever(locationPermissionsRepository.getLocationPermissionsSync()).thenReturn(
            listOf(LocationPermissionEntity("domain.com", ALLOW_ALWAYS)),
        )

        testee.run()

        verify(sitePermissionsRepository).sitePermissionPermanentlySaved(
            "domain.com",
            LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION,
            SitePermissionAskSettingType.ALLOW_ALWAYS,
        )
    }

    @Test
    fun whenDeniedPermissionsPresentThenCanBeMigrated() {
        whenever(settingsDataStore.appLocationPermissionMigrated).thenReturn(false)
        whenever(locationPermissionsRepository.getLocationPermissionsSync()).thenReturn(
            listOf(LocationPermissionEntity("domain.com", DENY_ALWAYS)),
        )

        testee.run()

        verify(sitePermissionsRepository).sitePermissionPermanentlySaved(
            "domain.com",
            LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION,
            SitePermissionAskSettingType.DENY_ALWAYS,
        )
    }
}
