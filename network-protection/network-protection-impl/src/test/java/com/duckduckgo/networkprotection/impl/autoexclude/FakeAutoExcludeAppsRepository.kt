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

import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeAutoExcludeAppsRepository : AutoExcludeAppsRepository {
    private val _appsForAutoExcludePrompt = mutableListOf<VpnIncompatibleApp>()
    private val _incompatibleApps = mutableListOf<VpnIncompatibleApp>()

    fun setAppsForAutoExcludePrompt(apps: List<VpnIncompatibleApp>) {
        _appsForAutoExcludePrompt.clear()
        _appsForAutoExcludePrompt.addAll(apps)
    }

    fun setIncompatibleApps(apps: List<VpnIncompatibleApp>) {
        _incompatibleApps.clear()
        _incompatibleApps.addAll(apps)
    }

    override suspend fun getAppsForAutoExcludePrompt(): List<VpnIncompatibleApp> {
        return _appsForAutoExcludePrompt.toList()
    }

    override fun markAppAsShown(app: VpnIncompatibleApp) {
        _appsForAutoExcludePrompt.remove(app)
    }

    override fun markAppsAsShown(app: List<VpnIncompatibleApp>) {
        _appsForAutoExcludePrompt.removeAll(app)
    }

    override suspend fun getAllIncompatibleApps(): List<VpnIncompatibleApp> {
        return _incompatibleApps.toList()
    }

    override suspend fun getInstalledIncompatibleApps(): List<VpnIncompatibleApp> {
        return _incompatibleApps.toList()
    }

    override suspend fun isAppMarkedAsIncompatible(appPackage: String): Boolean {
        return _incompatibleApps.any { appPackage == it.packageName }
    }

    override fun getAllIncompatibleAppPackagesFlow(): Flow<List<String>> {
        return flowOf(_incompatibleApps.map { it.packageName })
    }
}
