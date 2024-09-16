/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.store

import android.content.SharedPreferences
import androidx.core.content.edit

interface SyncUnavailableStore {
    var isSyncUnavailable: Boolean
    var syncUnavailableSince: String
    var syncErrorCount: Int
    var userNotifiedAt: String
    fun clearError()
    fun clearAll()
}

class SyncUnavailableSharedPrefsStore
constructor(
    private val sharedPrefsProv: SharedPrefsProvider,
) : SyncUnavailableStore {

    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }

    @Synchronized
    private fun encryptedPreferences(): SharedPreferences {
        return sharedPrefsProv.getSharedPrefs(FILENAME)
    }

    override var isSyncUnavailable: Boolean
        get() = encryptedPreferences?.getBoolean(KEY_SYNC_UNAVAILABLE, false) ?: false
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putBoolean(KEY_SYNC_UNAVAILABLE, value)
            }
        }

    override var syncUnavailableSince: String
        get() = encryptedPreferences?.getString(KEY_SYNC_UNAVAILABLE_SINCE, "") ?: ""
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_SYNC_UNAVAILABLE_SINCE, value)
            }
        }

    override var syncErrorCount: Int
        get() = encryptedPreferences?.getInt(KEY_SYNC_ERROR_COUNT, 0) ?: 0
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putInt(KEY_SYNC_ERROR_COUNT, value)
            }
        }

    override var userNotifiedAt: String
        get() = encryptedPreferences?.getString(KEY_SYNC_LAST_NOTIFICATION_AT, "") ?: ""
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_SYNC_LAST_NOTIFICATION_AT, value)
            }
        }

    override fun clearError() {
        isSyncUnavailable = false
        syncErrorCount = 0
        syncUnavailableSince = ""
    }

    override fun clearAll() {
        encryptedPreferences?.edit(commit = true) { clear() }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.sync.unavailable.store.v1"
        private const val KEY_SYNC_UNAVAILABLE = "KEY_SYNC_UNAVAILABLE"
        private const val KEY_SYNC_UNAVAILABLE_SINCE = "KEY_SYNC_UNAVAILABLE_SINCE"
        private const val KEY_SYNC_ERROR_COUNT = "KEY_SYNC_ERROR_COUNT"
        private const val KEY_SYNC_LAST_NOTIFICATION_AT = "KEY_SYNC_LAST_NOTIFICATION_AT"
    }
}
