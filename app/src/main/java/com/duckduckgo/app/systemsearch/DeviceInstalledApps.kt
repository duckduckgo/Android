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
import android.content.pm.ApplicationInfo.DisplayNameComparator
import android.content.pm.PackageManager
import androidx.annotation.WorkerThread

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

        val mainIntent = Intent(Intent.ACTION_MAIN)
        val mainActivities = packageManager.queryIntentActivities(mainIntent, 0)

        val appsInfo = mainActivities.map {
            it.activityInfo.packageName
        }.toSet().map {
            packageManager.getApplicationInfo(it, PackageManager.GET_META_DATA)
        }.sortedWith(DisplayNameComparator(packageManager))

        return appsInfo.map {
            val shortName = packageManager.getApplicationLabel(it).toString()
            val packageName = it.packageName
            val fullName = packageManager.getApplicationInfo(packageName, 0).className ?: return@map null
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@map null
            return@map DeviceApp(shortName, fullName, packageName, launchIntent)
        }.filterNotNull()
    }
}