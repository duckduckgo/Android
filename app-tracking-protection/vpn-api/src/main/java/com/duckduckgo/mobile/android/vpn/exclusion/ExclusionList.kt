/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.exclusion

import com.duckduckgo.mobile.android.vpn.VpnFeature
import kotlinx.coroutines.flow.Flow

interface ExclusionList {
    /**
     * VPN feature to which this exclusion list should considered for
     */
    val forFeature: VpnFeature

    /**
     * @return the list of installed apps and information about its excluded state in the exclusion list for this VPNFeature
     */
    suspend fun getAppsAndProtectionInfo(): Flow<List<TrackingProtectionAppInfo>>

    /**
     * @return the list of installed apps currently excluded for the VpnFeature owning this exclusion list
     */
    suspend fun getExclusionAppsList(): List<String>

    /**
     * @return the all apps manually excluded for the VpnFeature owning this exclusion list
     */
    fun manuallyExcludedApps(): Flow<List<Pair<String, Boolean>>>

    /**
     * Remove the app from this exclusion list so that its traffic does not go through the VpnFeature associated to this exclusion list
     */
    suspend fun manuallyEnabledApp(packageName: String)

    /**
     * Add the app to this exclusion list so that its traffic goes through the VpnFeature associated to this exclusion list
     */
    suspend fun manuallyExcludeApp(packageName: String)

    /**
     * Restore protection to the default list of the VpnFeature associated to this exclusion list
     */
    suspend fun restoreDefaultProtectedList()

    /**
     * Returns if an app tracking attempts are being blocked or not
     */
    suspend fun isVpnFeatureEnabledForApp(packageName: String): Boolean
}
