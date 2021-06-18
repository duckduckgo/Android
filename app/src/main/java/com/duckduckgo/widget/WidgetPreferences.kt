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

package com.duckduckgo.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject

interface WidgetPreferences {
    fun widgetTheme(widgetId: Int): WidgetTheme
    fun saveWidgetSelectedTheme(widgetId: Int, theme: String)
    fun widgetSize(widgetId: Int): Pair<Int, Int>
    fun storeWidgetSize(widgetId: Int, columns: Int, rows: Int)
}

class AppWidgetThemePreferences @Inject constructor(private val context: Context): WidgetPreferences {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override fun widgetTheme(widgetId: Int): WidgetTheme {
        return WidgetTheme.valueOf(preferences.getString(keyForWidgetTheme(widgetId), WidgetTheme.LIGHT.toString()) ?: WidgetTheme.LIGHT.toString())
    }

    override fun saveWidgetSelectedTheme(widgetId: Int, theme: String) {
        preferences.edit(true) {
            putString(keyForWidgetTheme(widgetId), theme)
        }
    }

    override fun widgetSize(widgetId: Int): Pair<Int, Int> {
        return Pair(preferences.getInt("$SHARED_PREFS_WIDTH_KEY-$widgetId", 2),
        preferences.getInt("$SHARED_PREFS_HEIGHT_KEY-$widgetId", 2))
    }

    override fun storeWidgetSize(widgetId: Int, columns: Int, rows: Int) {
        preferences.edit(true) {
            putInt("$SHARED_PREFS_WIDTH_KEY-$widgetId", columns)
            putInt("$SHARED_PREFS_HEIGHT_KEY-$widgetId", rows)
        }
    }

    private fun keyForWidgetTheme(widgetId: Int): String {
        return "$SHARED_PREFS_THEME_KEY-$widgetId"
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.widget.theme"
        const val SHARED_PREFS_THEME_KEY = "SelectedTheme"
        const val SHARED_PREFS_WIDTH_KEY = "Width"
        const val SHARED_PREFS_HEIGHT_KEY = "Height"
    }
}