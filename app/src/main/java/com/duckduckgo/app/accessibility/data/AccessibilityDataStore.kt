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

package com.duckduckgo.app.accessibility.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface AccessibilitySettingsDataStore {
    var fontSize: Float
    var forceZoom: Boolean
}

class AccessibilitySettingsSharedPreferences constructor(private val context: Context) : AccessibilitySettingsDataStore {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override var fontSize: Float
        get() = preferences.getFloat(KEY_FONT_SIZE, FONT_SIZE_DEFAULT)
        set(value) = preferences.edit { putFloat(KEY_FONT_SIZE, value) }

    override var forceZoom: Boolean
        get() = preferences.getBoolean(KEY_FORCE_ZOOM, false)
        set(enabled) = preferences.edit { putBoolean(KEY_FORCE_ZOOM, enabled) }

    companion object {
        const val FILENAME = "com.duckduckgo.app.accessibility.settings"
        const val KEY_FORCE_ZOOM = "FORCE_ZOOM"
        const val KEY_FONT_SIZE = "FONT_SIZE"
        const val FONT_SIZE_DEFAULT = 100f
    }
}
