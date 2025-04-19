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

package com.duckduckgo.networkprotection.impl.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NetpVpnSettingsDataStore {
    var customDns: String?
}

@ContributesBinding(AppScope::class)
class RealNetpVpnSettingsDataStore @Inject constructor(sharedPreferencesProvider: SharedPreferencesProvider) : NetpVpnSettingsDataStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = false)
    }

    override var customDns: String?
        get() = preferences.getString(KEY_NETP_CUSTOM_DNS, null)
        set(value) = preferences.edit { putString(KEY_NETP_CUSTOM_DNS, value) }

    companion object {
        private const val FILENAME = "com.duckduckgo.networkprotection.env.store.v1"
        private const val KEY_NETP_CUSTOM_DNS = "KEY_NETP_CUSTOM_DNS"
    }
}
