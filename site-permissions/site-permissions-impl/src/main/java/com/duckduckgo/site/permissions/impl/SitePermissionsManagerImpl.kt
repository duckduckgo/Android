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
import android.location.LocationManager
import android.webkit.PermissionRequest
import androidx.core.location.LocationManagerCompat
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.site.permissions.api.SitePermissionsManager.LocationPermissionRequest
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissionQueryResponse
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

// Cannot be a Singleton
@ContributesBinding(AppScope::class)
class SitePermissionsManagerImpl @Inject constructor(
    private val packageManager: PackageManager,
    private val locationManager: LocationManager,
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val dispatcherProvider: DispatcherProvider,
) : SitePermissionsManager {

    private suspend fun getSitePermissionsGranted(
        url: String,
        tabId: String,
        resources: Array<String>,
    ): Array<String> =
        resources
            .filter { sitePermissionsRepository.isDomainGranted(url, tabId, it) }
            .toTypedArray()

    override suspend fun getSitePermissions(
        tabId: String,
        request: PermissionRequest,
    ): SitePermissions {
        val autoAccept = mutableListOf<String>()
        val url = request.origin.toString()

        val sitePermissionsAllowedToAsk = request.resources
            .filter { isPermissionSupported(it) && isHardwareSupported(it) }
            .filter { sitePermissionsRepository.isDomainAllowedToAsk(url, it) }
            .toTypedArray()

        logcat { "Permissions: sitePermissionsAllowedToAsk in $url ${sitePermissionsAllowedToAsk.asList()}" }

        val sitePermissionsGranted = getSitePermissionsGranted(url, tabId, sitePermissionsAllowedToAsk)
        if (sitePermissionsGranted.isNotEmpty()) {
            withContext(dispatcherProvider.main()) {
                logcat { "Permissions: site permission granted" }
                autoAccept.addAll(sitePermissionsGranted)
            }
        }

        logcat { "Permissions: sitePermissionsGranted for $url are ${sitePermissionsGranted.asList()}" }

        val userList = sitePermissionsAllowedToAsk.filter { !sitePermissionsGranted.contains(it) }
        if (userList.isEmpty() && sitePermissionsGranted.isEmpty()) {
            withContext(dispatcherProvider.main()) {
                logcat { "Permissions: site permission not granted, deny" }
                request.deny()
            }
        }
        if (userList.isEmpty() && autoAccept.isNotEmpty()) {
            withContext(dispatcherProvider.main()) {
                logcat { "Permissions: site permission granted, auto accept" }
                request.grant(autoAccept.toTypedArray())
                autoAccept.clear()
            }
        }

        val sitePermissions = SitePermissions(autoAccept = autoAccept, userHandled = userList)
        logcat { "Permissions: site permissions $sitePermissions" }
        return sitePermissions
    }

    override suspend fun clearAllButFireproof(fireproofDomains: List<String>) {
        sitePermissionsRepository.sitePermissionsForAllWebsites().forEach { permission ->
            if (!fireproofDomains.contains(permission.domain)) {
                sitePermissionsRepository.deletePermissionsForSite(permission.domain)
            }
        }
    }

    override suspend fun getPermissionsQueryResponse(
        url: String,
        tabId: String,
        queriedPermission: String,
    ): SitePermissionQueryResponse {
        getAndroidPermission(queriedPermission)?.let { androidPermission ->
            if (sitePermissionsRepository.isDomainGranted(url, tabId, androidPermission)) {
                return SitePermissionQueryResponse.Granted
            } else if (isHardwareSupported(androidPermission) && sitePermissionsRepository.isDomainAllowedToAsk(url, androidPermission)) {
                return SitePermissionQueryResponse.Prompt
            }
            return SitePermissionQueryResponse.Denied
        }

        return SitePermissionQueryResponse.Denied
    }

    override suspend fun hasSitePermanentPermission(
        url: String,
        request: String,
    ): Boolean {
        return sitePermissionsRepository.isDomainGranted(url, "", LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION)
    }

    private fun isPermissionSupported(permission: String): Boolean =
        permission == PermissionRequest.RESOURCE_AUDIO_CAPTURE || permission == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
            permission == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID || permission == LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION

    private fun isHardwareSupported(permission: String): Boolean = when (permission) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
            kotlin.runCatching { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }.getOrDefault(false)
        }
        LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION -> {
            kotlin.runCatching { LocationManagerCompat.isLocationEnabled(locationManager) }.getOrDefault(false)
        }
        else -> {
            true
        }
    }

    private fun getAndroidPermission(webQueryPermission: String): String? {
        return when (webQueryPermission) {
            "camera" -> PermissionRequest.RESOURCE_VIDEO_CAPTURE
            "microphone" -> PermissionRequest.RESOURCE_AUDIO_CAPTURE
            else -> null
        }
    }
}
