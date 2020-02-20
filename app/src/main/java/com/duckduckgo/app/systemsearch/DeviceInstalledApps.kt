/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.systemsearch

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.performance.PerformanceConstants
import timber.log.Timber

data class DeviceApp(
    val shortName: String,
    val longName: String,
    val packageName: String,
    val launchIntent: Intent
)

class DeviceAppsLookup(private val packageManager: PackageManager) {

    private val allApps by lazy { all() }

    @WorkerThread
    fun query(query: String): List<DeviceApp> {

        if (query.isBlank()) return emptyList()

        return allApps.filter {
            it.shortName.contains(query, ignoreCase = true)
        }
    }

    @WorkerThread
    private fun all(): List<DeviceApp> {

        val appsInfo = packageManager.getInstalledApplications(GET_META_DATA)

        return appsInfo.map {
            val packageName = it.packageName
            val fullName = it.className ?: return@map null
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@map null
            val shortName = it.loadLabel(packageManager).toString()
            return@map DeviceApp(shortName, fullName, packageName, launchIntent)
        }.filterNotNull()

    }
}