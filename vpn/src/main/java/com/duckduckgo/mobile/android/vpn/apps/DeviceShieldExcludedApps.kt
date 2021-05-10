/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.apps

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceShieldExcludedApps {
    /** @return the list of installed apps currently excluded */
    suspend fun getExclusionAppList(): List<VpnExcludedInstalledAppInfo>

    /** Remove the app to the exclusion list so that its traffic does not go through the VPN */
    fun removeFromExclusionList(packageName: String)

    /** Add the app to the exclusion list so that its traffic goes through the VPN */
    fun addToExclusionList(packageName: String)
}

@ContributesBinding(AppObjectGraph::class)
@Singleton
class RealDeviceShieldExcludedApps @Inject constructor(
    private val context: Context,
    private val packageManager: PackageManager,
    private val appTrackerRepository: AppTrackerRepository,
    private val dispatcherProvider: DispatcherProvider
) : DeviceShieldExcludedApps {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("com.duckduckgo.mobile.android.vpn.exclusions", Context.MODE_MULTI_PROCESS)

    override suspend fun getExclusionAppList(): List<VpnExcludedInstalledAppInfo> = withContext(dispatcherProvider.io()) {
        val exclusionList = appTrackerRepository.getAppExclusionList()

        return@withContext packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { shouldBeInExclusionList(it, exclusionList) }
            .map {
                VpnExcludedInstalledAppInfo(
                    packageName = it.packageName,
                    name = packageManager.getApplicationLabel(it).toString(),
                    type = it.getAppType(),
                    category = it.parseAppCategory(),
                    isDdgApp = VpnExclusionList.isDdgApp(it.packageName),
                    isExcludedFromVpn = !isRemovedFromExclusionList(it.packageName)
                )
            }
            .sortedBy { it.name }
            .toList()
    }

    private fun shouldBeInExclusionList(appInfo: ApplicationInfo, exclusionList: List<String>): Boolean {
        return VpnExclusionList.isDdgApp(appInfo.packageName) ||
            exclusionList.contains(appInfo.packageName) ||
            appInfo.isGame()
    }

    override fun removeFromExclusionList(packageName: String) {
        preferences.edit { putBoolean(packageName, true) }
    }

    override fun addToExclusionList(packageName: String) {
        preferences.edit { putBoolean(packageName, false) }
    }

    private fun isRemovedFromExclusionList(packageName: String): Boolean {
        return preferences.getBoolean(packageName, false)
    }
}
