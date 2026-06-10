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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import android.graphics.drawable.Drawable
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.text.RegexOption.IGNORE_CASE

data class DeviceApp(
    val shortName: String,
    val packageName: String,
    val launchIntent: Intent,
    private var icon: Drawable? = null,
) {
    fun retrieveIcon(packageManager: PackageManager): Drawable {
        return icon ?: packageManager.getApplicationIcon(packageName).also {
            icon = it
        }
    }
}

interface DeviceAppLookup {
    suspend fun query(query: String): List<DeviceApp>
    suspend fun refreshAppList(): List<DeviceApp>
}

class InstalledDeviceAppLookup(
    private val appListProvider: DeviceAppListProvider,
    private val dispatcherProvider: DispatcherProvider,
) : DeviceAppLookup {

    private var cachedApps: List<DeviceApp>? = null

    private val refreshMutex = Mutex()

    override suspend fun query(query: String): List<DeviceApp> = withContext(dispatcherProvider.io()) {
        if (query.isBlank()) return@withContext emptyList()

        val apps = refreshMutex.withLock {
            cachedApps ?: run {
                val freshApps = appListProvider.get()
                cachedApps = freshApps
                freshApps
            }
        }

        val escapedQuery = Regex.escape(query)
        val wordPrefixMatchingRegex = ".*\\b$escapedQuery.*".toRegex(IGNORE_CASE)
        apps.filter {
            it.shortName.matches(wordPrefixMatchingRegex)
        }.sortedWith(comparator(query))
    }

    private fun comparator(query: String): Comparator<DeviceApp> {
        return compareByDescending<DeviceApp> {
            it.shortName.startsWith(query, ignoreCase = true)
        }.thenBy {
            it.shortName
        }
    }

    override suspend fun refreshAppList(): List<DeviceApp> = withContext(dispatcherProvider.io()) {
        refreshMutex.withLock {
            appListProvider.get().also {
                cachedApps = it
            }
        }
    }
}

interface DeviceAppListProvider {
    suspend fun get(): List<DeviceApp>
}

@SuppressLint("DenyListedApi")
class InstalledDeviceAppListProvider(
    private val packageManager: PackageManager,
    private val dispatcherProvider: DispatcherProvider,
) : DeviceAppListProvider {

    override suspend fun get(): List<DeviceApp> = withContext(dispatcherProvider.io()) {
        val appsInfo = packageManager.getInstalledApplications(GET_META_DATA)

        appsInfo.map {
            val packageName = it.packageName
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@map null
            val shortName = it.loadLabel(packageManager).toString()
            DeviceApp(shortName, packageName, launchIntent)
        }.filterNotNull()
    }
}
