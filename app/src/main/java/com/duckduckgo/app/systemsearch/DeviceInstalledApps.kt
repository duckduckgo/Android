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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import timber.log.Timber
import java.util.*

data class DeviceApp(
    val shortActivityName: String,
    val fullActivityName: String,
    val packageName: String,
    val launchIntent: Intent
)

class DeviceAppsLookup(private val packageManager: PackageManager) {

    private val allApps by lazy { all() }

    fun query(query: String): List<DeviceApp> {

        if (query.isBlank()) return emptyList()

        return allApps.filter {
            it.shortActivityName.contains(query, ignoreCase = true)
        }
    }

    private fun all(): List<DeviceApp> {
        val intent = Intent(Intent.ACTION_MAIN)
        val resInfos = packageManager.queryIntentActivities(intent, 0)
        val mainPackages = mutableSetOf<String>()
        val appInfos = mutableListOf<ApplicationInfo>()

        for (resInfo in resInfos) {
            val packageName = resInfo.activityInfo.packageName
            mainPackages.add(packageName)
        }

        for (packageName in mainPackages) {
            appInfos.add(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA))
        }

        Collections.sort(appInfos, ApplicationInfo.DisplayNameComparator(packageManager))

        Timber.i("Found ${appInfos.size} matching apps")

        val appsList: List<DeviceApp> = appInfos.map {
            val shortName = packageManager.getApplicationLabel(it).toString()
            val packageName = it.packageName
            val fullActivityName = packageManager.getApplicationInfo(packageName, 0).className
            val icon = it.icon
            Timber.i("Short name: $shortName, package: $packageName, full activity name: $fullActivityName")

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (fullActivityName == null) return@map null
            if (launchIntent == null) return@map null
            return@map DeviceApp(shortName, fullActivityName, packageName, launchIntent)

        }.filterNotNull()

        Timber.i("Found ${appsList.size} matching Activities")
        return appsList
    }
}