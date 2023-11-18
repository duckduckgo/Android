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

import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SitePermissionsManagerTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockSitePermissionsRepository: SitePermissionsRepository = mock()
    private val mockPackageManager = mock<PackageManager>()

    private val testee = SitePermissionsManagerImpl(mockPackageManager, mockSitePermissionsRepository, coroutineRule.testDispatcherProvider)

    private val url = "https://domain.com/whatever"
    private val tabId = "tabId"

    @Before
    fun before() {
        whenever(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)).thenReturn(true)
    }

    @Test
    fun givenListOfPermissionsThenPermissionsReturnedCorrectly() = runTest {
        val resources = arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE, PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_VIDEO_CAPTURE)).thenReturn(true)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_AUDIO_CAPTURE)).thenReturn(true)
        whenever(mockSitePermissionsRepository.isDomainGranted(url, tabId, PermissionRequest.RESOURCE_AUDIO_CAPTURE)).thenReturn(true)
        whenever(mockSitePermissionsRepository.isDomainGranted(url, tabId, PermissionRequest.RESOURCE_VIDEO_CAPTURE)).thenReturn(false)

        val permissionRequest: PermissionRequest = mock()
        whenever(permissionRequest.origin).thenReturn(url.toUri())
        whenever(permissionRequest.resources).thenReturn(resources)

        val permissions = testee.getSitePermissions(tabId, permissionRequest)
        assertEquals(1, permissions.autoAccept.size)
        assertEquals(1, permissions.userHandled.size)
        assertEquals(PermissionRequest.RESOURCE_AUDIO_CAPTURE, permissions.autoAccept.first())
        assertEquals(PermissionRequest.RESOURCE_VIDEO_CAPTURE, permissions.userHandled.first())
    }

    @Test
    fun givenListOfPermissionsShouldAutoAcceptThenGrantAndClearAutoHandlePermissions() = runTest {
        val resources = arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE, PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_VIDEO_CAPTURE)).thenReturn(true)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_AUDIO_CAPTURE)).thenReturn(true)
        whenever(mockSitePermissionsRepository.isDomainGranted(url, tabId, PermissionRequest.RESOURCE_AUDIO_CAPTURE)).thenReturn(true)
        whenever(mockSitePermissionsRepository.isDomainGranted(url, tabId, PermissionRequest.RESOURCE_VIDEO_CAPTURE)).thenReturn(true)

        val permissionRequest: PermissionRequest = mock()
        whenever(permissionRequest.origin).thenReturn(url.toUri())
        whenever(permissionRequest.resources).thenReturn(resources)

        val permissions = testee.getSitePermissions(tabId, permissionRequest)
        assertEquals(0, permissions.autoAccept.size)
        assertEquals(0, permissions.userHandled.size)
        verify(permissionRequest).grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE, PermissionRequest.RESOURCE_VIDEO_CAPTURE))
    }

    @Test
    fun givenListOfPermissionsThenFilterNotSupportedAndReturnOnlyPermissionsAllowedToAsk() = runTest {
        val resources =
            arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_MIDI_SYSEX, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_VIDEO_CAPTURE)).thenReturn(true)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_AUDIO_CAPTURE)).thenReturn(false)
        whenever(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)).thenReturn(true)

        val permissionRequest: PermissionRequest = mock()
        whenever(permissionRequest.origin).thenReturn(url.toUri())
        whenever(permissionRequest.resources).thenReturn(resources)

        val permissions = testee.getSitePermissions(tabId, permissionRequest)
        assertEquals(1, permissions.userHandled.size)
        assertEquals(0, permissions.autoAccept.size)
        assertEquals(PermissionRequest.RESOURCE_VIDEO_CAPTURE, permissions.userHandled.first())
    }

    @Test
    fun givenListOfPermissionsNoHardwareCameraThenFilterNotSupportedAndThenDenyPermissions() = runTest {
        val resources =
            arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_MIDI_SYSEX, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_VIDEO_CAPTURE)).thenReturn(true)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_AUDIO_CAPTURE)).thenReturn(false)
        whenever(mockPackageManager.hasSystemFeature(any())).thenReturn(false)

        val permissionRequest: PermissionRequest = mock()
        whenever(permissionRequest.origin).thenReturn(url.toUri())
        whenever(permissionRequest.resources).thenReturn(resources)

        val permissions = testee.getSitePermissions(tabId, permissionRequest)
        assertEquals(0, permissions.userHandled.size)
        assertEquals(0, permissions.autoAccept.size)
        verify(permissionRequest).deny()
    }

    @Test
    fun whenPermissionsShouldAutoDenyThenDeny() = runTest {
        val resources =
            arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_MIDI_SYSEX, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_VIDEO_CAPTURE)).thenReturn(false)
        whenever(mockSitePermissionsRepository.isDomainAllowedToAsk(url, PermissionRequest.RESOURCE_AUDIO_CAPTURE)).thenReturn(false)

        val permissionRequest: PermissionRequest = mock()
        whenever(permissionRequest.origin).thenReturn(url.toUri())
        whenever(permissionRequest.resources).thenReturn(resources)

        val permissions = testee.getSitePermissions(tabId, permissionRequest)
        assertEquals(0, permissions.userHandled.size)
        assertEquals(0, permissions.autoAccept.size)
        verify(permissionRequest).deny()
    }

    @Test
    fun whenClearAllButFireproofThenDontDeleteEntitiesWhichDomainIsInTheFireproofList() = runTest {
        val fireproofDomain = "domain.com"
        val testFireproofList = listOf(fireproofDomain, "domain1.com")
        val testSitePermissionsList = listOf(SitePermissionsEntity(fireproofDomain), SitePermissionsEntity("domain2.com"))
        whenever(mockSitePermissionsRepository.sitePermissionsForAllWebsites()).thenReturn(testSitePermissionsList)

        testee.clearAllButFireproof(testFireproofList)
        verify(mockSitePermissionsRepository, never()).deletePermissionsForSite(fireproofDomain)
    }

    @Test
    fun whenClearAllButFireproofThenDeleteEntitiesWhichDomainIsNotInTheFireproofList() = runTest {
        val domain = "domain2.com"
        val testFireproofList = listOf("domain.com", "domain1.com")
        val testSitePermissionsList = listOf(SitePermissionsEntity("domain.com"), SitePermissionsEntity(domain))
        whenever(mockSitePermissionsRepository.sitePermissionsForAllWebsites()).thenReturn(testSitePermissionsList)

        testee.clearAllButFireproof(testFireproofList)
        verify(mockSitePermissionsRepository).deletePermissionsForSite(domain)
    }

    @Test
    fun whenUrlNullThenGetPermissionsQueryResponseReturnsDenied() {
        assertEquals("denied", testee.getPermissionsQueryResponse(url, tabId, "some_permission"))
    }
}
