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

package com.duckduckgo.networkprotection.impl.exclusion

import kotlinx.coroutines.flow.Flow

interface ExclusionList {
    /**
     * @return the list of installed apps and information about its excluded state
     */
    suspend fun getAppsAndProtectionInfo(): Flow<List<TrackingProtectionAppInfo>>

    /**
     * @return the list of installed apps currently excluded
     */
    suspend fun getExclusionAppsList(): List<String>

    /**
     * @return the list of installed apps manually excluded by the user
     */
    fun manuallyExcludedApps(): Flow<List<Pair<String, Boolean>>>

    /**
     * Remove the app to the exclusion list so that its traffic does not go through the VPN
     */
    suspend fun manuallyEnabledApp(packageName: String)

    /**
     * Add the app to the exclusion list so that its traffic goes through the VPN
     */
    suspend fun manuallyExcludeApp(packageName: String)

    /**
     * Restore protection to the default list
     */
    suspend fun restoreDefaultProtectedList()

    /**
     * Returns if an app is in the exclusion list
     */
    suspend fun isAppInExclusionList(packageName: String): Boolean
}
