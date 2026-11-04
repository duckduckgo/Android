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
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface AppInstallStore : MainProcessLifecycleObserver {
    var installTimestamp: Long

    var widgetInstalled: Boolean

    /** Setting to `true` also flips [wasEverDefaultBrowser] to `true` permanently. */
    var defaultBrowser: Boolean
    val wasEverDefaultBrowser: Boolean

    var defaultBrowserChangedSurveyDone: Boolean

    var newDefaultBrowserDialogCount: Int

    fun hasInstallTimestampRecorded(): Boolean
}

fun AppInstallStore.daysInstalled(): Long {
    return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installTimestamp)
}

class AppInstallSharedPreferences @Inject constructor(
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val deviceMigration: Lazy<DeviceMigration>, // break any possible DI dependency cycle
    private val defaultBrowserDetector: Lazy<DefaultBrowserDetector>, // break any possible DI dependency cycle
) : AppInstallStore {
    override var installTimestamp: Long
        get() = preferences.getLong(KEY_TIMESTAMP_UTC, 0L)
        set(timestamp) = preferences.edit { putLong(KEY_TIMESTAMP_UTC, timestamp) }

    override var widgetInstalled: Boolean
        get() = preferences.getBoolean(KEY_WIDGET_INSTALLED, false)
        set(widgetInstalled) = preferences.edit { putBoolean(KEY_WIDGET_INSTALLED, widgetInstalled) }

    override var defaultBrowser: Boolean
        get() = preferences.getBoolean(KEY_DEFAULT_BROWSER, false)
        set(defaultBrowser) = preferences.edit {
            putBoolean(KEY_DEFAULT_BROWSER, defaultBrowser)
            if (defaultBrowser) {
                putBoolean(KEY_WAS_EVER_DEFAULT_BROWSER, true)
            }
        }

    override val wasEverDefaultBrowser: Boolean
        get() = preferences.getBoolean(KEY_WAS_EVER_DEFAULT_BROWSER, false)

    override var defaultBrowserChangedSurveyDone: Boolean
        get() = preferences.getBoolean(KEY_DEFAULT_BROWSER_CHANGED_SURVEY_DONE, false)
        set(value) = preferences.edit { putBoolean(KEY_DEFAULT_BROWSER_CHANGED_SURVEY_DONE, value) }

    override var newDefaultBrowserDialogCount: Int
        get() = preferences.getInt(ROLE_MANAGER_BROWSER_DIALOG_KEY, 0)
        set(defaultBrowser) = preferences.edit { putInt(ROLE_MANAGER_BROWSER_DIALOG_KEY, defaultBrowser) }

    override fun hasInstallTimestampRecorded(): Boolean = preferences.contains(KEY_TIMESTAMP_UTC)

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE).also { resetDefaultBrowserHistoryAfterTransfer(it) }
    }

    /**
     * After a device transfer the default browser history describes the previous phone, where DuckDuckGo may have been the
     * default. Read as is, it would look like the user unset DuckDuckGo on this phone. It is reset to this phone's state once,
     * before any reader sees it, and regardless of the device migration kill switch because it keeps that signal correct.
     */
    private fun resetDefaultBrowserHistoryAfterTransfer(preferences: SharedPreferences) = synchronized(RESET_LOCK) {
        if (deviceMigration.value.isDoneOnThisInstall(DEFAULT_BROWSER_HISTORY_RESET_DONE_KEY)) return@synchronized
        // Synchronous stand-in for isMigratedInstall(): it is non-null exactly on a migrated install.
        if (deviceMigration.value.sourceDeviceIsNewInstall() == null) return@synchronized

        val isDefaultBrowser = defaultBrowserDetector.value.isDefaultBrowser()
        preferences.edit(commit = true) {
            putBoolean(KEY_DEFAULT_BROWSER, isDefaultBrowser)
            putBoolean(KEY_WAS_EVER_DEFAULT_BROWSER, isDefaultBrowser)
        }
        deviceMigration.value.markDoneOnThisInstall(DEFAULT_BROWSER_HISTORY_RESET_DONE_KEY)
    }

    @UiThread
    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat(LogPriority.INFO) { "recording installation timestamp" }
            if (!hasInstallTimestampRecorded()) {
                installTimestamp = System.currentTimeMillis()
            }
        }
    }

    companion object {
        @VisibleForTesting
        const val FILENAME = "com.duckduckgo.app.install.settings"
        const val KEY_TIMESTAMP_UTC = "INSTALL_TIMESTAMP_UTC"
        const val KEY_WIDGET_INSTALLED = "KEY_WIDGET_INSTALLED"
        const val KEY_DEFAULT_BROWSER = "KEY_DEFAULT_BROWSER"
        const val KEY_WAS_EVER_DEFAULT_BROWSER = "KEY_WAS_EVER_DEFAULT_BROWSER"
        const val KEY_DEFAULT_BROWSER_CHANGED_SURVEY_DONE = "KEY_DEFAULT_BROWSER_CHANGED_SURVEY_DONE"
        private const val ROLE_MANAGER_BROWSER_DIALOG_KEY = "ROLE_MANAGER_BROWSER_DIALOG_KEY"
        private const val DEFAULT_BROWSER_HISTORY_RESET_DONE_KEY = "app.defaultBrowserHistoryReset"

        // Instances are not shared, so the one-time reset is serialised across all of them.
        private val RESET_LOCK = Any()
    }
}
