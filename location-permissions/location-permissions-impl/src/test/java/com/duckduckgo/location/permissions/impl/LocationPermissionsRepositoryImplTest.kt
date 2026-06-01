/*
 * Copyright (c) 2025 DuckDuckGo
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.location.permissions.api.LocationPermissionEntity
import com.duckduckgo.location.permissions.api.LocationPermissionType
import com.duckduckgo.location.permissions.store.LocationPermissionsDao
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class LocationPermissionsRepositoryImplTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dao: LocationPermissionsDao = mock()
    private val mockFaviconManager: FaviconManager = mock()
    private val lazyFaviconManager = Lazy { mockFaviconManager }

    private lateinit var repository: LocationPermissionsRepositoryImpl

    private val domain = "domain.com"

    @Before
    fun before() {
        repository = LocationPermissionsRepositoryImpl(dao, lazyFaviconManager, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenPermissionIsValidThenItIsStored() = runTest {
        whenever(dao.insert(LocationPermissionEntity(domain, LocationPermissionType.ALLOW_ALWAYS))).thenReturn(1L)
        val permission = LocationPermissionEntity(domain, LocationPermissionType.ALLOW_ALWAYS)

        val permissionStored = repository.savePermission(domain, LocationPermissionType.ALLOW_ALWAYS)

        assertEquals(permission, permissionStored)
    }

    @Test
    fun whenPermissionInsertFailsThenNullReturned() = runTest {
        whenever(dao.insert(LocationPermissionEntity(domain, LocationPermissionType.ALLOW_ALWAYS))).thenReturn(-1L)

        val permissionStored = repository.savePermission(domain, LocationPermissionType.ALLOW_ALWAYS)

        assertNull(permissionStored)
    }

    @Test
    fun whenGetAllPermissionsSyncThenReturnListFromDao() = runTest {
        val entities = listOf(
            LocationPermissionEntity("example.com", LocationPermissionType.ALLOW_ALWAYS),
            LocationPermissionEntity("example2.com", LocationPermissionType.ALLOW_ALWAYS),
        )
        whenever(dao.allPermissions()).thenReturn(entities)

        assertEquals(2, repository.getLocationPermissionsSync().size)
    }

    @Test
    fun whenDeletePermissionStoredThenItemRemovedFromDao() = runTest {
        val entity = LocationPermissionEntity("example.com", LocationPermissionType.ALLOW_ALWAYS)
        whenever(dao.getPermission("example.com")).thenReturn(entity)

        repository.deletePermission("example.com")

        verify(dao).delete(entity)
    }

    @Test
    fun whenDeletePermissionNotStoredThenNothingIsRemoved() = runTest {
        whenever(dao.getPermission("example3.com")).thenReturn(null)

        repository.deletePermission("example3.com")

        verify(dao, org.mockito.kotlin.never()).delete(org.mockito.kotlin.any())
    }

    @Test
    fun whenRetrievingStoredPermissionThenItCanBeRetrieved() = runTest {
        val entity = LocationPermissionEntity("example2.com", LocationPermissionType.ALLOW_ALWAYS)
        whenever(dao.getPermission("example2.com")).thenReturn(entity)

        val retrieved = repository.getDomainPermission("example2.com")!!

        assertEquals("example2.com", retrieved.domain)
    }

    @Test
    fun whenRetrievingNotStoredPermissionThenNothingCanBeRetrieved() = runTest {
        whenever(dao.getPermission("exampl3.com")).thenReturn(null)

        val retrieved = repository.getDomainPermission("exampl3.com")

        assertNull(retrieved)
    }

    @Test
    fun whenDeletePermissionStoredThenDeletePersistedFavicon() = runTest {
        val entity = LocationPermissionEntity("example.com", LocationPermissionType.ALLOW_ALWAYS)
        whenever(dao.getPermission("example.com")).thenReturn(entity)

        repository.deletePermission("example.com")

        verify(mockFaviconManager).deletePersistedFavicon("example.com")
    }

    @Test
    fun whenPermissionEntitiesCountByDomainThenReturnDaoCount() = runTest {
        whenever(dao.permissionEntitiesCountByDomain("test.com")).thenReturn(0)

        assertEquals(0, repository.permissionEntitiesCountByDomain("test.com"))
    }
}
