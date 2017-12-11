/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor.store

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import javax.inject.Inject

class PrivacySettingsSharedPreferences @Inject constructor(private val context: Context) : PrivacySettingsStore {

    companion object {
        val name = "com.duckduckgo.app.privacymonitor.settings"
        val privacyOnKey = "com.duckduckgo.app.privacymonitor.privacyon"
    }

    override var privacyOn: Boolean
        get() = preferences.getBoolean(privacyOnKey, true)
        set(on) {
            val editor = preferences.edit()
            editor.putBoolean(privacyOnKey, on)
            editor.apply()
        }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(name, MODE_PRIVATE)
}