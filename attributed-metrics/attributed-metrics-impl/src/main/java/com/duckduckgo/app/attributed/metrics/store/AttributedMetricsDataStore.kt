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

package com.duckduckgo.app.attributed.metrics.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.attributed.metrics.di.AttributedMetrics
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import javax.inject.Inject

interface AttributedMetricsDataStore {
    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)

    suspend fun getInitializationDate(): String?

    suspend fun setInitializationDate(date: String?)

    fun observeEnabled(): Flow<Boolean>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAttributedMetricsDataStore @Inject constructor(
    @AttributedMetrics private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AttributedMetricsDataStore {
    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val INIT_DATE = stringPreferencesKey("client_init_date")
    }

    private val enabledState: StateFlow<Boolean> =
        store.data
            .map { prefs -> prefs[Keys.IS_ENABLED] ?: false }
            .distinctUntilChanged()
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    override suspend fun isEnabled(): Boolean = store.data.firstOrNull()?.get(Keys.IS_ENABLED) ?: false

    override suspend fun setEnabled(enabled: Boolean) {
        store.edit { preferences ->
            preferences[Keys.IS_ENABLED] = enabled
        }
    }

    override suspend fun getInitializationDate(): String? = store.data.firstOrNull()?.get(Keys.INIT_DATE)

    override suspend fun setInitializationDate(date: String?) {
        store.edit { preferences ->
            if (date != null) {
                preferences[Keys.INIT_DATE] = date
            } else {
                preferences.remove(Keys.INIT_DATE)
            }
        }
    }

    override fun observeEnabled(): Flow<Boolean> = enabledState
}
