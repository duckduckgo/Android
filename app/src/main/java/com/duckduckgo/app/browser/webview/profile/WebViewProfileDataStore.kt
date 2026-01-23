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

package com.duckduckgo.app.browser.webview.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface WebViewProfileDataStore {
    suspend fun getCurrentProfileIndex(): Int
    suspend fun incrementProfileIndex(): Int
    fun getProfileName(index: Int): String
    suspend fun getCurrentProfileName(): String

    companion object {
        const val PROFILE_PREFIX = "ddg_default_"
    }
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealWebViewProfileDataStore @Inject constructor(
    @WebViewProfileData private val store: DataStore<Preferences>,
) : WebViewProfileDataStore {

    override suspend fun getCurrentProfileIndex(): Int {
        return store.data.map { preferences ->
            preferences[KEY_PROFILE_INDEX] ?: 0
        }.firstOrNull() ?: 0
    }

    override suspend fun incrementProfileIndex(): Int {
        var newIndex = 0
        store.edit { preferences ->
            val currentIndex = preferences[KEY_PROFILE_INDEX] ?: 0
            newIndex = currentIndex + 1
            preferences[KEY_PROFILE_INDEX] = newIndex
        }
        return newIndex
    }

    override fun getProfileName(index: Int): String = "${WebViewProfileDataStore.PROFILE_PREFIX}$index"

    override suspend fun getCurrentProfileName(): String = getProfileName(getCurrentProfileIndex())

    companion object {
        val KEY_PROFILE_INDEX = intPreferencesKey("PROFILE_INDEX")
    }
}
