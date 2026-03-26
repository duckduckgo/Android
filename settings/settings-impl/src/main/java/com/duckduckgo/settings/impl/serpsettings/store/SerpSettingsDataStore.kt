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

package com.duckduckgo.settings.impl.serpsettings.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.settings.impl.serpsettings.di.SerpSettings
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

interface SerpSettingsDataStore {

    suspend fun setSerpSettings(value: String)

    suspend fun getSerpSettings(): String?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SerpSettingsPrefsDataStore @Inject constructor(
    @SerpSettings private val store: DataStore<Preferences>,
) : SerpSettingsDataStore {

    override suspend fun setSerpSettings(value: String) {
        store.edit { prefs -> prefs[SERP_SETTINGS] = value }
    }

    override suspend fun getSerpSettings(): String? = store.data.firstOrNull()?.let { it[SERP_SETTINGS] }

    private companion object {
        private val SERP_SETTINGS = stringPreferencesKey(name = "SERP_SETTINGS")
    }
}
