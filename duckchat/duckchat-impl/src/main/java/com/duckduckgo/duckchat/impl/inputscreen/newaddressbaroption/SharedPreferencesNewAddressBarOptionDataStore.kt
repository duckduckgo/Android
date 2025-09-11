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

package com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.di.NewAddressBarOption
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

interface NewAddressBarOptionDataStore {
    suspend fun markAsShown()
    suspend fun getHasBeenShown(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SharedPreferencesNewAddressBarOptionDataStore @Inject constructor(
    @NewAddressBarOption private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : NewAddressBarOptionDataStore {

    private object Keys {
        val HAS_BEEN_SHOWN = booleanPreferencesKey(name = "NEW_ADDRESS_BAR_OPTION_SHOWN")
    }

    override suspend fun markAsShown() {
        withContext(dispatchers.io()) {
            dataStore.edit { prefs ->
                prefs[Keys.HAS_BEEN_SHOWN] = true
            }
        }
    }

    override suspend fun getHasBeenShown(): Boolean = withContext(dispatchers.io()) {
        dataStore.data.firstOrNull()?.let { it[Keys.HAS_BEEN_SHOWN] } ?: false
    }
}
