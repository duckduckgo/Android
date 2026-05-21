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

package com.duckduckgo.adblocking.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.adblocking.impl.di.AdBlockingPreferences
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface AdBlockingUserPreferences {

    fun isEnabledFlow(): Flow<Boolean?>
    suspend fun isEnabled(): Boolean?
    suspend fun setEnabled(enabled: Boolean)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAdBlockingUserPreferences @Inject constructor(
    @AdBlockingPreferences private val dataStore: DataStore<Preferences>,
) : AdBlockingUserPreferences {

    override fun isEnabledFlow(): Flow<Boolean?> = dataStore.data.map { prefs -> prefs[KEY_ENABLED] }

    override suspend fun isEnabled(): Boolean? = isEnabledFlow().first()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("ad_blocking_enabled")
    }
}
