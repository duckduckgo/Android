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

package com.duckduckgo.app.location.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.lastValue
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class LocationPermissionsViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var locationPermissionsDao: LocationPermissionsDao

    private lateinit var viewModel: LocationPermissionsViewModel

    private lateinit var db: AppDatabase

    private val commandCaptor = ArgumentCaptor.forClass(LocationPermissionsViewModel.Command::class.java)

    private val viewStateCaptor = ArgumentCaptor.forClass(LocationPermissionsViewModel.ViewState::class.java)

    private val mockCommandObserver: Observer<LocationPermissionsViewModel.Command> = mock()

    private val mockViewStateObserver: Observer<LocationPermissionsViewModel.ViewState> = mock()

    private val settingsDataStore: SettingsDataStore = mock()

    private val mockGeoLocationPermissions: GeoLocationPermissions = mock()

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        locationPermissionsDao = db.locationPermissionsDao()
        viewModel = LocationPermissionsViewModel(
            LocationPermissionsRepository(locationPermissionsDao, coroutineRule.testDispatcherProvider),
            mockGeoLocationPermissions,
            coroutineRule.testDispatcherProvider,
            settingsDataStore
        )
        viewModel.command.observeForever(mockCommandObserver)
        viewModel.viewState.observeForever(mockViewStateObserver)
    }

    @After
    fun after() {
        db.close()
        viewModel.command.removeObserver(mockCommandObserver)
        viewModel.viewState.removeObserver(mockViewStateObserver)
    }

    @Test
    fun whenViewModelIsCreatedThenInitialisedWithDefaultViewState() {
        val defaultViewState = LocationPermissionsViewModel.ViewState(false, emptyList())
        Mockito.verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        Assert.assertEquals(defaultViewState, viewStateCaptor.value)
    }

    @Test
    fun whenViewModelInitialisedThenViewStateShowsCurrentLocationPermissions() {
        givenLocationPermission(LocationPermissionType.ALLOW_ONCE, "domain.com")

        Mockito.verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        Assert.assertTrue(viewStateCaptor.value.locationPermissionEntities.size == 1)
    }

    @Test
    fun whenUserDeletesLocationPermissionThenConfirmDeleteCommandIssued() {
        val locationPermissionEntity = LocationPermissionEntity("domain.com", LocationPermissionType.ALLOW_ONCE)
        viewModel.onDeleteRequested(locationPermissionEntity)

        assertCommandIssued<LocationPermissionsViewModel.Command.ConfirmDeleteLocationPermission> {
            Assert.assertEquals(locationPermissionEntity, this.entity)
        }
    }

    @Test
    fun whenUserDeletesLocationPermissionThenViewStateIsUpdated() {
        givenLocationPermission(LocationPermissionType.ALLOW_ONCE, "domain.com")

        Mockito.verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        Assert.assertTrue(viewStateCaptor.value.locationPermissionEntities.size == 1)

        val locationPermissionEntity = LocationPermissionEntity("domain.com", LocationPermissionType.ALLOW_ONCE)
        viewModel.delete(locationPermissionEntity)

        Mockito.verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        Assert.assertTrue(viewStateCaptor.lastValue.locationPermissionEntities.isEmpty())
    }

    @Test
    fun whenUserDeletesLocationPermissionThenGeoLocationPermissionDeletesDomain() {
        givenLocationPermission(LocationPermissionType.ALLOW_ONCE, "domain.com")

        Mockito.verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        Assert.assertTrue(viewStateCaptor.value.locationPermissionEntities.size == 1)

        val locationPermissionEntity = LocationPermissionEntity("domain.com", LocationPermissionType.ALLOW_ONCE)
        viewModel.delete(locationPermissionEntity)

        Mockito.verify(mockGeoLocationPermissions).clear("domain.com")
    }

    @Test
    fun whenUseEditsLocationPermissionThenEditCommandIssued() {
        val locationPermissionEntity = LocationPermissionEntity("domain.com", LocationPermissionType.ALLOW_ONCE)
        viewModel.onEditRequested(locationPermissionEntity)

        assertCommandIssued<LocationPermissionsViewModel.Command.EditLocationPermissions> {
            Assert.assertEquals(locationPermissionEntity, this.entity)
        }
    }

    @Test
    fun whenUserTogglesLocationPermissionsThenViewStateUpdated() {
        viewModel.onLocationPermissionToggled(true)

        Mockito.verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        Assert.assertTrue(viewStateCaptor.value.locationPermissionEnabled)
    }

    @Test
    fun whenUserTogglesLoginDetectionThenUpdateSettingsDataStore() {
        viewModel.onLocationPermissionToggled(true)

        Mockito.verify(settingsDataStore).appLocationPermission = true
    }

    @Test
    fun whenUserEditsLocationPermissionThenViewStateIsUpdated() {
        givenLocationPermission(LocationPermissionType.ALLOW_ONCE, "domain.com")

        Mockito.verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        Assert.assertTrue(viewStateCaptor.value.locationPermissionEntities.size == 1)

        val oldPermission = viewStateCaptor.value.locationPermissionEntities[0].permission
        Assert.assertTrue(oldPermission == LocationPermissionType.ALLOW_ONCE)

        viewModel.onSiteLocationPermissionSelected("domain.com", LocationPermissionType.DENY_ALWAYS)

        Mockito.verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        Assert.assertTrue(viewStateCaptor.lastValue.locationPermissionEntities.size == 1)

        val newPermission = viewStateCaptor.lastValue.locationPermissionEntities[0].permission
        Assert.assertTrue(newPermission == LocationPermissionType.DENY_ALWAYS)
    }

    @Test
    fun whenAllowAlwaysPermissionsIsGrantedThenGeoLocationPermissionIsAllowed() = coroutineRule.runBlocking {
        val domain = "example.com"
        viewModel.onSiteLocationPermissionSelected(domain, LocationPermissionType.ALLOW_ALWAYS)

        verify(mockGeoLocationPermissions).allow(domain)
        Assert.assertEquals(locationPermissionsDao.getPermission(domain)!!.permission, LocationPermissionType.ALLOW_ALWAYS)
    }

    @Test
    fun whenAllowOncePermissionsIsGrantedThenGeoLocationPermissionIsAllowed() = coroutineRule.runBlocking {
        val domain = "example.com"
        viewModel.onSiteLocationPermissionSelected(domain, LocationPermissionType.ALLOW_ONCE)

        verify(mockGeoLocationPermissions).allow(domain)

        Assert.assertEquals(locationPermissionsDao.getPermission(domain)!!.permission, LocationPermissionType.ALLOW_ONCE)
    }

    @Test
    fun whenDenyOncePermissionsIsGrantedThenGeoLocationPermissionIsAllowed() = coroutineRule.runBlocking {
        val domain = "example.com"
        viewModel.onSiteLocationPermissionSelected(domain, LocationPermissionType.DENY_ONCE)

        verify(mockGeoLocationPermissions).clear(domain)
        Assert.assertEquals(locationPermissionsDao.getPermission(domain)!!.permission, LocationPermissionType.DENY_ONCE)
    }

    @Test
    fun whenDenyAlwaysPermissionsIsGrantedThenGeoLocationPermissionIsAllowed() = coroutineRule.runBlocking {
        val domain = "example.com"
        viewModel.onSiteLocationPermissionSelected(domain, LocationPermissionType.DENY_ALWAYS)

        verify(mockGeoLocationPermissions).clear(domain)
        Assert.assertEquals(locationPermissionsDao.getPermission(domain)!!.permission, LocationPermissionType.DENY_ALWAYS)
    }

    private fun givenLocationPermission(permission: LocationPermissionType = LocationPermissionType.ALLOW_ONCE, vararg domain: String) {
        domain.forEach {
            locationPermissionsDao.insert(LocationPermissionEntity(domain = it, permission = permission))
        }
    }

    private inline fun <reified T : LocationPermissionsViewModel.Command> assertCommandIssued(instanceAssertions: T.() -> Unit = {}) {
        Mockito.verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is T }
        Assert.assertNotNull(issuedCommand)
        (issuedCommand as T).apply { instanceAssertions() }
    }

}
