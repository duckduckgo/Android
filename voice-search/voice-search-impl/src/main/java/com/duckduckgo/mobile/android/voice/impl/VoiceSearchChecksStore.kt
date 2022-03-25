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

package com.duckduckgo.mobile.android.voice.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VoiceSearchChecksStore {
    fun hasPermissionDeclinedForever(): Boolean
    fun declinePermissionForever(declined: Boolean)
    fun hasAcceptedRationaleDialog(): Boolean
    fun acceptRationaleDialog(accept: Boolean)
    fun hasLoggedAvailability(): Boolean
    fun saveLoggedAvailability()
}

@ContributesBinding(AppScope::class)
class SharedPreferencesVoiceSearchChecksStore @Inject constructor(
    val context: Context
) : VoiceSearchChecksStore {
    companion object {
        const val FILENAME = "com.duckduckgo.app.voice"
        const val KEY_DECLINED_PERMISSION_FOREVER = "KEY_DECLINED_PERMISSION_FOREVER"
        const val KEY_RATIONALE_DIALOG_ACCEPTED = "KEY_RATIONALE_DIALOG_ACCEPTED"
        const val KEY_VOICE_SEARCH_AVAILABILITY_LOGGED = "KEY_VOICE_SEARCH_AVAILABILITY_LOGGED"
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override fun hasPermissionDeclinedForever(): Boolean = preferences.getBoolean(KEY_DECLINED_PERMISSION_FOREVER, false)

    override fun declinePermissionForever(declined: Boolean) = preferences.edit(true) {
        putBoolean(KEY_DECLINED_PERMISSION_FOREVER, declined)
    }

    override fun hasAcceptedRationaleDialog(): Boolean = preferences.getBoolean(KEY_RATIONALE_DIALOG_ACCEPTED, false)

    override fun acceptRationaleDialog(accept: Boolean) = preferences.edit(true) {
        putBoolean(KEY_RATIONALE_DIALOG_ACCEPTED, accept)
    }

    override fun hasLoggedAvailability(): Boolean = preferences.getBoolean(KEY_VOICE_SEARCH_AVAILABILITY_LOGGED, false)

    override fun saveLoggedAvailability() = preferences.edit {
        putBoolean(KEY_VOICE_SEARCH_AVAILABILITY_LOGGED, true)
    }
}
