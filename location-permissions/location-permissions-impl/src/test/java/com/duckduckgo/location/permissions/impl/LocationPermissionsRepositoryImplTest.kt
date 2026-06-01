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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.location.permissions.api.LocationPermissionEntity
import com.duckduckgo.location.permissions.api.LocationPermissionType
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LocationPermissionsRepositoryImplTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockDao: LocationPermissionsDao = mock()
    private val mockFaviconManager: FaviconManager = mock()
    private val lazyFaviconManager = Lazy { mockFaviconManager }

    private lateinit var repository: LocationPermissionsRepositoryImpl

    private val domain = "domain.com"

    @Before
    fun before() {
        repository = LocationPermissionsRepositoryImpl(mockDao, lazyFaviconManager, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenPermissionIsValidThenItIsStored() = runTest {
        whenever(mockDao.insert(any())).thenReturn(1L)

        val expected = LocationPermissionEntity(domain, LocationPermissionType.ALLOW_ALWAYS)
        val permissionStored = repository.savePermission(domain, LocationPermissionType.ALLOW_ALWAYS)

        assertEquals(expected, permissionStored)
        verify(mockDao).insert(expected)
    }

    @Test
    fun whenPermissionInsertFailsThenNullReturned() = runTest {
        whenever(mockDao.insert(any())).thenReturn(-1L)

        val permissionStored = repository.savePermission(domain, LocationPermissionType.ALLOW_ALWAYS)

        assertNull(permissionStored)
    }

    @Test
    fun whenGetAllPermissionsAsyncThenReturnLiveDataFromDao() {
        val liveData: LiveData<List<LocationPermissionEntity>> = mock()
        whenever(mockDao.allPermissionsEntities()).thenReturn(liveData)

        assertSame(liveData, repository.getLocationPermissionsAsync())
    }

    @Test
    fun whenGetAllPermissionsSyncThenReturnListFromDao() {
        val entities = listOf(
            LocationPermissionEntity("example.com", LocationPermissionType.ALLOW_ALWAYS),
            LocationPermissionEntity("example2.com", LocationPermissionType.ALLOW_ALWAYS),
        )
        whenever(mockDao.allPermissions()).thenReturn(entities)

        val result = repository.getLocationPermissionsSync()

        assertEquals(2, result.size)
    }

    @Test
    fun whenDeletePermissionStoredThenItemRemovedFromDao() = runTest {
        val entity = LocationPermissionEntity("example.com", LocationPermissionType.ALLOW_ALWAYS)
        whenever(mockDao.getPermission("example.com")).thenReturn(entity)

        repository.deletePermission("example.com")

        verify(mockDao).delete(entity)
    }

    @Test
    fun whenDeletePermissionNotStoredThenNothingIsRemovedFromDao() = runTest {
        whenever(mockDao.getPermission("example3.com")).thenReturn(null)

        repository.deletePermission("example3.com")

        verify(mockDao, never()).delete(any())
    }

    @Test
    fun whenRetrievingStoredPermissionThenItCanBeRetrieved() = runTest {
        val entity = LocationPermissionEntity("example2.com", LocationPermissionType.ALLOW_ALWAYS)
        whenever(mockDao.getPermission("example2.com")).thenReturn(entity)

        val retrieved = repository.getDomainPermission("example2.com")!!

        assertEquals("example2.com", retrieved.domain)
    }

    @Test
    fun whenRetrievingNotStoredPermissionThenNothingCanBeRetrieved() = runTest {
        whenever(mockDao.getPermission("exampl3.com")).thenReturn(null)

        val retrieved = repository.getDomainPermission("exampl3.com")

        assertNull(retrieved)
    }

    @Test
    fun whenDeletePermissionStoredThenDeletePersistedFavicon() = runTest {
        val entity = LocationPermissionEntity("example.com", LocationPermissionType.ALLOW_ALWAYS)
        whenever(mockDao.getPermission("example.com")).thenReturn(entity)

        repository.deletePermission("example.com")

        verify(mockFaviconManager).deletePersistedFavicon("example.com")
    }

    @Test
    fun whenPermissionEntitiesCountByDomainAndNoWebsitesMatchThenReturnZero() = runTest {
        whenever(mockDao.permissionEntitiesCountByDomain("test.com")).thenReturn(0)

        val count = repository.permissionEntitiesCountByDomain("test.com")

        assertEquals(0, count)
    }

    @Test
    fun whenPermissionEntitiesCountByDomainAndWebsitesMatchThenReturnCount() = runTest {
        val query = "%example%"
        whenever(mockDao.permissionEntitiesCountByDomain(query)).thenReturn(1)

        val count = repository.permissionEntitiesCountByDomain(query)

        assertEquals(1, count)
    }
}
