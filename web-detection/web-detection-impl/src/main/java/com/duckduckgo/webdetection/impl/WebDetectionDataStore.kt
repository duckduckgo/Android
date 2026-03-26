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

package com.duckduckgo.webdetection.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
internal annotation class WebDetection

interface WebDetectionDataStore {
    fun getRemoteConfigJson(): String
    fun setRemoteConfigJson(value: String)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesWebDetectionDataStore @Inject constructor(
    @WebDetection private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : WebDetectionDataStore {

    private object Keys {
        val WEB_DETECTION_RC = stringPreferencesKey(name = "WEB_DETECTION_RC")
    }

    @Volatile
    private var cachedJson: String = EMPTY_JSON

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            store.data.collect { prefs ->
                cachedJson = prefs[Keys.WEB_DETECTION_RC] ?: EMPTY_JSON
            }
        }
    }

    override fun getRemoteConfigJson(): String {
        return cachedJson
    }

    override fun setRemoteConfigJson(value: String) {
        cachedJson = value
        appCoroutineScope.launch(dispatcherProvider.io()) {
            store.edit { prefs -> prefs[Keys.WEB_DETECTION_RC] = value }
        }
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
