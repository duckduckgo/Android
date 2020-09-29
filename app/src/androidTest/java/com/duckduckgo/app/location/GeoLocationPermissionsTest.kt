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
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
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

    private val mockFaviconManager: FaviconManager = mock()
    private val lazyFaviconManager = Lazy { mockFaviconManager }

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        locationPermissionsDao = db.locationPermissionsDao()
        fireproofWebsiteDao = db.fireproofWebsiteDao()

        geoLocationPermissions = GeoLocationPermissionsManager(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LocationPermissionsRepository(locationPermissionsDao, lazyFaviconManager, coroutineRule.testDispatcherProvider),
            FireproofWebsiteRepository(fireproofWebsiteDao, coroutineRule.testDispatcherProvider, lazyFaviconManager),
            coroutineRule.testDispatcherProvider
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenClearingAllPermissionsButFireproofedThenOnlyNonFireproofedSitesAreDeleted() = coroutineRule.runBlocking {
        givenFireproofWebsiteDomain("anotherdomain.com")
        givenLocationPermissionsDomain("https://domain.com")

        val oldFireproofWebsites = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(oldFireproofWebsites.size, 1)

        val oldLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(oldLocationPermissions.size, 1)

        geoLocationPermissions.clearAllButFireproofed()

        val newFireproofWebsites = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(newFireproofWebsites.size, 1)

        val newLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(newLocationPermissions.size, 0)
    }

    @Test
    fun whenClearingAllPermissionsButFireproofedAndNoFireproofedSitesThenAllSitePermissionsAreDeleted() = coroutineRule.runBlocking {
        givenLocationPermissionsDomain("https://domain.com")

        val oldLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(oldLocationPermissions.size, 1)

        geoLocationPermissions.clearAllButFireproofed()

        val newLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(newLocationPermissions.size, 0)
    }

    @Test
    fun whenClearingAllPermissionsButFireproofedAndSiteIsFireproofedThenNothingIsDeleted() = coroutineRule.runBlocking {
        givenFireproofWebsiteDomain("domain.com")
        givenLocationPermissionsDomain("https://domain.com")

        val oldFireproofWebsites = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(oldFireproofWebsites.size, 1)

        val oldLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(oldLocationPermissions.size, 1)

        geoLocationPermissions.clearAllButFireproofed()

        val newFireproofWebsites = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(newFireproofWebsites.size, 1)

        val newLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(newLocationPermissions.size, 1)
    }

    @Test
    fun whenClearingAllPermissionsThenAllPermissionsAreDeleted() = coroutineRule.runBlocking {
        givenLocationPermissionsDomain("https://domain.com")

        val oldLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(oldLocationPermissions.size, 1)

        geoLocationPermissions.clearAll()

        val newLocationPermissions = locationPermissionsDao.allPermissions()
        assertEquals(newLocationPermissions.size, 0)
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
