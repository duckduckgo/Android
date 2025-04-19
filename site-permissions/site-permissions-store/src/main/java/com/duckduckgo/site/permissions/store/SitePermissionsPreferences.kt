/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.site.permissions.store

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject

interface SitePermissionsPreferences {

    var askCameraEnabled: Boolean
    var askMicEnabled: Boolean
    var askDrmEnabled: Boolean
    var askLocationEnabled: Boolean
}

class SitePermissionsPreferencesImp @Inject constructor(private val context: Context) : SitePermissionsPreferences {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var askCameraEnabled: Boolean
        get() = preferences.getBoolean(KEY_ASK_CAMERA_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_ASK_CAMERA_ENABLED, enabled) }

    override var askMicEnabled: Boolean
        get() = preferences.getBoolean(KEY_ASK_MIC_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_ASK_MIC_ENABLED, enabled) }

    override var askDrmEnabled: Boolean
        get() = preferences.getBoolean(KEY_ASK_DRM_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_ASK_DRM_ENABLED, enabled) }

    override var askLocationEnabled: Boolean
        get() = preferences.getBoolean(KEY_ASK_LOCATION_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_ASK_LOCATION_ENABLED, enabled) }

    companion object {
        const val FILENAME = "com.duckduckgo.site.permissions.settings"
        const val KEY_ASK_CAMERA_ENABLED = "ASK_CAMERA_ENABLED"
        const val KEY_ASK_MIC_ENABLED = "ASK_MIC_ENABLED"
        const val KEY_ASK_DRM_ENABLED = "ASK_DRM_ENABLED"
        const val KEY_ASK_LOCATION_ENABLED = "ASK_LOCATION_ENABLED"
    }
}

@SuppressLint("ApplySharedPref")
inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    action(editor)
    if (commit) {
        editor.commit()
    } else {
        editor.apply()
    }
}
