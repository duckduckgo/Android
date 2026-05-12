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

package com.duckduckgo.eventhub.impl.webevents

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.impl.di.WebEventsPrefs
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface WebEventsDataStore {
    fun getWebEventsConfigJson(): String
    suspend fun setWebEventsConfigJson(value: String)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealWebEventsDataStore @Inject constructor(
    @WebEventsPrefs private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : WebEventsDataStore {

    private object Keys {
        val WEB_EVENTS_RC = stringPreferencesKey(name = "WEB_EVENTS_RC")
    }

    @Volatile
    private var cachedWebEventsJson: String = EMPTY_JSON

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            store.data.collect { prefs ->
                cachedWebEventsJson = prefs[Keys.WEB_EVENTS_RC] ?: EMPTY_JSON
            }
        }
    }

    override fun getWebEventsConfigJson(): String = cachedWebEventsJson

    override suspend fun setWebEventsConfigJson(value: String) {
        cachedWebEventsJson = value
        store.edit { prefs -> prefs[Keys.WEB_EVENTS_RC] = value }
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
