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

package com.duckduckgo.site.permissions.impl

import android.webkit.PermissionRequest
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.site.permissions.store.SitePermissionsPreferences
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsDao
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionsAllowedDao
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class SitePermissionsRepositoryTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockSitePermissionsDao: SitePermissionsDao = mock()
    private val mockSitePermissionsAllowedDao: SitePermissionsAllowedDao = mock()
    private val mockSitePermissionsPreferences: SitePermissionsPreferences = mock()

    private val repository = SitePermissionsRepositoryImpl(
        mockSitePermissionsDao,
        mockSitePermissionsAllowedDao,
        mockSitePermissionsPreferences,
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider
    )

    private val url = "https://domain.com/whatever"
    private val domain = "domain.com"

    @Test
    fun givenPermissionNotSupportedThenDomainIsNotAllowedToAsk() {
        setInitialSettings()
        val permission = PermissionRequest.RESOURCE_MIDI_SYSEX

        assertFalse(repository.isDomainAllowedToAsk(url, permission))
    }

    @Test
    fun givenPermissionSupportedThenDomainIsAllowedToAsk() {
        setInitialSettings()
        val permission = PermissionRequest.RESOURCE_AUDIO_CAPTURE

        assertTrue(repository.isDomainAllowedToAsk(url, permission))
    }

    @Test
    fun whenAskForPermissionIsDisabledThenDomainIsNotAllowedToAsk() {
        setInitialSettings(cameraEnabled = false)
        val permission = PermissionRequest.RESOURCE_VIDEO_CAPTURE

        assertFalse(repository.isDomainAllowedToAsk(url, permission))
    }

    @Test
    fun whenAskForPermissionDisabledButSitePermissionSettingIsAlwaysAllowThenIsAllowedToAsk() {
        val testEntity = SitePermissionsEntity(domain, askMicSetting = SitePermissionAskSettingType.ALLOW_ALWAYS.name)
        setInitialSettings(micEnabled = false, sitePermissionEntity = testEntity)
        val permission = PermissionRequest.RESOURCE_AUDIO_CAPTURE

        assertTrue(repository.isDomainAllowedToAsk(url, permission))
    }

    @Test
    fun whenSitePermissionSettingIsDenyAlwaysThenDomainIsNotAllowedToAsk() {
        val testEntity = SitePermissionsEntity(domain, askCameraSetting = SitePermissionAskSettingType.DENY_ALWAYS.name)
        setInitialSettings(sitePermissionEntity = testEntity)
        val permission = PermissionRequest.RESOURCE_VIDEO_CAPTURE

        assertFalse(repository.isDomainAllowedToAsk(url, permission))
    }

    @Test
    fun whenSitePermissionsWasGrantedWithin24hThenReturnPermissionGranted() {
        setInitialSettings()
        val permission = PermissionRequest.RESOURCE_VIDEO_CAPTURE
        val tabId = "tabId"
        val sitePermissionAllowedEntity = SitePermissionAllowedEntity(domain, tabId, permission, setAllowedAtTime(12))
        whenever(mockSitePermissionsAllowedDao.getSitePermissionAllowed(domain, tabId, permission)).thenReturn(sitePermissionAllowedEntity)

        assertTrue(repository.isDomainGranted(url, tabId, permission))
    }

    @Test
    fun whenSitePermissionsWasMoreThen24hAgoThenReturnPermissionNotGranted() {
        setInitialSettings()
        val permission = PermissionRequest.RESOURCE_VIDEO_CAPTURE
        val tabId = "tabId"
        val sitePermissionAllowedEntity = SitePermissionAllowedEntity(domain, tabId, permission, setAllowedAtTime(25))
        whenever(mockSitePermissionsAllowedDao.getSitePermissionAllowed(domain, tabId, permission)).thenReturn(sitePermissionAllowedEntity)

        assertFalse(repository.isDomainGranted(url, tabId, permission))
    }

    @Test
    fun whenSitePermissionsSettingIsAllowAlwaysThenReturnPermissionGranted() {
        val testEntity = SitePermissionsEntity(domain, askCameraSetting = SitePermissionAskSettingType.ALLOW_ALWAYS.name)
        setInitialSettings(sitePermissionEntity = testEntity)
        val permission = PermissionRequest.RESOURCE_VIDEO_CAPTURE
        val tabId = "tabId"
        whenever(mockSitePermissionsAllowedDao.getSitePermissionAllowed(domain, tabId, permission)).thenReturn(null)

        assertTrue(repository.isDomainGranted(url, tabId, permission))
    }

    @Test
    fun whenUserGrantsSitePermissionFirstTimeThenSaveEntity() = runTest {
        val testEntity = SitePermissionsEntity(domain)
        setInitialSettings()
        repository.sitePermissionGranted(url, "tabId", PermissionRequest.RESOURCE_VIDEO_CAPTURE)

        verify(mockSitePermissionsDao).insert(testEntity)
    }

    @Test
    fun whenUserGrantsSitePermissionAlreadyInDbThenSkipSaveEntity() = runTest {
        val testEntity = SitePermissionsEntity(domain)
        setInitialSettings(sitePermissionEntity = testEntity)
        repository.sitePermissionGranted(url, "tabId", PermissionRequest.RESOURCE_VIDEO_CAPTURE)

        verify(mockSitePermissionsDao, never()).insert(testEntity)
    }

    @Test
    fun whenUserGrantsSitePermissionThenSaveAllowedEntity() = runTest {
        setInitialSettings()
        repository.sitePermissionGranted(url, "tabId", PermissionRequest.RESOURCE_VIDEO_CAPTURE)

        verify(mockSitePermissionsAllowedDao).insert(any())
    }

    @Test
    fun whenSitePermissionsWebsitesFlowIsCalledThenGetSitePermissionsWebsitesFlow() = runTest {
        repository.sitePermissionsWebsitesFlow()

        verify(mockSitePermissionsDao).getAllSitesPermissionsAsFlow()
    }

    @Test
    fun whenSitePermissionsForAllWebsitesIsCalledThenGetSitePermissionsForAllWebsites() = runTest {
        repository.sitePermissionsForAllWebsites()

        verify(mockSitePermissionsDao).getAllSitesPermissions()
    }

    @Test
    fun whenSitePermissionsAllowedFlowIsCalledThenGetSitePermissionsAllowedFlow() = runTest {
        repository.sitePermissionsAllowedFlow()

        verify(mockSitePermissionsAllowedDao).getAllSitesPermissionsAllowedAsFlow()
    }

    @Test
    fun whenUndoDeleteAllThenInsertSitePermissionsBackToAllowedDao() = runTest {
        val tabId = "tabId"
        val permission = PermissionRequest.RESOURCE_AUDIO_CAPTURE
        val testAllowedEntity = SitePermissionAllowedEntity(domain, tabId, permission, setAllowedAtTime(12))
        val allowedSites = listOf(testAllowedEntity)

        repository.undoDeleteAll(emptyList(), allowedSites)

        verify(mockSitePermissionsAllowedDao).insert(testAllowedEntity)
    }

    @Test
    fun whenUndoDeleteAllThenInsertSitePermissionsBackToSitePermissionsDao() = runTest {
        val testEntity = SitePermissionsEntity(domain)
        val sitePermissions = listOf(testEntity)

        repository.undoDeleteAll(sitePermissions, emptyList())

        verify(mockSitePermissionsDao).insert(testEntity)
    }

    @Test
    fun whenDeleteAllThenDeleteEntitiesFromDatabases() = runTest {
        repository.deleteAll()

        verify(mockSitePermissionsDao).deleteAll()
        verify(mockSitePermissionsAllowedDao).deleteAll()
    }

    @Test
    fun whenGetSitePermissionsForWebsiteCalledThenGetSitePermissionsByDomain() = runTest {
        repository.getSitePermissionsForWebsite(url)

        verify(mockSitePermissionsDao).getSitePermissionsByDomain(domain)
    }

    @Test
    fun whenDeletePermissionForSiteThenDeleteItFromDbs() = runTest {
        val testEntity = SitePermissionsEntity(domain)
        whenever(mockSitePermissionsDao.getSitePermissionsByDomain(domain)).thenReturn(testEntity)
        repository.deletePermissionsForSite(url)

        verify(mockSitePermissionsDao).delete(testEntity)
        verify(mockSitePermissionsAllowedDao).deleteAllowedSitesForDomain(domain)
    }

    @Test
    fun whenSavePermissionCalledThenInsertEntityInDb() = runTest {
        val testEntity = SitePermissionsEntity(domain)
        repository.savePermission(testEntity)

        verify(mockSitePermissionsDao).insert(testEntity)
    }

    private fun setInitialSettings(
        cameraEnabled: Boolean = true,
        micEnabled: Boolean = true,
        sitePermissionEntity: SitePermissionsEntity? = null
    ) {
        whenever(mockSitePermissionsPreferences.askCameraEnabled).thenReturn(cameraEnabled)
        whenever(mockSitePermissionsPreferences.askMicEnabled).thenReturn(micEnabled)
        whenever(mockSitePermissionsDao.getSitePermissionsByDomain(domain)).thenReturn(sitePermissionEntity)
    }

    private fun setAllowedAtTime(hoursAgo: Int): Long {
        val now = System.currentTimeMillis()
        return abs(now - hoursAgo * 3600000)
    }
}
