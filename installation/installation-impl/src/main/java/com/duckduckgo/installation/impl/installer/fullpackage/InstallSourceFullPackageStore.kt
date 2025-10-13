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

package com.duckduckgo.installation.impl.installer.fullpackage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.installation.impl.installer.di.InstallerModule.InstallSourceFullPackageDataStore
import com.duckduckgo.installation.impl.installer.fullpackage.InstallSourceFullPackageStore.IncludedPackages
import com.duckduckgo.installation.impl.installer.fullpackage.feature.InstallSourceFullPackageListJsonParser
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface InstallSourceFullPackageStore {
    suspend fun updateInstallSourceFullPackages(json: String)
    suspend fun getInstallSourceFullPackages(): IncludedPackages

    data class IncludedPackages(val list: List<String> = emptyList()) {

        fun hasWildcard(): Boolean {
            return list.contains("*")
        }
    }
}

@ContributesBinding(AppScope::class, boundType = InstallSourceFullPackageStore::class)
@SingleInstanceIn(AppScope::class)
class InstallSourceFullPackageStoreImpl @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val jsonParser: InstallSourceFullPackageListJsonParser,
    @InstallSourceFullPackageDataStore private val dataStore: DataStore<Preferences>,
) : InstallSourceFullPackageStore {

    override suspend fun updateInstallSourceFullPackages(json: String) {
        withContext(dispatchers.io()) {
            val includedPackages = jsonParser.parseJson(json)
            dataStore.edit {
                it[packageInstallersKey] = includedPackages.list.toSet()
            }
        }
    }

    override suspend fun getInstallSourceFullPackages(): IncludedPackages {
        return withContext(dispatchers.io()) {
            val packageInstallers = dataStore.data.map { it[packageInstallersKey] }.firstOrNull()
            return@withContext IncludedPackages(packageInstallers?.toList() ?: emptyList())
        }
    }

    companion object {
        val packageInstallersKey = stringSetPreferencesKey("package_installers")
    }
}
