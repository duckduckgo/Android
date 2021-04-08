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
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DeviceShieldExcludedApps {
    /** @return the list of installed apps currently excluded */
    fun getExclusionAppList(): List<VpnExcludedInstalledAppInfo>

    /** Remove the app to the exclusion list so that its traffic does not go through the VPN */
    fun removeFromExclusionList(packageName: String)

    /** Add the app to the exclusion list so that its traffic goes through the VPN */
    fun addToExclusionList(packageName: String)
}

@ContributesBinding(AppObjectGraph::class)
class RealDeviceShieldExcludedApps @Inject constructor(
    private val context: Context,
    private val packageManager: PackageManager
) : DeviceShieldExcludedApps {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("com.duckduckgo.mobile.android.vpn.exclusions", Context.MODE_MULTI_PROCESS)

    override fun getExclusionAppList(): List<VpnExcludedInstalledAppInfo> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.shouldBeInExclusionList() }
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
