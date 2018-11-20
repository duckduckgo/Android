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
import androidx.core.content.edit
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption
import javax.inject.Inject

interface SettingsDataStore {
    var theme: DuckDuckGoTheme?
    var autoCompleteSuggestionsEnabled: Boolean
    var appUsedSinceLastClear: Boolean
    var automaticallyClearWhatOption: ClearWhatOption
    var automaticallyClearWhenOption: ClearWhenOption
    var appBackgroundedTimestamp: Long
    fun hasBackgroundTimestampRecorded(): Boolean
    fun clearAppBackgroundTimestamp()
}

class SettingsSharedPreferences @Inject constructor(private val context: Context) : SettingsDataStore {

    override var theme: DuckDuckGoTheme?
        get() {
            val themeName = preferences.getString(KEY_THEME, null) ?: return null
            return DuckDuckGoTheme.valueOf(themeName)
        }
        set(theme) = preferences.edit { putString(KEY_THEME, theme.toString()) }

    override var autoCompleteSuggestionsEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOCOMPLETE_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_AUTOCOMPLETE_ENABLED, enabled) }

    override var appUsedSinceLastClear: Boolean
        get() = preferences.getBoolean(KEY_APP_USED_SINCE_LAST_CLEAR, true)
        set(enabled) = preferences.edit { putBoolean(KEY_APP_USED_SINCE_LAST_CLEAR, enabled) }


    override var automaticallyClearWhatOption: ClearWhatOption
        get() {
            val savedValue = preferences.getString(KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION, null) ?: ClearWhatOption.CLEAR_NONE.name
            return ClearWhatOption.valueOf(savedValue)
        }
        set(value) = preferences.edit { putString(KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION, value.name) }

    override var automaticallyClearWhenOption: ClearWhenOption
        get() {
            val savedValue = preferences.getString(KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION, null) ?: ClearWhenOption.APP_EXIT_ONLY.name
            return ClearWhenOption.valueOf(savedValue)
        }
        set(value) = preferences.edit { putString(KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION, value.name) }

    override var appBackgroundedTimestamp: Long
        get() = preferences.getLong(KEY_APP_BACKGROUNDED_TIMESTAMP, 0)
        set(value) = preferences.edit(commit = true) { putLong(KEY_APP_BACKGROUNDED_TIMESTAMP, value) }

    override fun hasBackgroundTimestampRecorded(): Boolean = preferences.contains(KEY_APP_BACKGROUNDED_TIMESTAMP)
    override fun clearAppBackgroundTimestamp() = preferences.edit { remove(KEY_APP_BACKGROUNDED_TIMESTAMP) }


    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.app.settings_activity.settings"
        const val KEY_THEME = "THEME"
        const val KEY_AUTOCOMPLETE_ENABLED = "AUTOCOMPLETE_ENABLED"
        const val KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION = "AUTOMATICALLY_CLEAR_WHAT_OPTION"
        const val KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION = "AUTOMATICALLY_CLEAR_WHEN_OPTION"
        const val KEY_APP_BACKGROUNDED_TIMESTAMP = "APP_BACKGROUNDED_TIMESTAMP"
        const val KEY_APP_USED_SINCE_LAST_CLEAR = "APP_USED_SINCE_LAST_CLEAR"
    }
}