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
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.GoBackToSitePermissions
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.ShowPermissionSettingSelectionDialog
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSetting
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ASK
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PermissionsPerWebsiteViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockSitePermissionsRepository: SitePermissionsRepository = mock()

    private val viewModel = PermissionsPerWebsiteViewModel(
        sitePermissionsRepository = mockSitePermissionsRepository,
    )

    private val domain = "domain.com"

    @Test
    fun whenPermissionsSettingsAreLoadedThenViewStateEmittedSettings() = runTest {
        loadAskForPermissionsPrefs()
        loadWebsitePermissionsSettings()

        viewModel.websitePermissionSettings(domain)

        viewModel.viewState.test {
            val permissions = awaitItem().websitePermissions
            assertEquals(4, permissions.size)
        }
    }

    @Test
    fun whenSitePermissionIsAllowAlwaysThenShowSettingAsAllow() = runTest {
        loadAskForPermissionsPrefs()
        loadWebsitePermissionsSettings(cameraSetting = SitePermissionAskSettingType.ALLOW_ALWAYS.name)

        viewModel.websitePermissionSettings(domain)

        viewModel.viewState.test {
            val cameraSetting = awaitItem().websitePermissions[1]
            assertEquals(WebsitePermissionSettingOption.ALLOW, cameraSetting.setting)
        }
    }

    @Test
    fun whenSitePermissionIsAskEveryTimeThenShowSettingAsAsk() = runTest {
        loadAskForPermissionsPrefs()
        loadWebsitePermissionsSettings(cameraSetting = SitePermissionAskSettingType.ASK_EVERY_TIME.name)

        viewModel.websitePermissionSettings(domain)

        viewModel.viewState.test {
            val cameraSetting = awaitItem().websitePermissions[1]
            assertEquals(WebsitePermissionSettingOption.ASK, cameraSetting.setting)
        }
    }

    @Test
    fun whenSitePermissionIsDenyAlwaysTimeThenShowSettingAsDeny() = runTest {
        loadAskForPermissionsPrefs()
        loadWebsitePermissionsSettings(cameraSetting = SitePermissionAskSettingType.DENY_ALWAYS.name)

        viewModel.websitePermissionSettings(domain)

        viewModel.viewState.test {
            val cameraSetting = awaitItem().websitePermissions[1]
            assertEquals(WebsitePermissionSettingOption.DENY, cameraSetting.setting)
        }
    }

    @Test
    fun whenAskForSitePermissionPrefsIsDisabledAndSettingIsAskThenShowSettingAsAskDisabled() = runTest {
        loadAskForPermissionsPrefs(cameraEnabled = false)
        loadWebsitePermissionsSettings()

        viewModel.websitePermissionSettings(domain)

        viewModel.viewState.test {
            val cameraSetting = awaitItem().websitePermissions[1]
            assertEquals(WebsitePermissionSettingOption.ASK_DISABLED, cameraSetting.setting)
        }
    }

    @Test
    fun whenAskForSitePermissionPrefsIsDisabledAndSettingIsDenyThenShowSettingAsDeny() = runTest {
        loadAskForPermissionsPrefs(cameraEnabled = false)
        loadWebsitePermissionsSettings(cameraSetting = SitePermissionAskSettingType.DENY_ALWAYS.name)

        viewModel.websitePermissionSettings(domain)

        viewModel.viewState.test {
            val cameraSetting = awaitItem().websitePermissions[1]
            assertEquals(WebsitePermissionSettingOption.DENY, cameraSetting.setting)
        }
    }

    @Test
    fun whenAskForSitePermissionPrefsIsDisabledAndSettingIsAllowThenShowSettingAsAllow() = runTest {
        loadAskForPermissionsPrefs(cameraEnabled = false)
        loadWebsitePermissionsSettings(cameraSetting = SitePermissionAskSettingType.ALLOW_ALWAYS.name)

        viewModel.websitePermissionSettings(domain)

        viewModel.viewState.test {
            val cameraSetting = awaitItem().websitePermissions[1]
            assertEquals(WebsitePermissionSettingOption.ALLOW, cameraSetting.setting)
        }
    }

    @Test
    fun whenPermissionIsTappedThenShowSettingsSelectionDialog() = runTest {
        loadAskForPermissionsPrefs()
        loadWebsitePermissionsSettings()

        viewModel.websitePermissionSettings(domain)
        val websitePermissionSetting =
            WebsitePermissionSetting(0, R.string.sitePermissionsSettingsCamera, ASK)
        viewModel.permissionSettingSelected(websitePermissionSetting)

        viewModel.commands.test {
            assertTrue(awaitItem() is ShowPermissionSettingSelectionDialog)
        }
    }

    @Test
    fun whenPermissionSettingIsChangedThenSave() = runTest {
        loadAskForPermissionsPrefs()
        loadWebsitePermissionsSettings()

        viewModel.websitePermissionSettings(domain)
        val websitePermissionSetting =
            WebsitePermissionSetting(0, R.string.sitePermissionsSettingsCamera, WebsitePermissionSettingOption.ASK)
        val sitePermissionSetting =
            SitePermissionsEntity(
                domain,
                websitePermissionSetting.setting.toSitePermissionSettingEntityType().name,
                websitePermissionSetting.setting.toSitePermissionSettingEntityType().name,
            )
        viewModel.onPermissionSettingSelected(websitePermissionSetting, domain)

        verify(mockSitePermissionsRepository).savePermission(sitePermissionSetting)
    }

    @Test
    fun whenWebsitePermissionsAreRemovedThenDeleteFromDB() = runTest {
        loadAskForPermissionsPrefs()
        loadWebsitePermissionsSettings()

        viewModel.websitePermissionSettings(domain)
        viewModel.removeWebsitePermissionsSettings(domain)

        verify(mockSitePermissionsRepository).deletePermissionsForSite(domain)
    }

    @Test
    fun whenWebsitePermissionsAreRemovedThenNavigateBackToSitePermissionsScreen() = runTest {
        loadAskForPermissionsPrefs()
        loadWebsitePermissionsSettings()

        viewModel.websitePermissionSettings(domain)
        viewModel.removeWebsitePermissionsSettings(domain)

        viewModel.commands.test {
            assertTrue(awaitItem() is GoBackToSitePermissions)
        }
    }

    private fun loadAskForPermissionsPrefs(
        micEnabled: Boolean = true,
        cameraEnabled: Boolean = true,
        locationEnabled: Boolean = true,
        drmEnabled: Boolean = true,
    ) {
        whenever(mockSitePermissionsRepository.askMicEnabled).thenReturn(micEnabled)
        whenever(mockSitePermissionsRepository.askCameraEnabled).thenReturn(cameraEnabled)
        whenever(mockSitePermissionsRepository.askDrmEnabled).thenReturn(drmEnabled)
        whenever(mockSitePermissionsRepository.askLocationEnabled).thenReturn(locationEnabled)
    }

    private fun loadWebsitePermissionsSettings(
        cameraSetting: String = SitePermissionAskSettingType.ASK_EVERY_TIME.name,
        micSetting: String = SitePermissionAskSettingType.ASK_EVERY_TIME.name,
        locationSetting: String = SitePermissionAskSettingType.ASK_EVERY_TIME.name,
        drmSetting: String = SitePermissionAskSettingType.ASK_EVERY_TIME.name,
    ) {
        val testSitePermissionEntity = SitePermissionsEntity(domain, cameraSetting, micSetting, drmSetting, locationSetting)
        mockSitePermissionsRepository.stub { onBlocking { getSitePermissionsForWebsite(domain) }.thenReturn(testSitePermissionEntity) }
    }
}
