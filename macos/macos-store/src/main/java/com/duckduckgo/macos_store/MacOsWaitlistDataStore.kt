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

package com.duckduckgo.macos_store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface MacOsWaitlistDataStore {
    var inviteCode: String?
    var waitlistTimestamp: Int
    var waitlistToken: String?
    var sendNotification: Boolean
}

class MacOsWaitlistDataStoreSharedPreferences constructor(private val context: Context) :
    MacOsWaitlistDataStore {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override var waitlistTimestamp: Int
        get() = preferences.getInt(KEY_WAITLIST_TIMESTAMP, -1) ?: -1
        set(value) {
            preferences.edit(commit = true) {
                putInt(KEY_WAITLIST_TIMESTAMP, value)
            }
        }

    override var waitlistToken: String?
        get() = preferences.getString(KEY_WAITLIST_TOKEN, null)
        set(value) {
            preferences.edit(commit = true) {
                if (value == null) remove(KEY_WAITLIST_TOKEN)
                else putString(KEY_WAITLIST_TOKEN, value)
            }
        }

    override var inviteCode: String?
        get() = preferences.getString(KEY_INVITE_CODE, null)
        set(value) {
            preferences.edit(commit = true) {
                if (value == null) remove(KEY_INVITE_CODE)
                else putString(KEY_INVITE_CODE, value)
            }
        }

    override var sendNotification: Boolean
        get() = preferences.getBoolean(KEY_SEND_NOTIFICATION, false) ?: false
        set(value) {
            preferences.edit(commit = true) {
                putBoolean(KEY_SEND_NOTIFICATION, value)
            }
        }

    companion object {
        const val FILENAME = "com.duckduckgo.macos.store.waitlist"
        const val KEY_WAITLIST_TIMESTAMP = "KEY_WAITLIST_TIMESTAMP"
        const val KEY_WAITLIST_TOKEN = "KEY_WAITLIST_TOKEN"
        const val KEY_INVITE_CODE = "KEY_INVITE_CODE"
        const val KEY_SEND_NOTIFICATION = "KEY_SEND_NOTIFICATION"
    }
}
