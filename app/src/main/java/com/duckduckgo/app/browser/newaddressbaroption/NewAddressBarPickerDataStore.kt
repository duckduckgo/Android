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

package com.duckduckgo.app.browser.newaddressbaroption

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.app.di.NewAddressBarPicker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NewAddressBarPickerDataStore {
    suspend fun setAsShown()
    suspend fun wasShown(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SharedPreferencesNewAddressBarPickerDataStore @Inject constructor(
    @NewAddressBarPicker private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : NewAddressBarPickerDataStore {

    private object Keys {
        val WAS_SHOWN_KEY = booleanPreferencesKey(name = "NEW_ADDRESS_BAR_PICKER_WAS_SHOWN")
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
}
