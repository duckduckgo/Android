/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.autocomplete.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutoCompleteDataStore {
    suspend fun setHistoryInAutoCompleteIAMDismissed()

    suspend fun wasHistoryInAutoCompleteIAMDismissed(): Boolean

    suspend fun incrementCountHistoryInAutoCompleteIAMShown()

    suspend fun countHistoryInAutoCompleteIAMShown(): Int
}

@ContributesBinding(AppScope::class)
class SharedPreferencesAutoCompleteDataStore @Inject constructor(
    private val context: Context,
) : AutoCompleteDataStore {
    companion object {
        const val FILENAME = "com.duckduckgo.app.autocomplete"
        const val KEY_HISTORY_AUTOCOMPLETE_SHOW_COUNT = "KEY_HISTORY_AUTOCOMPLETE_SHOW_COUNT"
        const val KEY_HISTORY_AUTOCOMPLETE_DISMISSED = "KEY_HISTORY_AUTOCOMPLETE_DISMISSED"
    }

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override suspend fun countHistoryInAutoCompleteIAMShown(): Int {
        return preferences.getInt(KEY_HISTORY_AUTOCOMPLETE_SHOW_COUNT, 0)
    }

    override suspend fun incrementCountHistoryInAutoCompleteIAMShown() {
        updateValue(KEY_HISTORY_AUTOCOMPLETE_SHOW_COUNT, countHistoryInAutoCompleteIAMShown() + 1)
    }
    override suspend fun setHistoryInAutoCompleteIAMDismissed() {
        updateValue(KEY_HISTORY_AUTOCOMPLETE_DISMISSED, true)
    }

    override suspend fun wasHistoryInAutoCompleteIAMDismissed(): Boolean {
        return preferences.getBoolean(KEY_HISTORY_AUTOCOMPLETE_DISMISSED, false)
    }

    private fun updateValue(
        key: String,
        value: Int,
    ) {
        preferences.edit(true) {
            putInt(key, value)
        }
    }

    private fun updateValue(
        key: String,
        value: Boolean,
    ) {
        preferences.edit(true) {
            putBoolean(key, value)
        }
    }
}
