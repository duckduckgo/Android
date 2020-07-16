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
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import javax.inject.Inject

interface SettingsDataStore {

    var lastExecutedJobId: String?
    var theme: DuckDuckGoTheme?
    var hideTips: Boolean
    var autoCompleteSuggestionsEnabled: Boolean
    var appIcon: AppIcon
    var appIconChanged: Boolean
    var appLoginDetection: Boolean
    var appLocationPermission: Boolean
    var systemLocationPermissionDialogResponse: Boolean

    /**
     * This will be checked upon app startup and used to decide whether it should perform a clear or not.
     * If the app is cleared and the process restarted as a result, then we don't want to clear and restart again when the app launches.
     * To avoid this extra clear, we can indicate whether the app could have been used since the last clear or not.
     * If the app is cleared in the background, or the process is being restarted, we can set this to false.
     * If the app is cleared in the foreground, but the process isn't restarted, we can set this to true.
     */
    var appUsedSinceLastClear: Boolean
    var automaticallyClearWhatOption: ClearWhatOption
    var automaticallyClearWhenOption: ClearWhenOption
    var appBackgroundedTimestamp: Long
    var appNotificationsEnabled: Boolean
    fun isCurrentlySelected(clearWhatOption: ClearWhatOption): Boolean
    fun isCurrentlySelected(clearWhenOption: ClearWhenOption): Boolean
    fun hasBackgroundTimestampRecorded(): Boolean
    fun clearAppBackgroundTimestamp()
}

class SettingsSharedPreferences @Inject constructor(private val context: Context) : SettingsDataStore {

    override var lastExecutedJobId: String?
        get() = preferences.getString(KEY_BACKGROUND_JOB_ID, null)
        set(value) {
            preferences.edit(commit = true) {
                if (value == null) remove(KEY_BACKGROUND_JOB_ID)
                else putString(KEY_BACKGROUND_JOB_ID, value)
            }
        }

    override var theme: DuckDuckGoTheme?
        get() {
            val themeName = preferences.getString(KEY_THEME, null) ?: return null
            return DuckDuckGoTheme.valueOf(themeName)
        }
        set(theme) = preferences.edit { putString(KEY_THEME, theme.toString()) }

    override var hideTips: Boolean
        get() = preferences.getBoolean(KEY_HIDE_TIPS, false)
        set(enabled) = preferences.edit { putBoolean(KEY_HIDE_TIPS, enabled) }

    override var autoCompleteSuggestionsEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOCOMPLETE_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_AUTOCOMPLETE_ENABLED, enabled) }

    override var appLoginDetection: Boolean
        get() = preferences.getBoolean(KEY_LOGIN_DETECTION_ENABLED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_LOGIN_DETECTION_ENABLED, enabled) }

    override var appLocationPermission: Boolean
        get() = preferences.getBoolean(KEY_SITE_LOCATION_PERMISSION_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_SITE_LOCATION_PERMISSION_ENABLED, enabled) }

    override var systemLocationPermissionDialogResponse: Boolean
        get() = preferences.getBoolean(KEY_SYSTEM_LOCATION_PERMISSION_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_SYSTEM_LOCATION_PERMISSION_ENABLED, enabled) }

    override var appIcon: AppIcon
        get() {
            val componentName = preferences.getString(KEY_APP_ICON, DEFAULT_ICON.componentName) ?: return DEFAULT_ICON
            return AppIcon.from(componentName)
        }
        set(appIcon) = preferences.edit(commit = true) { putString(KEY_APP_ICON, appIcon.componentName) }

    // Changing the app icon makes the app close in some devices / OS versions. This is a problem if the user has
    // selected automatic data / tabs clear. We will use this flag to track if the user has changed the icon
    // and prevent the tabs / data from be cleared {check AutomaticDataClearer}
    override var appIconChanged: Boolean
        get() = preferences.getBoolean(KEY_APP_ICON_CHANGED, false)
        set(enabled) = preferences.edit(commit = true) { putBoolean(KEY_APP_ICON_CHANGED, enabled) }

    override var appUsedSinceLastClear: Boolean
        get() = preferences.getBoolean(KEY_APP_USED_SINCE_LAST_CLEAR, true)
        set(enabled) = preferences.edit(commit = true) { putBoolean(KEY_APP_USED_SINCE_LAST_CLEAR, enabled) }

    override var automaticallyClearWhatOption: ClearWhatOption
        get() = automaticallyClearWhatSavedValue() ?: ClearWhatOption.CLEAR_NONE
        set(value) = preferences.edit { putString(KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION, value.name) }

    override var automaticallyClearWhenOption: ClearWhenOption
        get() = automaticallyClearWhenSavedValue() ?: ClearWhenOption.APP_EXIT_ONLY
        set(value) = preferences.edit { putString(KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION, value.name) }

    override var appBackgroundedTimestamp: Long
        get() = preferences.getLong(KEY_APP_BACKGROUNDED_TIMESTAMP, 0)
        set(value) = preferences.edit(commit = true) { putLong(KEY_APP_BACKGROUNDED_TIMESTAMP, value) }

    override var appNotificationsEnabled: Boolean
        get() = preferences.getBoolean(KEY_APP_NOTIFICATIONS_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_APP_NOTIFICATIONS_ENABLED, enabled) }

    override fun hasBackgroundTimestampRecorded(): Boolean = preferences.contains(KEY_APP_BACKGROUNDED_TIMESTAMP)
    override fun clearAppBackgroundTimestamp() = preferences.edit { remove(KEY_APP_BACKGROUNDED_TIMESTAMP) }

    override fun isCurrentlySelected(clearWhatOption: ClearWhatOption): Boolean {
        val currentlySelected = automaticallyClearWhatSavedValue() ?: return false
        return currentlySelected == clearWhatOption
    }

    override fun isCurrentlySelected(clearWhenOption: ClearWhenOption): Boolean {
        val currentlySelected = automaticallyClearWhenSavedValue() ?: return false
        return currentlySelected == clearWhenOption
    }

    private fun automaticallyClearWhatSavedValue(): ClearWhatOption? {
        val savedValue = preferences.getString(KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION, null) ?: return null
        return ClearWhatOption.valueOf(savedValue)
    }

    private fun automaticallyClearWhenSavedValue(): ClearWhenOption? {
        val savedValue = preferences.getString(KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION, null) ?: return null
        return ClearWhenOption.valueOf(savedValue)
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.app.settings_activity.settings"
        const val KEY_BACKGROUND_JOB_ID = "BACKGROUND_JOB_ID"
        const val KEY_THEME = "THEME"
        const val KEY_AUTOCOMPLETE_ENABLED = "AUTOCOMPLETE_ENABLED"
        const val KEY_LOGIN_DETECTION_ENABLED = "KEY_LOGIN_DETECTION_ENABLED"
        const val KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION = "AUTOMATICALLY_CLEAR_WHAT_OPTION"
        const val KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION = "AUTOMATICALLY_CLEAR_WHEN_OPTION"
        const val KEY_APP_BACKGROUNDED_TIMESTAMP = "APP_BACKGROUNDED_TIMESTAMP"
        const val KEY_APP_NOTIFICATIONS_ENABLED = "APP_NOTIFCATIONS_ENABLED"
        const val KEY_APP_USED_SINCE_LAST_CLEAR = "APP_USED_SINCE_LAST_CLEAR"
        const val KEY_HIDE_TIPS = "HIDE_TIPS"
        const val KEY_APP_ICON = "APP_ICON"
        const val KEY_APP_ICON_CHANGED = "APP_ICON_CHANGED"
        const val KEY_SITE_LOCATION_PERMISSION_ENABLED = "KEY_SITE_LOCATION_PERMISSION_ENABLED"
        const val KEY_SYSTEM_LOCATION_PERMISSION_ENABLED = "KEY_SYSTEM_LOCATION_PERMISSION_ENABLED"

        private val DEFAULT_ICON = if (BuildConfig.DEBUG) {
            AppIcon.BLUE
        } else {
            AppIcon.DEFAULT
        }
        const val KEY_SEARCH_NOTIFICATION = "SEARCH_NOTIFICATION"
    }
}
