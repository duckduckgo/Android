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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GeoLocationPermissionsTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var locationPermissionsDao: LocationPermissionsDao

    private lateinit var fireproofWebsiteDao: FireproofWebsiteDao

    private lateinit var db: AppDatabase

    private lateinit var geoLocationPermissions: GeoLocationPermissions

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        locationPermissionsDao = db.locationPermissionsDao()
        fireproofWebsiteDao = db.fireproofWebsiteDao()

        geoLocationPermissions = GeoLocationPermissionsManager(
            LocationPermissionsRepository(locationPermissionsDao, coroutineRule.testDispatcherProvider),
            FireproofWebsiteRepository(fireproofWebsiteDao, coroutineRule.testDispatcherProvider),
            coroutineRule.testDispatcherProvider
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAllAllowedPermissionsAreClearedThenOnlyNoFireproofedSitesAreDeleted() = coroutineRule.runBlocking {
        givenFireproofWebsiteDomain("anotherdomain.com")
        givenLocationPermissionsDomain("domain.com")

        val oldFireproofWebsites = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(oldFireproofWebsites.size, 1)

        val oldLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(oldLocationPermissions.size, 1)

        geoLocationPermissions.clearAll()

        val newFireproofWebsites = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(newFireproofWebsites.size, 1)

        val newLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(newLocationPermissions.size, 0)
    }

    @Test
    fun whenAllAllowedPermissionsAreClearedAndNoFireproofedSitesThenAllSitePermissionsAreDeleted() = coroutineRule.runBlocking {
        givenLocationPermissionsDomain("domain.com")

        val oldLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(oldLocationPermissions.size, 1)

        geoLocationPermissions.clearAll()

        val newLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(newLocationPermissions.size, 0)
    }

    @Test
    fun whenAllAllowedPermissionsAreClearedAndSiteIsFireproofedThenIsNotDeleted() = coroutineRule.runBlocking {
        givenFireproofWebsiteDomain("domain.com")
        givenLocationPermissionsDomain("domain.com")

        val oldFireproofWebsites = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(oldFireproofWebsites.size, 1)

        val oldLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(oldLocationPermissions.size, 1)

        geoLocationPermissions.clearAll()

        val newFireproofWebsites = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(newFireproofWebsites.size, 1)

        val newLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(newLocationPermissions.size, 1)
    }

    @Test
    fun whenAllStoredPermissionsAreAlwaysDenyThenNoPermissionsAreDeleted() = coroutineRule.runBlocking {
        givenFireproofWebsiteDomain("anotherdomain.com")
        givenLocationPermissionsDomain("domain.com", LocationPermissionType.DENY_ALWAYS)

        val oldLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(oldLocationPermissions.size, 1)

        geoLocationPermissions.clearAll()

        val newLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(newLocationPermissions.size, 1)
    }

    private fun givenFireproofWebsiteDomain(vararg fireproofWebsitesDomain: String) {
        fireproofWebsitesDomain.forEach {
            fireproofWebsiteDao.insert(FireproofWebsiteEntity(domain = it))
        }
    }

    private fun givenLocationPermissionsDomain(domain: String, permissionType: LocationPermissionType = LocationPermissionType.ALLOW_ALWAYS) {
        locationPermissionsDao.insert(LocationPermissionEntity(domain = domain, permission = permissionType))
    }
}
