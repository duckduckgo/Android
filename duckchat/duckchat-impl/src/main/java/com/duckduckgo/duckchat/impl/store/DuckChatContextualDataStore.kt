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

package com.duckduckgo.duckchat.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.di.DuckChat
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface DuckChatContextualDataStore {
    suspend fun persistTabChatUrl(tabId: String, url: String)
    suspend fun getTabChatUrl(tabId: String): String?
    fun clearTabChatUrl(tabId: String)
    fun clearAll()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDuckChatContextualDataStore @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    moshi: Moshi,
) : DuckChatContextualDataStore {

    private object Keys {
        val TAB_CHAT_URLS = stringPreferencesKey(name = "DUCK_CHAT_CONTEXTUAL_TAB_URLS")
    }

    private val mapAdapter: JsonAdapter<Map<String, String>> =
        moshi.adapter(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                String::class.java,
            ),
        )

    override suspend fun persistTabChatUrl(tabId: String, url: String) {
        withContext(dispatchers.io()) {
            store.edit { prefs ->
                val updated =
                    load(prefs[Keys.TAB_CHAT_URLS])
                        .toMutableMap()
                        .apply { this[tabId] = url }
                prefs[Keys.TAB_CHAT_URLS] = mapAdapter.toJson(updated)
            }
        }
    }

    override suspend fun getTabChatUrl(tabId: String): String? {
        return withContext(dispatchers.io()) {
            val prefs = store.data.first()
            load(prefs[Keys.TAB_CHAT_URLS])[tabId]
        }
    }

    override fun clearTabChatUrl(tabId: String) {
        coroutineScope.launch(dispatchers.io()) {
            store.edit { prefs ->
                val updated =
                    load(prefs[Keys.TAB_CHAT_URLS])
                        .toMutableMap()
                        .apply { remove(tabId) }
                prefs[Keys.TAB_CHAT_URLS] = mapAdapter.toJson(updated)
            }
        }
    }

    override fun clearAll() {
        coroutineScope.launch(dispatchers.io()) {
            store.edit { prefs ->
                prefs.remove(Keys.TAB_CHAT_URLS)
            }
        }
    }

    private fun load(raw: String?): Map<String, String> {
        return raw?.let { runCatching { mapAdapter.fromJson(it) }.getOrNull() } ?: emptyMap()
    }
}
