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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.Theming.Constants.BROADCAST_THEME_CHANGED
import com.duckduckgo.app.global.Theming.Constants.THEME_MAP
import com.duckduckgo.app.settings.db.SettingsDataStore


enum class DuckDuckGoTheme {
    DARK,
    LIGHT;
}

object Theming {

    fun initializeTheme(settingsDataStore: SettingsDataStore) {
        if (settingsDataStore.theme == null) {
            settingsDataStore.theme = DuckDuckGoTheme.DARK
        }
    }

    object Constants {

        const val BROADCAST_THEME_CHANGED = "BROADCAST_THEME_CHANGED"

        val THEME_MAP = mapOf(
            Pair(R.style.AppTheme, DuckDuckGoTheme.LIGHT) to R.style.AppTheme_Light,
            Pair(R.style.AppTheme, DuckDuckGoTheme.DARK) to R.style.AppTheme_Dark
        )
    }
}

fun DuckDuckGoActivity.applyTheme(): BroadcastReceiver? {
    val themeId = THEME_MAP[Pair(manifestThemeId(), settingsDataStore.theme)] ?: return null
    setTheme(themeId)
    return registerForThemeChangeBroadcast()
}

fun DuckDuckGoActivity.appTheme(): DuckDuckGoTheme? {
    return settingsDataStore.theme
}

private fun DuckDuckGoActivity.registerForThemeChangeBroadcast(): BroadcastReceiver {
    val manager = LocalBroadcastManager.getInstance(applicationContext)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            recreate()
        }
    }
    manager.registerReceiver(receiver, IntentFilter(BROADCAST_THEME_CHANGED))
    return receiver
}

fun DuckDuckGoActivity.sendThemeChangedBroadcast() {
    val manager = LocalBroadcastManager.getInstance(applicationContext)
    manager.sendBroadcast(Intent(BROADCAST_THEME_CHANGED))
}

private fun DuckDuckGoActivity.manifestThemeId(): Int {
    return packageManager.getActivityInfo(componentName, 0).themeResource
}
