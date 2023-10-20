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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.extractDomain
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.store.SitePermissionsPreferences
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsDao
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionsAllowedDao
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface SitePermissionsRepository {
    var askCameraEnabled: Boolean
    var askMicEnabled: Boolean
    var askDrmEnabled: Boolean
    fun isDomainAllowedToAsk(url: String, permission: String): Boolean
    fun isDomainGranted(url: String, tabId: String, permission: String): Boolean
    fun sitePermissionGranted(url: String, tabId: String, permission: String)
    fun sitePermissionsWebsitesFlow(): Flow<List<SitePermissionsEntity>>
    fun sitePermissionsForAllWebsites(): List<SitePermissionsEntity>
    fun sitePermissionsAllowedFlow(): Flow<List<SitePermissionAllowedEntity>>
    fun getDrmForSession(domain: String): Boolean?
    fun saveDrmForSession(domain: String, allowed: Boolean)
    suspend fun undoDeleteAll(sitePermissions: List<SitePermissionsEntity>, allowedSites: List<SitePermissionAllowedEntity>)
    suspend fun deleteAll()
    suspend fun getSitePermissionsForWebsite(url: String): SitePermissionsEntity?
    suspend fun deletePermissionsForSite(url: String)
    suspend fun savePermission(sitePermissionsEntity: SitePermissionsEntity)
}

@ContributesBinding(ActivityScope::class)
class SitePermissionsRepositoryImpl @Inject constructor(
    private val sitePermissionsDao: SitePermissionsDao,
    private val sitePermissionsAllowedDao: SitePermissionsAllowedDao,
    private val sitePermissionsPreferences: SitePermissionsPreferences,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SitePermissionsRepository {

    override var askCameraEnabled: Boolean
        get() = sitePermissionsPreferences.askCameraEnabled
        set(value) {
            sitePermissionsPreferences.askCameraEnabled = value
        }
    override var askMicEnabled: Boolean
        get() = sitePermissionsPreferences.askMicEnabled
        set(value) {
            sitePermissionsPreferences.askMicEnabled = value
        }

    override var askDrmEnabled: Boolean
        get() = sitePermissionsPreferences.askDrmEnabled
        set(value) {
            sitePermissionsPreferences.askDrmEnabled = value
        }

    private val drmSessions = mutableMapOf<String, Boolean>()

    override fun isDomainAllowedToAsk(url: String, permission: String): Boolean {
        val domain = url.extractDomain() ?: url
        val sitePermissionsForDomain = sitePermissionsDao.getSitePermissionsByDomain(domain)
        return when (permission) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                val isAskCameraSettingDenied = sitePermissionsForDomain?.askCameraSetting == SitePermissionAskSettingType.DENY_ALWAYS.name
                val isAskCameraDisabled =
                    askCameraEnabled || sitePermissionsForDomain?.askCameraSetting == SitePermissionAskSettingType.ALLOW_ALWAYS.name
                isAskCameraDisabled && !isAskCameraSettingDenied
            }
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                val isAskMicSettingDenied = sitePermissionsForDomain?.askMicSetting == SitePermissionAskSettingType.DENY_ALWAYS.name
                val isAskMicDisabled =
                    askMicEnabled || sitePermissionsForDomain?.askMicSetting == SitePermissionAskSettingType.ALLOW_ALWAYS.name
                isAskMicDisabled && !isAskMicSettingDenied
            }
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                val isAskDrmSettingDenied = sitePermissionsForDomain?.askDrmSetting == SitePermissionAskSettingType.DENY_ALWAYS.name
                val isAskDrmDisabled =
                    askDrmEnabled || sitePermissionsForDomain?.askDrmSetting == SitePermissionAskSettingType.ALLOW_ALWAYS.name
                isAskDrmDisabled && !isAskDrmSettingDenied
            }
            else -> false
        }
    }

    override fun isDomainGranted(url: String, tabId: String, permission: String): Boolean {
        val domain = url.extractDomain() ?: url
        val sitePermissionForDomain = sitePermissionsDao.getSitePermissionsByDomain(domain)
        val permissionAllowedEntity = sitePermissionsAllowedDao.getSitePermissionAllowed(domain, tabId, permission) // TODO: is this one trying to do something similar? Used by audio + video
        val permissionGrantedWithin24h = permissionAllowedEntity?.allowedWithin24h() == true

        return when (permission) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                val isCameraAlwaysAllowed = sitePermissionForDomain?.askCameraSetting == SitePermissionAskSettingType.ALLOW_ALWAYS.name
                permissionGrantedWithin24h || isCameraAlwaysAllowed
            }
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                val isMicAlwaysAllowed = sitePermissionForDomain?.askMicSetting == SitePermissionAskSettingType.ALLOW_ALWAYS.name
                permissionGrantedWithin24h || isMicAlwaysAllowed
            }
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                val isDrmAlwaysAllowed = sitePermissionForDomain?.askDrmSetting == SitePermissionAskSettingType.ALLOW_ALWAYS.name
                permissionGrantedWithin24h || isDrmAlwaysAllowed
            }
            else -> false
        }
    }

    override fun sitePermissionGranted(url: String, tabId: String, permission: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val domain = url.extractDomain() ?: url
            val existingPermission = sitePermissionsDao.getSitePermissionsByDomain(domain)
            if (existingPermission == null) {
                sitePermissionsDao.insert(SitePermissionsEntity(domain = domain))
            }
            val sitePermissionAllowed = SitePermissionAllowedEntity(
                domain,
                tabId,
                permission,
                System.currentTimeMillis(),
            )
            sitePermissionsAllowedDao.insert(sitePermissionAllowed)
        }
    }

    override fun sitePermissionsWebsitesFlow(): Flow<List<SitePermissionsEntity>> {
        return sitePermissionsDao.getAllSitesPermissionsAsFlow()
    }

    override fun sitePermissionsForAllWebsites(): List<SitePermissionsEntity> {
        return sitePermissionsDao.getAllSitesPermissions()
    }

    override fun sitePermissionsAllowedFlow(): Flow<List<SitePermissionAllowedEntity>> {
        return sitePermissionsAllowedDao.getAllSitesPermissionsAllowedAsFlow()
    }

    override fun getDrmForSession(domain: String): Boolean? {
        return drmSessions[domain]
    }

    override fun saveDrmForSession(domain: String, allowed: Boolean) {
        drmSessions[domain] = allowed
    }

    override suspend fun undoDeleteAll(sitePermissions: List<SitePermissionsEntity>, allowedSites: List<SitePermissionAllowedEntity>) {
        withContext(dispatcherProvider.io()) {
            sitePermissions.forEach { entity ->
                sitePermissionsDao.insert(entity)
            }
            allowedSites.forEach { entity ->
                sitePermissionsAllowedDao.insert(entity)
            }
        }
    }

    override suspend fun deleteAll() {
        sitePermissionsDao.deleteAll()
        sitePermissionsAllowedDao.deleteAll()
    }

    override suspend fun getSitePermissionsForWebsite(url: String): SitePermissionsEntity? {
        return withContext(dispatcherProvider.io()) {
            val domain = url.extractDomain() ?: url
            sitePermissionsDao.getSitePermissionsByDomain(domain)
        }
    }

    override suspend fun deletePermissionsForSite(url: String) {
        return withContext(dispatcherProvider.io()) {
            val domain = url.extractDomain() ?: url
            val entity = sitePermissionsDao.getSitePermissionsByDomain(domain)
            entity?.let { sitePermissionsDao.delete(it) }
            sitePermissionsAllowedDao.deleteAllowedSitesForDomain(domain)
        }
    }

    override suspend fun savePermission(sitePermissionsEntity: SitePermissionsEntity) {
        withContext(dispatcherProvider.io()) {
            sitePermissionsDao.insert(sitePermissionsEntity)
        }
    }
}
