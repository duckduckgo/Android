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

package com.duckduckgo.common.ui.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.ui.DuckDuckGoTheme
import javax.inject.Inject

class ThemingSharedPreferences @Inject constructor(
    private val context: Context,
    private val experimentalUIThemingFeature: ExperimentalUIThemingFeature,
) : ThemingDataStore {

    private val themePrefMapper = ThemePrefsMapper()

    override var theme: DuckDuckGoTheme
        get() = selectedThemeSavedValue()
        set(theme) = preferences.edit { putString(KEY_THEME, themePrefMapper.prefValue(theme)) }

    override fun isCurrentlySelected(theme: DuckDuckGoTheme): Boolean {
        return selectedThemeSavedValue() == theme
    }

    private fun selectedThemeSavedValue(): DuckDuckGoTheme {
        val savedValue = preferences.getString(KEY_THEME, null)
        return themePrefMapper.themeFrom(
            savedValue,
            DuckDuckGoTheme.SYSTEM_DEFAULT,
            experimentalUIThemingFeature.self().isEnabled(),
            experimentalUIThemingFeature.warmColors().isEnabled(),
        )
    }

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    private class ThemePrefsMapper {

        companion object {
            private const val THEME_LIGHT = "LIGHT"
            private const val THEME_DARK = "DARK"
            private const val THEME_SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
        }

        fun prefValue(theme: DuckDuckGoTheme) =
            when (theme) {
                DuckDuckGoTheme.SYSTEM_DEFAULT -> THEME_SYSTEM_DEFAULT
                DuckDuckGoTheme.LIGHT -> THEME_LIGHT
                else -> THEME_DARK
            }

        fun themeFrom(
            value: String?,
            defValue: DuckDuckGoTheme,
            isExperimentEnabled: Boolean,
            warmColors: Boolean,
        ) =
            when (value) {
                THEME_LIGHT -> if (isExperimentEnabled) {
                    if (warmColors) {
                        DuckDuckGoTheme.EXPERIMENT_LIGHT_WARM
                    } else {
                        DuckDuckGoTheme.EXPERIMENT_LIGHT_COOL
                    }
                } else {
                    DuckDuckGoTheme.LIGHT
                }

                THEME_DARK -> if (isExperimentEnabled) {
                    if (warmColors) {
                        DuckDuckGoTheme.EXPERIMENT_DARK_WARM
                    } else {
                        DuckDuckGoTheme.EXPERIMENT_DARK_COOL
                    }
                } else {
                    DuckDuckGoTheme.DARK
                }

                THEME_SYSTEM_DEFAULT -> DuckDuckGoTheme.SYSTEM_DEFAULT
                else -> defValue
            }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.settings_activity.settings"
        const val KEY_THEME = "THEME"
    }
}
