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
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DevSettingsDataStore {
    var nextTdsEnabled: Boolean
    var overrideUA: Boolean
    var selectedUA: UAOverride
}

@ContributesBinding(AppObjectGraph::class)
class DevSettingsSharedPreferences @Inject constructor(private val context: Context) : DevSettingsDataStore {
    override var nextTdsEnabled: Boolean
        get() = preferences.getBoolean(KEY_NEXT_TDS_ENABLED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_NEXT_TDS_ENABLED, enabled) }

    override var overrideUA: Boolean
        get() = preferences.getBoolean(KEY_OVERRIDE_UA, false)
        set(enabled) = preferences.edit { putBoolean(KEY_OVERRIDE_UA, enabled) }

    override var selectedUA: UAOverride
        get() = selectedUASavedValue()
        set(value) = preferences.edit { putString(KEY_SELECTED_UA, value.name) }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    private fun selectedUASavedValue(): UAOverride {
        val savedValue = preferences.getString(KEY_SELECTED_UA, null) ?: return UAOverride.DDG
        return UAOverride.valueOf(savedValue)
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.dev_settings_activity.dev_settings"
        const val KEY_NEXT_TDS_ENABLED = "KEY_NEXT_TDS_ENABLED"
        const val KEY_OVERRIDE_UA = "KEY_OVERRIDE_UA"
        const val KEY_SELECTED_UA = "KEY_SELECTED_UA"
    }
}

enum class UAOverride {
    NO_APP_ID,
    NO_VERSION,
    CHROME,
    FIREFOX,
    DDG,
    WEBVIEW
}
