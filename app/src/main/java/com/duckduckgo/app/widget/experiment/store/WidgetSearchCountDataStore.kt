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

package com.duckduckgo.app.widget.experiment.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.duckduckgo.app.widget.experiment.di.WidgetSearchCount
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

interface WidgetSearchCountDataStore {
    suspend fun incrementWidgetSearchCount()
    suspend fun getWidgetSearchCount(): Int
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesWidgetSearchCountDataStore @Inject constructor(
    @WidgetSearchCount private val store: DataStore<Preferences>,
) : WidgetSearchCountDataStore {
    private object Keys {
        val WIDGET_SEARCH_COUNT = intPreferencesKey(name = "WIDGET_SEARCH_COUNT")
    }

    override suspend fun incrementWidgetSearchCount() {
        store.edit { preferences ->
            val currentCount = preferences[Keys.WIDGET_SEARCH_COUNT] ?: 0
            preferences[Keys.WIDGET_SEARCH_COUNT] = currentCount + 1
        }
    }

    override suspend fun getWidgetSearchCount(): Int {
        return store.data.map { preferences ->
            preferences[Keys.WIDGET_SEARCH_COUNT] ?: 0
        }.firstOrNull() ?: 0
    }
}
