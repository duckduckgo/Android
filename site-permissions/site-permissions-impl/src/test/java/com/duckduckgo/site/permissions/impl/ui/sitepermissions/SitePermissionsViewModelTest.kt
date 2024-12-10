/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl.ui.sitepermissions

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel.Command.LaunchWebsiteAllowed
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel.Command.ShowRemovedAllConfirmationSnackbar
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SitePermissionsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockSitePermissionsRepository: SitePermissionsRepository = mock()

    private val viewModel = SitePermissionsViewModel(
        sitePermissionsRepository = mockSitePermissionsRepository,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Before
    fun before() {
        loadPermissionsSettings()
        loadWebsites()
        viewModel.allowedSites()
    }

    @Test
    fun whenAllowedSitesLoadedThenViewStateEmittedWebsites() = runTest {
        viewModel.viewState.test {
            val sitePermissions = awaitItem().sitesPermissionsAllowed
            assertEquals(2, sitePermissions.size)
        }
    }

    @Test
    fun whenRemoveAllWebsitesThenDeleteAllSitePermissionsIsCalled() = runTest {
        viewModel.viewState.test {
            viewModel.removeAllSitesSelected()

            verify(mockSitePermissionsRepository).deleteAll()

            val sitePermissions = expectMostRecentItem().sitesPermissionsAllowed
            assertEquals(2, sitePermissions.size)
        }
    }

    @Test
    fun whenRemoveAllWebsitesThenShowRemoveAllSnackBar() = runTest {
        viewModel.removeAllSitesSelected()

        viewModel.commands.test {
            assertTrue(awaitItem() is ShowRemovedAllConfirmationSnackbar)
        }
    }

    @Test
    fun whenPermissionsSettingsAreLoadedThenViewStateEmittedForAskLocationEnabled() = runTest {
        loadPermissionsSettings(locationEnabled = false)
        viewModel.viewState.test {
            val askLocationEnabled = awaitItem().askLocationEnabled
            assertFalse(askLocationEnabled)
        }
    }

    @Test
    fun whenPermissionsSettingsAreLoadedThenViewStateEmittedForAskCameraEnabled() = runTest {
        loadPermissionsSettings(cameraEnabled = false)

        viewModel.viewState.test {
            val askCameraEnabled = awaitItem().askCameraEnabled
            assertFalse(askCameraEnabled)
        }
    }

    @Test
    fun whenPermissionsSettingsAreLoadedThenViewStateEmittedForAskMicEnabled() = runTest {
        loadPermissionsSettings(micEnabled = false)

        viewModel.viewState.test {
            val askMicEnabled = awaitItem().askMicEnabled
            assertFalse(askMicEnabled)
        }
    }

    @Test
    fun whenToggleOffAskForLocationThenViewStateEmitted() = runTest {
        viewModel.permissionToggleSelected(false, R.string.sitePermissionsSettingsLocation)

        viewModel.viewState.test {
            val locationEnabled = awaitItem().askLocationEnabled
            assertFalse(locationEnabled)
        }
    }

    @Test
    fun whenToggleOffAskForCameraThenViewStateEmitted() = runTest {
        viewModel.permissionToggleSelected(false, R.string.sitePermissionsSettingsCamera)

        viewModel.viewState.test {
            val cameraEnabled = awaitItem().askCameraEnabled
            assertFalse(cameraEnabled)
        }
    }

    @Test
    fun whenToggleOffAskForMicThenViewStateEmitted() = runTest {
        viewModel.permissionToggleSelected(false, R.string.sitePermissionsSettingsMicrophone)

        viewModel.viewState.test {
            val micEnabled = awaitItem().askMicEnabled
            assertFalse(micEnabled)
        }
    }

    @Test
    fun whenToggleOffAskForDRMThenViewStateEmitted() = runTest {
        viewModel.permissionToggleSelected(false, R.string.sitePermissionsSettingsDRM)

        viewModel.viewState.test {
            val drmEnabled = awaitItem().askDrmEnabled
            assertFalse(drmEnabled)
        }
    }

    @Test
    fun whenWebsiteIsTappedThenNavigateToPermissionsPerWebsiteScreen() = runTest {
        val testDomain = "website1.com"
        viewModel.allowedSiteSelected(testDomain)

        viewModel.commands.test {
            assertTrue(awaitItem() is LaunchWebsiteAllowed)
        }
    }

    private fun loadWebsites() {
        val sitePermissions = listOf(SitePermissionsEntity("www.website2.com"), SitePermissionsEntity("www.website3.com"))
        whenever(mockSitePermissionsRepository.sitePermissionsWebsitesFlow()).thenReturn(flowOf(sitePermissions))
        whenever(mockSitePermissionsRepository.sitePermissionsAllowedFlow()).thenReturn(flowOf(emptyList()))
    }

    private fun loadPermissionsSettings(
        micEnabled: Boolean = true,
        cameraEnabled: Boolean = true,
        locationEnabled: Boolean = true,
        drmEnabled: Boolean = true,
    ) {
        whenever(mockSitePermissionsRepository.askMicEnabled).thenReturn(micEnabled)
        whenever(mockSitePermissionsRepository.askCameraEnabled).thenReturn(cameraEnabled)
        whenever(mockSitePermissionsRepository.askLocationEnabled).thenReturn(locationEnabled)
        whenever(mockSitePermissionsRepository.askDrmEnabled).thenReturn(drmEnabled)
    }
}
