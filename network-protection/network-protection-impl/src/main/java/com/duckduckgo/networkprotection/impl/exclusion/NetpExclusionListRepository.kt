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

package com.duckduckgo.networkprotection.impl.exclusion

import android.content.Context
import android.content.pm.PackageManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.safeGetInstalledApplications
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludeAppsRepository
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NetPExclusionListRepository {
    suspend fun getExcludedAppPackages(): List<String>

    suspend fun isExcluded(packageName: String): Boolean
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealNetPExclusionListRepository @Inject constructor(
    private val manualExclusionListRepository: NetPManualExclusionListRepository,
    private val autoExcludeAppsRepository: AutoExcludeAppsRepository,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
    private val context: Context,
) : NetPExclusionListRepository {
    override suspend fun getExcludedAppPackages(): List<String> {
        return withContext(dispatcherProvider.io()) {
            val manuallyExcludedApps = manualExclusionListRepository.getManualAppExclusionList()
            val autoExcludeApps = getAutoExcludeApps()

            packageManager.safeGetInstalledApplications(context).asSequence()
                .filter { isExcludedFromVpn(it.packageName, manuallyExcludedApps, autoExcludeApps) }
                .sortedBy { it.name }
                .map { it.packageName }
                .toList()
        }
    }

    // Callers check one package at a time (often once per installed app), so this must not enumerate installed apps
    override suspend fun isExcluded(packageName: String): Boolean {
        return withContext(dispatcherProvider.io()) {
            isExcludedFromVpn(packageName, manualExclusionListRepository.getManualAppExclusionList(), getAutoExcludeApps())
        }
    }

    private suspend fun getAutoExcludeApps(): List<VpnIncompatibleApp> {
        return if (isAutoExcludeEnabled()) {
            autoExcludeAppsRepository.getAllIncompatibleApps()
        } else {
            emptyList()
        }
    }

    private fun isAutoExcludeEnabled(): Boolean {
        return netPSettingsLocalConfig.autoExcludeBrokenApps().isEnabled()
    }

    private fun isExcludedFromVpn(
        packageName: String,
        manualExclusionList: List<NetPManuallyExcludedApp>,
        autoExcludeList: List<VpnIncompatibleApp>,
    ): Boolean {
        val userExcludedApp = manualExclusionList.find { it.packageId == packageName }
        if (userExcludedApp != null) {
            return !userExcludedApp.isProtected
        }

        return autoExcludeList.any { it.packageName == packageName }
    }
}
