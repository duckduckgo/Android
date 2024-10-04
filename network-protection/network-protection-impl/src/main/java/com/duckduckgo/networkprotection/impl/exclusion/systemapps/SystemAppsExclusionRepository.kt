/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.exclusion.systemapps

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.exclusion.SystemAppOverridesProvider
import com.duckduckgo.networkprotection.impl.exclusion.isSystemApp
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository.SystemAppCategory
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository.SystemAppCategory.Communication
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository.SystemAppCategory.Media
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository.SystemAppCategory.Networking
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository.SystemAppCategory.Others
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface SystemAppsExclusionRepository {
    suspend fun excludeCategory(category: SystemAppCategory)
    suspend fun includeCategory(category: SystemAppCategory)
    suspend fun isCategoryExcluded(category: SystemAppCategory): Boolean
    suspend fun getAvailableCategories(): Set<SystemAppCategory>

    suspend fun getExcludedCategories(): Set<SystemAppCategory>

    suspend fun getAllExcludedSystemApps(): Set<String>

    suspend fun restoreDefaults()

    suspend fun hasShownWarning(): Boolean
    suspend fun markWarningShown()

    sealed class SystemAppCategory(val name: String) {
        data object Communication : SystemAppCategory("Communication")
        data object Networking : SystemAppCategory("Networking")
        data object Media : SystemAppCategory("Media")
        data object Others : SystemAppCategory("Others")
    }
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSystemAppsExclusionRepository @Inject constructor(
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val packageManager: PackageManager,
    private val systemAppOverridesProvider: SystemAppOverridesProvider,
    private val dispatcherProvider: DispatcherProvider,
) : SystemAppsExclusionRepository {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = false,
            migrate = false,
        )
    }

    override suspend fun excludeCategory(category: SystemAppCategory) = withContext(dispatcherProvider.io()) {
        when (category) {
            is Communication -> netPSettingsLocalConfig.excludeSystemAppsCommunication()
            is Networking -> netPSettingsLocalConfig.excludeSystemAppsNetworking()
            is Media -> netPSettingsLocalConfig.excludeSystemAppsMedia()
            is Others -> netPSettingsLocalConfig.excludeSystemAppsOthers()
        }.setRawStoredState(State(true))
    }

    override suspend fun includeCategory(category: SystemAppCategory) = withContext(dispatcherProvider.io()) {
        when (category) {
            is Communication -> netPSettingsLocalConfig.excludeSystemAppsCommunication()
            is Networking -> netPSettingsLocalConfig.excludeSystemAppsNetworking()
            is Media -> netPSettingsLocalConfig.excludeSystemAppsMedia()
            is Others -> netPSettingsLocalConfig.excludeSystemAppsOthers()
        }.setRawStoredState(State(false))
    }

    override suspend fun isCategoryExcluded(category: SystemAppCategory): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext when (category) {
            is Communication -> netPSettingsLocalConfig.excludeSystemAppsCommunication()
            is Networking -> netPSettingsLocalConfig.excludeSystemAppsNetworking()
            is Media -> netPSettingsLocalConfig.excludeSystemAppsMedia()
            is Others -> netPSettingsLocalConfig.excludeSystemAppsOthers()
        }.isEnabled()
    }

    override suspend fun hasShownWarning(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext preferences.getBoolean(KEY_WARNING_SHOWN, false)
    }

    override suspend fun markWarningShown() = withContext(dispatcherProvider.io()) {
        preferences.edit(commit = true) {
            putBoolean(KEY_WARNING_SHOWN, true)
        }
    }

    override suspend fun getAvailableCategories(): Set<SystemAppCategory> = withContext(dispatcherProvider.io()) {
        return@withContext setOf(
            Communication,
            Networking,
            Media,
            Others,
        )
    }

    override suspend fun getExcludedCategories(): Set<SystemAppCategory> {
        return buildSet {
            getAvailableCategories().forEach {
                if (isCategoryExcluded(it)) add(it)
            }
        }
    }

    override suspend fun getAllExcludedSystemApps(): Set<String> = withContext(dispatcherProvider.io()) {
        return@withContext buildSet {
            if (isCategoryExcluded(Communication)) addAll(getCommunicationSystemApps())
            if (isCategoryExcluded(Networking)) addAll(getNetworkingSystemApps())
            if (isCategoryExcluded(Media)) addAll(getMediaSystemApps())
            if (isCategoryExcluded(Others)) addAll(getOtherSystemApps())
        }
    }

    override suspend fun restoreDefaults() {
        includeCategory(Communication)
        includeCategory(Networking)
        includeCategory(Media)
        includeCategory(Others)
    }

    private fun getCommunicationSystemApps(): Set<String> {
        return setOf(
            "com.android.calllogbackup",
            "com.android.cellbroadcastreceiver",
            "com.android.mms.service",
            "com.android.phone",
            "com.android.providers.contacts",
            "com.android.providers.telephony",
            "com.android.service.ims",
            "com.google.android.apps.messaging",
            "com.google.android.gms",
            "com.google.android.telephony",
            "org.codeaurora.ims",
            "com.google.android.cellbroadcastservice",
            "com.wsomacp",
            "com.samsung.android.incall.contentprovider",
            "com.android.carrierconfig",
            "com.android.stk",
            "com.samsung.android.app.telephonyui",
            "com.sec.imsservice",
            "com.samsung.android.smartcallprovider",
            "com.android.server.telecom",
            "com.samsung.android.callbgprovider",
        )
    }

    private fun getNetworkingSystemApps(): Set<String> {
        return setOf(
            "com.android.bluetooth",
            "com.android.nfc",
            "com.google.android.networkstack",
            "com.google.android.networkstack.tethering",
            "com.samsung.android.networkstack",
            "com.samsung.android.wifi.softapdualap.resources",
            "com.google.android.networkstack.tethering.overlay",
            "com.samsung.android.wifi.p2paware.resources",
            "com.samsung.android.wifi.softap.resource",
            "com.samsung.android.wifi.resources",
            "com.android.wifi.resources",
            "com.google.android.apps.carrier.carrierwifi",
        )
    }

    private fun getMediaSystemApps(): Set<String> {
        return setOf(
            "com.android.providers.media",
            "com.google.android.providers.media.module",
            "com.google.android.music",
            "com.google.android.videos",
        )
    }

    private fun getOtherSystemApps(): Set<String> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.isSystemApp() && !it.isSystemAppOveridden() && !it.isCategorized() }
            .map { it.packageName }
            .toSet()
    }

    private fun ApplicationInfo.isSystemAppOveridden() = systemAppOverridesProvider.getSystemAppOverridesList().contains(packageName)

    private fun ApplicationInfo.isCategorized(): Boolean {
        return getCommunicationSystemApps().contains(packageName) ||
            getNetworkingSystemApps().contains(packageName) ||
            getMediaSystemApps().contains(packageName)
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.networkprotection.exclusion.systemapps"
        private const val KEY_WARNING_SHOWN = "KEY_SYSTEM_APPS_WARNING_SHOWN"
    }
}
