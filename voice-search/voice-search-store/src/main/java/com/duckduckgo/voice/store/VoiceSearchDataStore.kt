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
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface VoiceSearchDataStore {
    var userAcceptedRationaleDialog: Boolean
    var availabilityLogged: Boolean
    var countVoiceSearchDismissed: Int
    var lastSelectedMode: VoiceSearchMode

    fun isVoiceSearchEnabled(default: Boolean): Boolean
    fun setVoiceSearchEnabled(value: Boolean)

    /** Emits the current enabled value immediately, then again on every change to the enabled flag. */
    fun voiceSearchEnabledFlow(default: Boolean): Flow<Boolean>
}

class SharedPreferencesVoiceSearchDataStore constructor(
    private val context: Context,
) : VoiceSearchDataStore {
    companion object {
        const val FILENAME = "com.duckduckgo.app.voice"
        const val KEY_RATIONALE_DIALOG_ACCEPTED = "KEY_RATIONALE_DIALOG_ACCEPTED"
        const val KEY_VOICE_SEARCH_AVAILABILITY_LOGGED = "KEY_VOICE_SEARCH_AVAILABILITY_LOGGED"
        const val KEY_VOICE_SEARCH_ENABLED = "KEY_VOICE_SEARCH_ENABLED"
        const val KEY_VOICE_SEARCH_DISMISSED = "KEY_VOICE_SEARCH_DISMISSED"
        const val KEY_LAST_SELECTED_MODE = "KEY_LAST_SELECTED_MODE"
    }

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

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

    override fun isVoiceSearchEnabled(default: Boolean): Boolean {
        return preferences.getBoolean(KEY_VOICE_SEARCH_ENABLED, default)
    }

    override fun setVoiceSearchEnabled(value: Boolean) {
        updateValue(KEY_VOICE_SEARCH_ENABLED, value)
    }

    override fun voiceSearchEnabledFlow(default: Boolean): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == KEY_VOICE_SEARCH_ENABLED) {
                trySend(prefs.getBoolean(KEY_VOICE_SEARCH_ENABLED, default))
            }
        }
        trySend(preferences.getBoolean(KEY_VOICE_SEARCH_ENABLED, default))
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override var countVoiceSearchDismissed: Int
        get() = preferences.getInt(KEY_VOICE_SEARCH_DISMISSED, 0)
        set(value) {
            updateValue(KEY_VOICE_SEARCH_DISMISSED, value)
        }

    override var lastSelectedMode: VoiceSearchMode
        get() = VoiceSearchMode.fromValue(preferences.getInt(KEY_LAST_SELECTED_MODE, VoiceSearchMode.SEARCH.value))
        set(value) {
            updateValue(KEY_LAST_SELECTED_MODE, value.value)
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
