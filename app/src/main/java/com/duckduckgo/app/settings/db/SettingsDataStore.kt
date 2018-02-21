/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.settings.db

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject


interface SettingsDataStore {
    var autoCompleteSuggestionsEnabled: Boolean
    var desktopSiteRequested: Boolean
}

class AppSettingsPreferencesStore @Inject constructor(private val context: Context) : SettingsDataStore {

    companion object {
        const val SHARED_PREFERENCES_FILENAME = "com.duckduckgo.app.settings_activity.settings"
        const val KEY_AUTOCOMPLETE_ENABLED = "AUTOCOMPLETE_ENABLED"
        const val KEY_DESKTOP_SITE_REQUESTED = "KEY_DESKTOP_SITE_REQUESTED"
    }

    override var autoCompleteSuggestionsEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOCOMPLETE_ENABLED, true)
        set(enabled) {
            preferences.edit()
                    .putBoolean(KEY_AUTOCOMPLETE_ENABLED, enabled)
                    .apply()
        }

    override var desktopSiteRequested: Boolean
    get() = preferences.getBoolean(KEY_DESKTOP_SITE_REQUESTED, false)
    set(desktopSiteRequested) {
        preferences.edit()
                .putBoolean(KEY_DESKTOP_SITE_REQUESTED, desktopSiteRequested)
                .apply()
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(SHARED_PREFERENCES_FILENAME, Context.MODE_PRIVATE)
}