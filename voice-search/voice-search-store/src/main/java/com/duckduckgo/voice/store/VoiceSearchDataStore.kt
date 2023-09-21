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

package com.duckduckgo.voice.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface VoiceSearchDataStore {
    var permissionDeclinedForever: Boolean
    var userAcceptedRationaleDialog: Boolean
    var availabilityLogged: Boolean
    var isVoiceSearchEnabled: Boolean
    var noMicAccessDialogDeclined: Boolean
    var countVoiceSearchDismissed: Int
}

class SharedPreferencesVoiceSearchDataStore constructor(
    private val context: Context,
) : VoiceSearchDataStore {
    companion object {
        const val FILENAME = "com.duckduckgo.app.voice"
        const val KEY_DECLINED_PERMISSION_FOREVER = "KEY_DECLINED_PERMISSION_FOREVER"
        const val KEY_RATIONALE_DIALOG_ACCEPTED = "KEY_RATIONALE_DIALOG_ACCEPTED"
        const val KEY_VOICE_SEARCH_AVAILABILITY_LOGGED = "KEY_VOICE_SEARCH_AVAILABILITY_LOGGED"
        const val KEY_VOICE_SEARCH_ENABLED = "KEY_VOICE_SEARCH_ENABLED"
        const val KEY_VOICE_SEARCH_NO_MIC_ACCESS_DIALOG_DECLINED = "KEY_VOICE_SEARCH_NO_MIC_ACCESS_DIALOG_DECLINED"
        const val KEY_VOICE_SEARCH_DISMISSED = "KEY_VOICE_SEARCH_DISMISSED"
    }

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var permissionDeclinedForever: Boolean
        get() = preferences.getBoolean(KEY_DECLINED_PERMISSION_FOREVER, false)
        set(declined) {
            updateValue(KEY_DECLINED_PERMISSION_FOREVER, declined)
        }

    override var userAcceptedRationaleDialog: Boolean
        get() = preferences.getBoolean(KEY_RATIONALE_DIALOG_ACCEPTED, false)
        set(accept) {
            updateValue(KEY_RATIONALE_DIALOG_ACCEPTED, accept)
        }

    override var availabilityLogged: Boolean
        get() = preferences.getBoolean(KEY_VOICE_SEARCH_AVAILABILITY_LOGGED, false)
        set(value) {
            updateValue(KEY_VOICE_SEARCH_AVAILABILITY_LOGGED, value)
        }

    override var isVoiceSearchEnabled: Boolean
        get() = preferences.getBoolean(KEY_VOICE_SEARCH_ENABLED, true)
        set(value) {
            updateValue(KEY_VOICE_SEARCH_ENABLED, value)
        }
    override var noMicAccessDialogDeclined: Boolean
        get() = preferences.getBoolean(KEY_VOICE_SEARCH_NO_MIC_ACCESS_DIALOG_DECLINED, false)
        set(value) {
            updateValue(KEY_VOICE_SEARCH_NO_MIC_ACCESS_DIALOG_DECLINED, value)
        }

    override var countVoiceSearchDismissed: Int
        get() = preferences.getInt(KEY_VOICE_SEARCH_DISMISSED, 0)
        set(value) {
            updateValue(KEY_VOICE_SEARCH_DISMISSED, value)
        }

    private fun updateValue(
        key: String,
        value: Boolean,
    ) {
        preferences.edit(true) {
            putBoolean(key, value)
        }
    }

    private fun updateValue(
        key: String,
        value: Int,
    ) {
        preferences.edit(true) {
            putInt(key, value)
        }
    }
}
