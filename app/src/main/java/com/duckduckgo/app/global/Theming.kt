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
import android.support.v4.content.LocalBroadcastManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoTheme.DARK
import com.duckduckgo.app.global.DuckDuckGoTheme.LIGHT
import com.duckduckgo.app.global.ThemingConstants.BROADCAST_THEME_CHANGED
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.ThemeFeature.LightThemeAsDefault


enum class DuckDuckGoTheme {
    DARK,
    LIGHT;
}

fun DuckDuckGoApplication.initializeTheme(settingsDataStore: SettingsDataStore, variant: Variant) {
    if (settingsDataStore.theme == null) {
        settingsDataStore.theme = if (variant.hasFeature(LightThemeAsDefault)) LIGHT else DARK
    }
}

fun DuckDuckGoActivity.applyTheme(settingsDataStore: SettingsDataStore, variant: Variant): BroadcastReceiver? {
    if (!isThemeConfigurable()) {
        return null
    }
    val themeId = when (settingsDataStore.theme) {
        LIGHT -> R.style.AppTheme_Light
        DARK -> R.style.AppTheme_Dark
        null -> if (variant.hasFeature(LightThemeAsDefault)) R.style.AppTheme_Light else R.style.AppTheme_Dark
    }
    setTheme(themeId)
    return registerForThemeChangeBroadcast()
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

private fun DuckDuckGoActivity.isThemeConfigurable(): Boolean {
    return manifestThemeId() == R.style.AppTheme_Dark || manifestThemeId() == R.style.AppTheme_Light
}

private fun DuckDuckGoActivity.manifestThemeId(): Int {
    return packageManager.getActivityInfo(componentName, 0).themeResource
}

object ThemingConstants {
    const val BROADCAST_THEME_CHANGED = "BROADCAST_THEME_CHANGED"
}
