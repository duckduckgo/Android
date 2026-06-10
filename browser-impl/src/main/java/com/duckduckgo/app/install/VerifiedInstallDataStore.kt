/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.install

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface VerifiedInstallDataStore {
    suspend fun getLastInstalledVersion(): Int?
    suspend fun setLastInstalledVersion(version: Int?)
}

@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(AppScope::class)
class VerifiedInstallDataStoreImpl @Inject constructor(
    private val context: Context,
) : VerifiedInstallDataStore {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "verified_install_preferences",
    )

    override suspend fun getLastInstalledVersion(): Int? {
        return context.dataStore.data.map { preferences ->
            preferences[LAST_INSTALLED_VERSION]
        }.firstOrNull()
    }

    override suspend fun setLastInstalledVersion(version: Int?) {
        context.dataStore.edit { preferences ->
            if (version == null) {
                preferences.remove(LAST_INSTALLED_VERSION)
            } else {
                preferences[LAST_INSTALLED_VERSION] = version
            }
        }
    }

    companion object {
        private val LAST_INSTALLED_VERSION = intPreferencesKey("last_installed_version")
    }
}
