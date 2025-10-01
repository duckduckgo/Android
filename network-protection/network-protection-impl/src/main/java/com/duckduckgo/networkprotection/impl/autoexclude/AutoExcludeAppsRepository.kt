/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.autoexclude

import android.content.pm.PackageManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.store.db.AutoExcludeDao
import com.duckduckgo.networkprotection.store.db.FlaggedIncompatibleApp
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AutoExcludeAppsRepository {
    /**
     * Returns a list of apps that should be shown in the auto exclude prompt.
     * An app can only be shown in the prompt once.
     * An installed app will be flagged if it is part of the auto exclude list and is not manually excluded by the user.
     *
     * This method is internally dispatched to be executed in IO.
     */
    suspend fun getAppsForAutoExcludePrompt(): List<VpnIncompatibleApp>

    /**
     * Marks an app that has been shown in the auto exclude prompt.
     * An app can only be shown in auto-exclude prompt ONLY once.
     *
     * This method is internally dispatched to be executed in IO.
     */
    fun markAppAsShown(app: VpnIncompatibleApp)

    /**
     * Marks a list of apps that has been shown in the auto exclude prompt.
     * An app can only be shown in auto-exclude prompt ONLY once.
     *
     * This method is internally dispatched to be executed in IO.
     */
    fun markAppsAsShown(app: List<VpnIncompatibleApp>)

    /**
     * Returns a list of apps that is is part of the auto exclude list
     *
     * This method is internally dispatched to be executed in IO.
     */
    suspend fun getAllIncompatibleApps(): List<VpnIncompatibleApp>

    /**
     * Returns a flow of list of apps that is is part of the auto exclude list
     */
    fun getAllIncompatibleAppPackagesFlow(): Flow<List<String>>

    /**
     * Returns a list of apps that is is part of the auto exclude list and is installed in this device
     *
     * This method is internally dispatched to be executed in IO.
     */
    suspend fun getInstalledIncompatibleApps(): List<VpnIncompatibleApp>

    /**
     * Returns if the app is part of the auto exclude list
     *
     * This method is internally dispatched to be executed in IO.
     */
    suspend fun isAppMarkedAsIncompatible(appPackage: String): Boolean
}

@ContributesBinding(AppScope::class)
class RealAutoExcludeAppsRepository @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val autoExcludeDao: AutoExcludeDao,
    private val packageManager: PackageManager,
) : AutoExcludeAppsRepository {

    private val autoExcludeList: Deferred<List<VpnIncompatibleApp>> = appCoroutineScope.async(start = CoroutineStart.LAZY) {
        autoExcludeDao.getAutoExcludeApps()
    }

    override suspend fun getAppsForAutoExcludePrompt(): List<VpnIncompatibleApp> {
        return withContext(dispatcherProvider.io()) {
            getInstalledIncompatibleApps().filter {
                !autoExcludeDao.getFlaggedIncompatibleApps().any { flaggedIncompatibleApp ->
                    it.packageName == flaggedIncompatibleApp.packageName
                }
            }
        }
    }

    override fun markAppAsShown(app: VpnIncompatibleApp) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            autoExcludeDao.insertFlaggedIncompatibleApps(
                FlaggedIncompatibleApp(
                    packageName = app.packageName,
                ),
            )
        }
    }

    override fun markAppsAsShown(app: List<VpnIncompatibleApp>) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            app.map {
                FlaggedIncompatibleApp(
                    packageName = it.packageName,
                )
            }.also {
                autoExcludeDao.insertFlaggedIncompatibleApps(it)
            }
        }
    }

    override suspend fun getInstalledIncompatibleApps(): List<VpnIncompatibleApp> {
        return withContext(dispatcherProvider.io()) {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA).map { it.packageName }

            autoExcludeList.await().filter {
                installedApps.contains(it.packageName)
            }
        }
    }

    override suspend fun getAllIncompatibleApps(): List<VpnIncompatibleApp> {
        return withContext(dispatcherProvider.io()) {
            autoExcludeList.await()
        }
    }

    override fun getAllIncompatibleAppPackagesFlow(): Flow<List<String>> {
        return flow {
            emit(autoExcludeList.await().map { it.packageName })
        }
    }

    override suspend fun isAppMarkedAsIncompatible(appPackage: String): Boolean {
        return withContext(dispatcherProvider.io()) {
            getAllIncompatibleApps().any { it.packageName == appPackage }
        }
    }
}
