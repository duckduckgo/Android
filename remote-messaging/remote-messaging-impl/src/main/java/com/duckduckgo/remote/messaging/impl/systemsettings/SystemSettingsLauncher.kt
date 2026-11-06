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

package com.duckduckgo.remote.messaging.impl.systemsettings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.impl.systemsettings.SystemSettingsScreen.SAMSUNG_HOME_SCREEN
import dev.zacsweers.metro.ContributesBinding
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

interface SystemSettingsLauncher {
    fun launch(
        context: Context,
        screen: SystemSettingsScreen,
    )
}

@ContributesBinding(AppScope::class)
class RealSystemSettingsLauncher @Inject constructor() : SystemSettingsLauncher {

    override fun launch(
        context: Context,
        screen: SystemSettingsScreen,
    ) {
        runCatching { context.start(screen.intent()) }
            .recoverCatching { context.start(Intent(Settings.ACTION_SETTINGS)) }
            .onFailure { logcat(WARN) { "RMF: could not open system settings screen $screen" } }
    }

    private fun Context.start(intent: Intent) {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun SystemSettingsScreen.intent(): Intent = when (this) {
        SAMSUNG_HOME_SCREEN -> Intent(SAMSUNG_APPLICATION_SETTINGS_ACTION).apply {
            addCategory(SAMSUNG_APPLICATION_SETTINGS_CATEGORY)
            component = ComponentName(SAMSUNG_LAUNCHER_PACKAGE, SAMSUNG_HOME_SCREEN_SETTINGS_ACTIVITY)
            putExtra(SETTINGS_HIGHLIGHT_KEY, SAMSUNG_SHOW_SEARCH_ON_HOME_SCREEN_PREFERENCE)
        }
    }

    private companion object {
        const val SAMSUNG_LAUNCHER_PACKAGE = "com.sec.android.app.launcher"
        const val SAMSUNG_HOME_SCREEN_SETTINGS_ACTIVITY = "com.android.homescreen.settings.HomeScreenSettingsActivity"
        const val SAMSUNG_APPLICATION_SETTINGS_ACTION = "com.sec.android.intent.action.SEC_APPLICATION_SETTINGS"
        const val SAMSUNG_APPLICATION_SETTINGS_CATEGORY = "com.sec.android.intent.category.SEC_APPLICATION_SETTINGS"
        const val SAMSUNG_SHOW_SEARCH_ON_HOME_SCREEN_PREFERENCE = "pref_search_on_home_screen"
        const val SETTINGS_HIGHLIGHT_KEY = ":settings:fragment_args_key"
    }
}
