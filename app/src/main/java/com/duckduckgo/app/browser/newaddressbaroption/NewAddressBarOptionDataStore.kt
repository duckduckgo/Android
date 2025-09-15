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

package com.duckduckgo.app.browser.newaddressbaroption

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.app.di.NewAddressBarOption
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

interface NewAddressBarOptionDataStore {
    suspend fun setAsShown()
    suspend fun wasShown(): Boolean

    suspend fun setAsValidated()
    suspend fun wasValidated(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SharedPreferencesNewAddressBarOptionDataStore @Inject constructor(
    @NewAddressBarOption private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : NewAddressBarOptionDataStore {

    private object Keys {
        val WAS_SHOWN_KEY = booleanPreferencesKey(name = "NEW_ADDRESS_BAR_OPTION_WAS_SHOWN")
        val WAS_VALIDATED_KEY = booleanPreferencesKey(name = "NEW_ADDRESS_BAR_OPTION_WAS_VALIDATED")
    }

    override suspend fun setAsShown() {
        withContext(dispatchers.io()) {
            dataStore.edit { prefs ->
                prefs[Keys.WAS_SHOWN_KEY] = true
            }
        }
    }

    override suspend fun wasShown(): Boolean = withContext(dispatchers.io()) {
        dataStore.data.firstOrNull()?.let { it[Keys.WAS_SHOWN_KEY] } ?: false
    }

    override suspend fun setAsValidated() {
        withContext(dispatchers.io()) {
            dataStore.edit { prefs ->
                prefs[Keys.WAS_VALIDATED_KEY] = true
            }
        }
    }

    override suspend fun wasValidated(): Boolean = withContext(dispatchers.io()) {
        dataStore.data.firstOrNull()?.let { it[Keys.WAS_VALIDATED_KEY] } ?: false
    }
}
