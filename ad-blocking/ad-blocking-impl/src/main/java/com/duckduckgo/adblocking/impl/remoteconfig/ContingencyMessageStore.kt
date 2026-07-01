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

package com.duckduckgo.adblocking.impl.remoteconfig

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

interface ContingencyMessageStore {
    /**
     * Whether the contingency message has already been shown to the user. Cached so it can be
     * read synchronously ([StateFlow.value]) from the UI thread.
     */
    val shown: StateFlow<Boolean>

    suspend fun setShown()

    suspend fun reset()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesContingencyMessageStore @Inject constructor(
    @ContingencyMessage private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ContingencyMessageStore {

    private object Keys {
        val CONTINGENCY_MESSAGE_SHOWN = booleanPreferencesKey(name = "CONTINGENCY_MESSAGE_SHOWN")
    }

    override val shown: StateFlow<Boolean> = store.data
        .map { prefs -> prefs[Keys.CONTINGENCY_MESSAGE_SHOWN] ?: false }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    override suspend fun setShown() {
        store.edit { prefs -> prefs[Keys.CONTINGENCY_MESSAGE_SHOWN] = true }
    }

    override suspend fun reset() {
        store.edit { prefs -> prefs.remove(Keys.CONTINGENCY_MESSAGE_SHOWN) }
    }
}
