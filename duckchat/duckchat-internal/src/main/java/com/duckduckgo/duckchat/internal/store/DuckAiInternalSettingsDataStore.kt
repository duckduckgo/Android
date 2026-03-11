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

package com.duckduckgo.duckchat.internal.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DuckAiInternalSettingsDataStore {
    var customUrl: String?
}

@ContributesBinding(AppScope::class)
class DevDuckAiInternalSettingsDataStoreImpl @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : DuckAiInternalSettingsDataStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = false,
            migrate = false,
        )
    }

    override var customUrl: String?
        get() = preferences.getString(KEY_CUSTOM_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = preferences.edit { putString(KEY_CUSTOM_URL, value) }

    companion object {
        private const val FILENAME = "com.duckduckgo.duckchat.dev.settings"
        private const val KEY_CUSTOM_URL = "KEY_CUSTOM_DUCK_AI_URL"
    }
}
