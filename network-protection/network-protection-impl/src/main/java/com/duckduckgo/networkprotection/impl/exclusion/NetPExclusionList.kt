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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class NetPExclusionList @Inject constructor(
    private val packageManager: PackageManager,
    private val dispatcherProvider: DispatcherProvider,
    private val netPExclusionListRepository: NetPExclusionListRepository,
) : ExclusionList {

    private var installedApps: Sequence<ApplicationInfo> = emptySequence()

    override suspend fun getAppsAndProtectionInfo(): Flow<List<TrackingProtectionAppInfo>> {
        return netPExclusionListRepository.getManualAppExclusionListFlow()
            .map { userExclusionList ->
                installedApps
                    .map { appInfo ->
                        val isExcluded = shouldBeExcluded(appInfo, userExclusionList)
                        TrackingProtectionAppInfo(
                            packageName = appInfo.packageName,
                            name = packageManager.getApplicationLabel(appInfo).toString(),
                            type = appInfo.getAppType(),
                            category = appInfo.parseAppCategory(),
                            isExcluded = isExcluded,
                            knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
                            userModified = isUserModified(appInfo.packageName, userExclusionList),
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                    .toList()
            }.onStart {
                refreshInstalledApps()
            }.flowOn(dispatcherProvider.io())
    }

    private fun refreshInstalledApps() {
        logcat { "Excluded Apps: refreshInstalledApps" }
        installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
    }

    private fun shouldBeExcluded(
        appInfo: ApplicationInfo,
        userExclusionList: List<NetPManuallyExcludedApp>,
    ): Boolean {
        val userExcludedApp = userExclusionList.find { it.packageId == appInfo.packageName }

        return userExcludedApp?.run {
            !userExcludedApp.isProtected
        } ?: false
    }

    private fun isUserModified(
        packageName: String,
        userExclusionList: List<NetPManuallyExcludedApp>,
    ): Boolean {
        val userExcludedApp = userExclusionList.find { it.packageId == packageName }
        return userExcludedApp != null
    }

    override suspend fun getExclusionAppsList(): List<String> {
        return withContext(dispatcherProvider.io()) {
            return@withContext netPExclusionListRepository.getManualAppExclusionList()
                .filter { !it.isProtected }
                .sortedBy { it.packageId }
                .map { it.packageId }
        }
    }

    override fun manuallyExcludedApps(): Flow<List<Pair<String, Boolean>>> {
        return netPExclusionListRepository.getManualAppExclusionListFlow().map { list -> list.map { it.packageId to it.isProtected } }
    }

    override suspend fun manuallyEnabledApp(packageName: String) {
        withContext(dispatcherProvider.io()) {
            netPExclusionListRepository.manuallyEnableApp(packageName)
        }
    }

    override suspend fun manuallyExcludeApp(packageName: String) {
        withContext(dispatcherProvider.io()) {
            netPExclusionListRepository.manuallyExcludeApp(packageName)
        }
    }

    override suspend fun restoreDefaultProtectedList() {
        withContext(dispatcherProvider.io()) {
            netPExclusionListRepository.restoreDefaultProtectedList()
        }
    }

    override suspend fun isAppInExclusionList(packageName: String): Boolean {
        logcat { "TrackingProtectionAppsRepository: Checking $packageName protection status" }
        val manualAppExclusionList = netPExclusionListRepository.getManualAppExclusionList()
        val isProtected = manualAppExclusionList.find { it.packageId == packageName }?.isProtected ?: true

        return !isProtected
    }
}
