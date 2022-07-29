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
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.store.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsDao
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionsAllowedDao
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SitePermissionsRepository {
    fun isDomainAllowedToAsk(url: String, permission: String): Boolean
    fun isDomainGranted(url: String, tabId: String, permission: String): Boolean
}

@ContributesBinding(ActivityScope::class)
class SitePermissionsRepositoryImpl @Inject constructor(
    private val sitePermissionsDao: SitePermissionsDao,
    private val sitePermissionsAllowedDao: SitePermissionsAllowedDao
) : SitePermissionsRepository {

    override fun isDomainAllowedToAsk(url: String, permission: String): Boolean {
        val sitePermissionsForDomain = sitePermissionsDao.getSitePermissionsByDomain(url)
        return when (permission) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                val askForCameraEnabled = sitePermissionsForDomain?.askCameraEnabled == true
                val isAskCameraSettingDenied = sitePermissionsForDomain?.askCameraSetting == SitePermissionAskSettingType.DENY_ALWAYS.name
                askForCameraEnabled && !isAskCameraSettingDenied
            }
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                val askForMicEnabled = sitePermissionsForDomain?.askMicEnabled == true
                val isAskMicSettingDenied = sitePermissionsForDomain?.askMicSetting == SitePermissionAskSettingType.DENY_ALWAYS.name
                askForMicEnabled && !isAskMicSettingDenied
            }
            else -> false
        }
    }

    override fun isDomainGranted(url: String, tabId: String, permission: String): Boolean {
        val sitePermissionForDomain = sitePermissionsDao.getSitePermissionsByDomain(url)
        val permissionAllowedId = SitePermissionAllowedEntity.getPermissionAllowedId(url, tabId, permission)
        val sitePermissionGranted = sitePermissionsAllowedDao.getSitePermissionAllowed(permissionAllowedId) != null

        return when (permission) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                val isCameraAlwaysAllowed = sitePermissionForDomain?.askCameraSetting == SitePermissionAskSettingType.ALLOW_ALWAYS.name
                sitePermissionGranted || isCameraAlwaysAllowed
            }
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                val isMicAlwaysAllowed = sitePermissionForDomain?.askMicSetting == SitePermissionAskSettingType.ALLOW_ALWAYS.name
                sitePermissionGranted || isMicAlwaysAllowed
            }
            else -> false
        }
    }
}
