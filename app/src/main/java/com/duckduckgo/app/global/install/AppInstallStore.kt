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

package com.duckduckgo.app.global.install

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface AppInstallStore {
    var installTimestamp: Long

    var widgetInstalled: Boolean

    var defaultBrowser: Boolean

    var newDefaultBrowserDialogCount: Int

    fun hasInstallTimestampRecorded(): Boolean
}

fun AppInstallStore.daysInstalled(): Long {
    return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installTimestamp)
}

class AppInstallSharedPreferences @Inject constructor(private val context: Context) : AppInstallStore {
    override var installTimestamp: Long
        get() = preferences.getLong(KEY_TIMESTAMP_UTC, 0L)
        set(timestamp) = preferences.edit { putLong(KEY_TIMESTAMP_UTC, timestamp) }

    override var widgetInstalled: Boolean
        get() = preferences.getBoolean(KEY_WIDGET_INSTALLED, false)
        set(widgetInstalled) = preferences.edit { putBoolean(KEY_WIDGET_INSTALLED, widgetInstalled) }

    override var defaultBrowser: Boolean
        get() = preferences.getBoolean(KEY_DEFAULT_BROWSER, false)
        set(defaultBrowser) = preferences.edit { putBoolean(KEY_DEFAULT_BROWSER, defaultBrowser) }

    override var newDefaultBrowserDialogCount: Int
        get() = preferences.getInt(ROLE_MANAGER_BROWSER_DIALOG_KEY, 0)
        set(defaultBrowser) = preferences.edit { putInt(ROLE_MANAGER_BROWSER_DIALOG_KEY, defaultBrowser) }

    override fun hasInstallTimestampRecorded(): Boolean = preferences.contains(KEY_TIMESTAMP_UTC)

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        @VisibleForTesting
        const val FILENAME = "com.duckduckgo.app.install.settings"
        const val KEY_TIMESTAMP_UTC = "INSTALL_TIMESTAMP_UTC"
        const val KEY_WIDGET_INSTALLED = "KEY_WIDGET_INSTALLED"
        const val KEY_DEFAULT_BROWSER = "KEY_DEFAULT_BROWSER"
        private const val ROLE_MANAGER_BROWSER_DIALOG_KEY = "ROLE_MANAGER_BROWSER_DIALOG_KEY"
    }
}
