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

package com.duckduckgo.app.global

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.ThemingConstants.BROADCAST_THEME_CHANGED
import com.duckduckgo.app.settings.db.SettingsDataStore

fun Activity.applyTheme(settingsDataStore: SettingsDataStore): BroadcastReceiver? {
    if (!isThemeConfigurable()) {
        return null
    }
    setTheme(themeId(settingsDataStore))
    return registerForThemeChangeBroadcast()
}

private fun Activity.registerForThemeChangeBroadcast(): BroadcastReceiver {
    val manager = LocalBroadcastManager.getInstance(applicationContext)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            recreate()
        }
    }
    manager.registerReceiver(receiver, IntentFilter(BROADCAST_THEME_CHANGED))
    return receiver
}

fun Activity.sendThemeChangedBroadcast() {
    val manager = LocalBroadcastManager.getInstance(applicationContext)
    manager.sendBroadcast(Intent(BROADCAST_THEME_CHANGED))
}

private fun themeId(settingsDataStore: SettingsDataStore): Int {
    return when (settingsDataStore.lightThemeEnabled) {
        true -> R.style.AppTheme_Light
        false -> R.style.AppTheme_Dark
    }
}

private fun Activity.isThemeConfigurable(): Boolean {
    return manifestThemeId() == R.style.AppTheme_Dark || manifestThemeId() == R.style.AppTheme_Light
}

private fun Activity.manifestThemeId(): Int {
    return packageManager.getActivityInfo(componentName, 0).themeResource
}

object ThemingConstants {
    const val BROADCAST_THEME_CHANGED = "BROADCAST_THEME_CHANGED"
}
