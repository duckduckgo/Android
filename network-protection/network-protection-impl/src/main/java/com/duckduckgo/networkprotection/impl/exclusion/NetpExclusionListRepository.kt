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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludeAppsRepository
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NetPExclusionListRepository {
    suspend fun getExcludedAppPackages(): List<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealNetPExclusionListRepository @Inject constructor(
    private val manualExclusionListRepository: NetPManualExclusionListRepository,
    private val autoExcludeAppsRepository: AutoExcludeAppsRepository,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
) : NetPExclusionListRepository {
    override suspend fun getExcludedAppPackages(): List<String> {
        return withContext(dispatcherProvider.io()) {
            val manuallyExcludedApps = manualExclusionListRepository.getManualAppExclusionList()
            val autoExcludeApps = if (isAutoExcludeEnabled()) {
                autoExcludeAppsRepository.getAllIncompatibleApps()
            } else {
                emptyList()
            }

            packageManager.getInstalledApplications(PackageManager.GET_META_DATA).asSequence()
                .filter { isExcludedFromVpn(it, manuallyExcludedApps, autoExcludeApps) }
                .sortedBy { it.name }
                .map { it.packageName }
                .toList()
        }
    }

    private fun isAutoExcludeEnabled(): Boolean {
        return netPSettingsLocalConfig.autoExcludeBrokenApps().isEnabled()
    }

    private fun isExcludedFromVpn(
        appInfo: ApplicationInfo,
        manualExclusionList: List<NetPManuallyExcludedApp>,
        autoExcludeList: List<VpnIncompatibleApp>,
    ): Boolean {
        val userExcludedApp = manualExclusionList.find { it.packageId == appInfo.packageName }
        if (userExcludedApp != null) {
            return !userExcludedApp.isProtected
        }

        return autoExcludeList.isNotEmpty() && autoExcludeList.any { it.packageName == appInfo.packageName }
    }
}
