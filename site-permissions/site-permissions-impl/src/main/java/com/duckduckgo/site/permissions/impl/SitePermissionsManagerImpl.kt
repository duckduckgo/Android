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
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import javax.inject.Inject

class SitePermissionsManagerImpl @Inject constructor(
    private val packageManager: PackageManager,
    private val sitePermissionsRepository: SitePermissionsRepository,
) : SitePermissionsManager {

    override suspend fun getSitePermissionsGranted(
        url: String,
        tabId: String,
        resources: Array<String>,
    ): Array<String> =
        resources
            .filter { sitePermissionsRepository.isDomainGranted(url, tabId, it) }
            .toTypedArray()

    override suspend fun getSitePermissionsAllowedToAsk(
        url: String,
        resources: Array<String>,
    ): Array<String> =
        resources
            .filter { isPermissionSupported(it) && isHardwareSupported(it) }
            .filter { sitePermissionsRepository.isDomainAllowedToAsk(url, it) }
            .toTypedArray()

    override suspend fun clearAllButFireproof(fireproofDomains: List<String>) {
        sitePermissionsRepository.sitePermissionsForAllWebsites().forEach { permission ->
            if (!fireproofDomains.contains(permission.domain)) {
                sitePermissionsRepository.deletePermissionsForSite(permission.domain)
            }
        }
    }

    private fun isPermissionSupported(permission: String): Boolean =
        permission == PermissionRequest.RESOURCE_AUDIO_CAPTURE || permission == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
            permission == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID

    private fun isHardwareSupported(permission: String): Boolean = when (permission) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
            kotlin.runCatching { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }.getOrDefault(false)
        }
        else -> {
            true
        }
    }
}
