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

package com.duckduckgo.app.browser.omnibar.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.browser.omnibar.datastore.SharedPreferencesOmnibarDataStore.Keys.KEY_OMNIBAR_TYPE
import com.duckduckgo.app.di.Omnibar
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.ui.omnibar.OmnibarType
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

interface OmnibarDataStore {
    suspend fun setOmnibarType(omnibarType: OmnibarType)

    val omnibarTypeFlow: Flow<OmnibarType>

    val omnibarType: OmnibarType
        get() = runBlocking {
            omnibarTypeFlow.first()
        }
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesOmnibarDataStore @Inject constructor(
    @Omnibar private val store: DataStore<Preferences>,
    private val settingsDataStore: SettingsDataStore,
) : OmnibarDataStore {
    private object Keys {
        val KEY_OMNIBAR_TYPE = stringPreferencesKey(name = "KEY_OMNIBAR_TYPE")
    }

    override suspend fun setOmnibarType(omnibarType: OmnibarType) {
        store.edit { it[KEY_OMNIBAR_TYPE] = omnibarType.typeName }
    }

    @Suppress("Deprecation")
    override val omnibarTypeFlow: Flow<OmnibarType> = store.data.map { preferences ->
        val positionName = preferences[KEY_OMNIBAR_TYPE] ?: settingsDataStore.omnibarPosition.name
        when (positionName) {
            OmnibarType.SINGLE_TOP.typeName -> OmnibarType.SINGLE_TOP
            OmnibarType.SINGLE_BOTTOM.typeName -> OmnibarType.SINGLE_BOTTOM
            OmnibarType.SPLIT.typeName -> OmnibarType.SPLIT
            else -> OmnibarType.SINGLE_TOP
        }
    }
}
