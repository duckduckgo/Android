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

package com.duckduckgo.app.browser.tabs.store

import android.os.Bundle
import android.os.Parcelable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.app.browser.tabs.store.BrowserTabsDataStore.Keys.TAB_STATE_KEYS
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface TabsDataStore {
    fun getState(): Bundle?
    suspend fun saveState(state: Parcelable)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class BrowserTabsDataStore @Inject constructor(
    @Tabs private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : TabsDataStore {

    private object Keys {
        val TAB_STATE_KEYS = stringSetPreferencesKey("TAB_STATE_KEYS")
    }

    override fun getState(): Bundle? {
        return sortingMode.value
    }

    override suspend fun saveState(state: Parcelable) {
        if (state !is Bundle) return
        store.edit { prefs ->
            prefs[TAB_STATE_KEYS] = state.keySet()
            state.keySet().forEach { key ->
                state.getString(key)?.let { stateValue ->
                    prefs[stringPreferencesKey(key)] = stateValue
                }
            }
        }
    }

    private val sortingMode: StateFlow<Bundle?> = store.data
        .map { prefs ->
            prefs[TAB_STATE_KEYS]?.let { keys ->
                val bundle = Bundle()
                keys.forEach { key ->
                    prefs[stringPreferencesKey(key)]?.let { stateValue ->
                        bundle.putString(key, stateValue)
                    }
                }
                bundle
            }
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)
}
