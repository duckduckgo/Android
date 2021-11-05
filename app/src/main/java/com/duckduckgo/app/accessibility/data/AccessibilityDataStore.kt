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
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

interface AccessibilitySettingsDataStore {
    val systemFontSize: Float
    var useSystemFontSize: Boolean
    val fontSize: Float
    var appFontSize: Float
    var forceZoom: Boolean
    fun settingsFlow(): Flow<AccessibilitySettings>
}

class AccessibilitySettingsSharedPreferences(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope
) : AccessibilitySettingsDataStore {

    private val accessibilityStateFlow = MutableStateFlow(AccessibilitySettings(useSystemFontSize, fontSize, forceZoom))

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override val fontSize: Float
        get() {
            return if (useSystemFontSize) {
                systemFontSize
            } else {
                if (appFontSize <= FONT_SIZE_DEFAULT) appFontSize else (appFontSize - (appFontSize - FONT_SIZE_DEFAULT) * SCALE_FACTOR)
            }
        }

    override val systemFontSize: Float
        get() = context.resources.configuration.fontScale * FONT_SIZE_DEFAULT

    override var appFontSize: Float
        get() = preferences.getFloat(KEY_FONT_SIZE, FONT_SIZE_DEFAULT)
        set(value) {
            preferences.edit { putFloat(KEY_FONT_SIZE, value) }
            emitNewValues()
        }

    override var forceZoom: Boolean
        get() = preferences.getBoolean(KEY_FORCE_ZOOM, false)
        set(enabled) {
            preferences.edit { putBoolean(KEY_FORCE_ZOOM, enabled) }
            emitNewValues()
        }

    override var useSystemFontSize: Boolean
        get() = preferences.getBoolean(KEY_SYSTEM_FONT_SIZE, true)
        set(enabled) {
            preferences.edit { putBoolean(KEY_SYSTEM_FONT_SIZE, enabled) }
            emitNewValues()
        }

    override fun settingsFlow() = accessibilityStateFlow.asStateFlow().onSubscription {
        Timber.i("AccessibilityActSettings: newSubscription")
        emitNewValues()
    }

    private fun emitNewValues() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            accessibilityStateFlow.emit(AccessibilitySettings(useSystemFontSize, fontSize, forceZoom))
            Timber.i("AccessibilityActSettings: new value emitted ${AccessibilitySettings(useSystemFontSize, fontSize, forceZoom)}")
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.accessibility.settings"
        const val KEY_SYSTEM_FONT_SIZE = "SYSTEM_FONT_SIZE"
        const val KEY_FORCE_ZOOM = "FORCE_ZOOM"
        const val KEY_FONT_SIZE = "FONT_SIZE"
        const val FONT_SIZE_DEFAULT = 100f
        const val SCALE_FACTOR = 0.2f
    }
}

data class AccessibilitySettings(
    val useSystemFontSize: Boolean,
    val fontSize: Float,
    val forceZoom: Boolean
)
