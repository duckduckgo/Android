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

package com.duckduckgo.networkprotection.internal.network

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.duckduckgo.common.utils.extensions.safeGetInstalledApplications
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.networkprotection.internal.feature.NetPInternalFeatureToggles
import javax.inject.Inject

class NetPInternalExclusionListProvider @Inject constructor(
    private val packageManager: PackageManager,
    private val netPInternalFeatureToggles: NetPInternalFeatureToggles,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val context: Context,
) {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    internal fun getExclusionList(): Set<String> {
        if (!netPInternalFeatureToggles.excludeSystemApps().isEnabled()) return excludeManuallySelectedApps()

        // returns the list of system apps for now
        return packageManager.safeGetInstalledApplications(context)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            .map { it.packageName }
            .toSet()
    }

    internal fun excludeSystemApp(packageName: String) {
        preferences.edit { putBoolean(packageName, true) }
    }

    internal fun includeSystemApp(packageName: String) {
        preferences.edit { remove(packageName) }
    }

    private fun excludeManuallySelectedApps(): Set<String> {
        return preferences.all.keys
    }
}

private const val FILENAME = "com.duckduckgo.netp.internal.excluded_system_apps.v1"
