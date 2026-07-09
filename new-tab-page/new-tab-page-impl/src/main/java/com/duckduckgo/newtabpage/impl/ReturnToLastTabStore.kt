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

package com.duckduckgo.newtabpage.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.impl.RealReturnToLastTabStore.Keys.RETURN_TO_LAST_TAB_ENABLED
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Persists whether the return-to-last-tab hatch should be shown on the NTP after an idle return.
 * Defaults to enabled, so existing users keep seeing the hatch.
 */
interface ReturnToLastTabStore {
    val isEnabled: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}

private val Context.returnToLastTabStore: DataStore<Preferences> by preferencesDataStore(
    name = "return_to_last_tab_store",
)

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealReturnToLastTabStore @Inject constructor(
    private val context: Context,
) : ReturnToLastTabStore {

    override val isEnabled: Flow<Boolean>
        get() = context.returnToLastTabStore.data
            .map { prefs -> prefs[RETURN_TO_LAST_TAB_ENABLED] ?: true }
            .distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean) {
        context.returnToLastTabStore.edit { prefs -> prefs[RETURN_TO_LAST_TAB_ENABLED] = enabled }
    }

    internal object Keys {
        val RETURN_TO_LAST_TAB_ENABLED = booleanPreferencesKey(name = "RETURN_TO_LAST_TAB_ENABLED")
    }
}
