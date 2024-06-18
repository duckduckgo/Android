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

package com.duckduckgo.duckplayer.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DuckPlayerDataStore {
    suspend fun getDuckPlayerRC(): String

    suspend fun setDuckPlayerRC(value: String)
}

@ContributesBinding(AppScope::class)
class SharedPreferencesDuckPlayerDataStore @Inject constructor(
    private val context: Context,
) : DuckPlayerDataStore {
    companion object {
        const val FILENAME = "com.duckduckgo.duckplayer"
        const val KEY_DUCK_PLAYER_REMOTE_CONFIG = "KEY_DUCK_PLAYER_REMOTE_CONFIG"
    }

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    override suspend fun getDuckPlayerRC(): String {
        return preferences.getString(KEY_DUCK_PLAYER_REMOTE_CONFIG, null) ?: ""
    }

    override suspend fun setDuckPlayerRC(value: String) {
        updateValue(KEY_DUCK_PLAYER_REMOTE_CONFIG, value)
    }

    private fun updateValue(
        key: String,
        value: String,
    ) {
        preferences.edit(true) { putString(key, value) }
    }
}
