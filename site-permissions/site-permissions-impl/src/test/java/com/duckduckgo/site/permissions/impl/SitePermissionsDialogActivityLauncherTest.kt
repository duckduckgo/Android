/*
 * Copyright (c) 2026 DuckDuckGo
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

import android.app.Activity
import android.webkit.PermissionRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.site.permissions.api.SitePermissionsGrantedListener
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SitePermissionsDialogActivityLauncherTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val systemPermissionsHelper: SystemPermissionsHelper = mock()
    private val sitePermissionsRepository: SitePermissionsRepository = mock()
    private val faviconManager: FaviconManager = mock()
    private val pixel: Pixel = mock()
    private val permissionsGrantedListener: SitePermissionsGrantedListener = mock()

    private val testee = SitePermissionsDialogActivityLauncher(
        systemPermissionsHelper = systemPermissionsHelper,
        sitePermissionsRepository = sitePermissionsRepository,
        faviconManager = faviconManager,
        pixel = pixel,
        dispatcher = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    @Test
    fun whenDrmAlreadyAllowedForSessionThenDialogNotShownAndNoImpressionPixelFired() {
        whenever(sitePermissionsRepository.getDrmForSession("example.com")).thenReturn(true)

        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDrmBlockedByConfigThenDialogNotShownAndNoImpressionPixelFired() {
        whenever(sitePermissionsRepository.getDrmForSession("example.com")).thenReturn(null)
        whenever(sitePermissionsRepository.isDrmBlockedForUrlByConfig("https://example.com")).thenReturn(true)

        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        verifyNoMoreInteractions(pixel)
    }
}
