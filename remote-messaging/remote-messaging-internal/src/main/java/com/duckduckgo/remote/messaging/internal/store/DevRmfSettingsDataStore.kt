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

package com.duckduckgo.remote.messaging.internal.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DevRmfSettingsDataStore {
    var customRmfUrl: String?
    var useCustomRmfUrl: Boolean
}

@ContributesBinding(AppScope::class)
class DevRmfSettingsDataStoreImpl @Inject constructor(
    sharedPreferencesProvider: SharedPreferencesProvider,
) : DevRmfSettingsDataStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = false, migrate = false)
    }

    override var customRmfUrl: String?
        get() = preferences.getString(KEY_CUSTOM_URL, null)
        set(value) = preferences.edit { putString(KEY_CUSTOM_URL, value) }

    override var useCustomRmfUrl: Boolean
        get() = preferences.getBoolean(KEY_USE_CUSTOM_URL, false)
        set(value) = preferences.edit { putBoolean(KEY_USE_CUSTOM_URL, value) }

    companion object {
        private const val FILENAME = "com.duckduckgo.remote.messaging.internal.settings"
        private const val KEY_CUSTOM_URL = "KEY_CUSTOM_URL"
        private const val KEY_USE_CUSTOM_URL = "KEY_USE_CUSTOM_URL"
    }
}
