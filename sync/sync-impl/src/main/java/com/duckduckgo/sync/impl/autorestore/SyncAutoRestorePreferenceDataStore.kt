/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.autorestore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.di.SyncAutoRestoreStore
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface SyncAutoRestorePreferenceDataStore {
    suspend fun isRestoreOnReinstallEnabled(): Boolean
    suspend fun setRestoreOnReinstallEnabled(enabled: Boolean)
}

@ContributesBinding(AppScope::class)
class RealSyncAutoRestorePreferenceDataStore @Inject constructor(
    @SyncAutoRestoreStore private val dataStore: DataStore<Preferences>,
) : SyncAutoRestorePreferenceDataStore {

    override suspend fun isRestoreOnReinstallEnabled(): Boolean {
        return dataStore.data.map { it[restoreOnReinstallKey] }.firstOrNull() ?: true
    }

    override suspend fun setRestoreOnReinstallEnabled(enabled: Boolean) {
        dataStore.edit { it[restoreOnReinstallKey] = enabled }
    }

    companion object {
        private val restoreOnReinstallKey = booleanPreferencesKey("restore_on_reinstall")
    }
}
