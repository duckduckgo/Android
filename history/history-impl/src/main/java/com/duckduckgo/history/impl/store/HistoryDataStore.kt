/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.history.impl.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface HistoryDataStore {
    suspend fun isHistoryUserEnabled(default: Boolean): Boolean

    suspend fun setHistoryUserEnabled(value: Boolean)
}

class SharedPreferencesHistoryDataStore
constructor(
    private val context: Context,
) : HistoryDataStore {
    companion object {
        const val FILENAME = "com.duckduckgo.history"
        const val KEY_HISTORY_USER_ENABLED = "KEY_HISTORY_USER_ENABLED"
    }

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    override suspend fun isHistoryUserEnabled(default: Boolean): Boolean {
        return preferences.getBoolean(KEY_HISTORY_USER_ENABLED, default)
    }

    override suspend fun setHistoryUserEnabled(value: Boolean) {
        updateValue(KEY_HISTORY_USER_ENABLED, value)
    }

    private fun updateValue(
        key: String,
        value: Boolean,
    ) {
        preferences.edit(true) { putBoolean(key, value) }
    }
}
