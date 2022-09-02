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
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.duckduckgo.site.permissions.store.SitePermissionsPreferencesImp
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.ALLOW_ALWAYS
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsDao
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity.Companion.allowedWithin24h
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionsAllowedDao
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SitePermissionsRepository {
    var askCameraEnabled: Boolean
    var askMicEnabled: Boolean
    fun isDomainAllowedToAsk(url: String, permission: String): Boolean
    fun isDomainGranted(url: String, tabId: String, permission: String): Boolean
    fun sitePermissionGranted(url: String, tabId: String, permission: String)
    fun sitePermissionsWebsitesFlow(): Flow<List<SitePermissionsEntity>>
    fun sitePermissionsForAllWebsites(): List<SitePermissionsEntity>
    suspend fun undoDeleteAll(sitePermissions: List<SitePermissionsEntity>)
    suspend fun deleteAll()
    suspend fun getSitePermissionsForWebsite(url: String): SitePermissionsEntity?
    suspend fun deletePermissionsForSite(url: String)
    suspend fun savePermission(sitePermissionsEntity: SitePermissionsEntity)
}

@ContributesBinding(ActivityScope::class)
class SitePermissionsRepositoryImpl @Inject constructor(
    private val sitePermissionsDao: SitePermissionsDao,
    private val sitePermissionsAllowedDao: SitePermissionsAllowedDao,
    private val sitePermissionsPreferences: SitePermissionsPreferencesImp,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
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

    override fun isDomainAllowedToAsk(url: String, permission: String): Boolean {
        val sitePermissionsForDomain = sitePermissionsDao.getSitePermissionsByDomain(url)
        return when (permission) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                val isAskCameraSettingDenied = sitePermissionsForDomain?.askCameraSetting == SitePermissionAskSettingType.DENY_ALWAYS.name
                askCameraEnabled && !isAskCameraSettingDenied
            }
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                val isAskMicSettingDenied = sitePermissionsForDomain?.askMicSetting == SitePermissionAskSettingType.DENY_ALWAYS.name
                askMicEnabled && !isAskMicSettingDenied
            }
            else -> false
        }
    }

    override fun isDomainGranted(url: String, tabId: String, permission: String): Boolean {
        val sitePermissionForDomain = sitePermissionsDao.getSitePermissionsByDomain(url)
        val permissionAllowedId = SitePermissionAllowedEntity.getPermissionAllowedId(url, tabId, permission)
        val permissionAllowedEntity = sitePermissionsAllowedDao.getSitePermissionAllowed(permissionAllowedId)
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
            else -> false
        }
    }

    override fun sitePermissionGranted(url: String, tabId: String, permission: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val existingPermission = sitePermissionsDao.getSitePermissionsByDomain(url)
            if (existingPermission == null) {
                sitePermissionsDao.insert(SitePermissionsEntity(domain = url))
            }
            val sitePermissionAllowed = SitePermissionAllowedEntity(
                SitePermissionAllowedEntity.getPermissionAllowedId(url, tabId, permission),
                url,
                tabId,
                permission,
                System.currentTimeMillis()
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

    override suspend fun undoDeleteAll(sitePermissions: List<SitePermissionsEntity>) {
        withContext(dispatcherProvider.io()) {
            sitePermissions.forEach { entity ->
                sitePermissionsDao.insert(entity)
            }
        }
    }
    override suspend fun deleteAll() {
        sitePermissionsDao.deleteAll()
    }

    override suspend fun getSitePermissionsForWebsite(url: String): SitePermissionsEntity? {
        return withContext(dispatcherProvider.io()) {
            sitePermissionsDao.getSitePermissionsByDomain(url)
        }
    }

    override suspend fun deletePermissionsForSite(url: String) {
        return withContext(dispatcherProvider.io()) {
            val entity = sitePermissionsDao.getSitePermissionsByDomain(url)
            entity?.let { sitePermissionsDao.delete(it) }
        }
    }

    override suspend fun savePermission(sitePermissionsEntity: SitePermissionsEntity) {
        withContext(dispatcherProvider.io()) {
            sitePermissionsDao.insert(sitePermissionsEntity)
        }
    }
}
