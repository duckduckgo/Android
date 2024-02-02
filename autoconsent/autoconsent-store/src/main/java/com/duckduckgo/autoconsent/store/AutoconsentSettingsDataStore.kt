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

package com.duckduckgo.autoconsent.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface AutoconsentSettingsDataStore {
    var userSetting: Boolean
    var firstPopupHandled: Boolean
}

class RealAutoconsentSettingsDataStore constructor(
    private val context: Context,
    private val onByDefault: Boolean,
) :
    AutoconsentSettingsDataStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var userSetting: Boolean
        get() = preferences.getBoolean(AUTOCONSENT_USER_SETTING, onByDefault)
        set(value) {
            preferences.edit(commit = true) {
                putBoolean(AUTOCONSENT_USER_SETTING, value)
            }
        }

    override var firstPopupHandled: Boolean
        get() = preferences.getBoolean(AUTOCONSENT_FIRST_POPUP_HANDLED, false)
        set(value) {
            preferences.edit(commit = true) {
                putBoolean(AUTOCONSENT_FIRST_POPUP_HANDLED, value)
            }
        }

    companion object {
        private const val FILENAME = "com.duckduckgo.autoconsent.store.settings"
        private const val AUTOCONSENT_USER_SETTING = "AutoconsentUserSetting"
        private const val AUTOCONSENT_FIRST_POPUP_HANDLED = "AutoconsentFirstPopupHandled"
    }
}
