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

package com.duckduckgo.app.privacy.store

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import javax.inject.Inject

class PrivacySettingsSharedPreferences @Inject constructor(private val context: Context) : PrivacySettingsStore {

    override var privacyOn: Boolean
        get() = preferences.getBoolean(KEY_PRIVACY_ON, true)
        set(on) {
            val editor = preferences.edit()
            editor.putBoolean(KEY_PRIVACY_ON, on)
            editor.apply()
        }

    override var historicTrackerOptionRecorded: Boolean
        get() = preferences.getBoolean(KEY_PRIVACY_HISTORIC_TRACKING_OPTION_RECORDED, false)
        set(recorded) {
            val editor = preferences.edit()
            editor.putBoolean(KEY_PRIVACY_HISTORIC_TRACKING_OPTION_RECORDED, recorded)
            editor.apply()
        }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, MODE_PRIVATE)


    companion object {
        private const val FILENAME = "com.duckduckgo.app.privacymonitor.settings"
        private const val KEY_PRIVACY_ON = "com.duckduckgo.app.privacymonitor.privacyon"
        private const val KEY_PRIVACY_HISTORIC_TRACKING_OPTION_RECORDED = "com.duckduckgo.app.privacymonitor.historictrackingoptionrecorded"

    }
}