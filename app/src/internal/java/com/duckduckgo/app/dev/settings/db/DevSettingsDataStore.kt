/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.dev.settings.db

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DevSettingsDataStore {
    var nextTdsEnabled: Boolean
}

@ContributesBinding(AppScope::class)
class DevSettingsSharedPreferences @Inject constructor(private val context: Context) : DevSettingsDataStore {
    override var nextTdsEnabled: Boolean
        get() = preferences.getBoolean(KEY_NEXT_TDS_ENABLED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_NEXT_TDS_ENABLED, enabled) }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.app.dev_settings_activity.dev_settings"
        const val KEY_NEXT_TDS_ENABLED = "KEY_NEXT_TDS_ENABLED"
    }
}
