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

package com.duckduckgo.privacy.config.internal.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DevPrivacyConfigSettingsDataStore {
    var remotePrivacyConfigUrl: String?
    var useCustomPrivacyConfigUrl: Boolean
    var canUrlBeChanged: Boolean
}

@ContributesBinding(AppScope::class)
class DevPrivacyConfigSettingsDataStoreImpl @Inject constructor(private val context: Context) : DevPrivacyConfigSettingsDataStore {

    override var remotePrivacyConfigUrl: String?
        get() = selectedRemotePrivacyConfigUrlSavedValue()
        set(value) = preferences.edit { putString(KEY_SELECTED_REMOTE_PRIVACY_CONFIG, value) }

    override var useCustomPrivacyConfigUrl: Boolean
        get() = preferences.getBoolean(KEY_CUSTOM_REMOTE_PRIVACY_CONFIG_ENABLED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_CUSTOM_REMOTE_PRIVACY_CONFIG_ENABLED, enabled) }

    override var canUrlBeChanged: Boolean
        get() = preferences.getBoolean(KEY_URL_CHANGED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_URL_CHANGED, enabled) }

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    private fun selectedRemotePrivacyConfigUrlSavedValue(): String? {
        return preferences.getString(KEY_SELECTED_REMOTE_PRIVACY_CONFIG, null)
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.privacy.config.internal.settings"
        private const val KEY_SELECTED_REMOTE_PRIVACY_CONFIG = "KEY_SELECTED_REMOTE_PRIVACY_CONFIG"
        private const val KEY_CUSTOM_REMOTE_PRIVACY_CONFIG_ENABLED = "KEY_CUSTOM_REMOTE_PRIVACY_CONFIG_ENABLED"
        private const val KEY_URL_CHANGED = "KEY_URL_CHANGED"
    }
}
