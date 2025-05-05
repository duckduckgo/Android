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
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SyncUnavailableStore {
    suspend fun isSyncUnavailable(): Boolean
    suspend fun setSyncUnavailable(value: Boolean)
    suspend fun getSyncUnavailableSince(): String
    suspend fun setSyncUnavailableSince(value: String)
    suspend fun getSyncErrorCount(): Int
    suspend fun setSyncErrorCount(value: Int)
    suspend fun getUserNotifiedAt(): String
    suspend fun setUserNotifiedAt(value: String)
    suspend fun clearError()
    suspend fun clearAll()
}

class SyncUnavailableSharedPrefsStore
constructor(
    private val sharedPrefsProv: SharedPrefsProvider,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val createAsyncPreferences: Boolean,
) : SyncUnavailableStore {

    @Synchronized
    private fun encryptedPreferencesSync(): SharedPreferences {
        return sharedPrefsProv.getSharedPrefs(FILENAME)
    }

    private suspend fun encryptedPreferencesAsync(): SharedPreferences {
        return mutex.withLock {
            sharedPrefsProv.getSharedPrefs(FILENAME)
        }
    }

    private val mutex: Mutex = Mutex()
    private val encryptedPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        appCoroutineScope.async(dispatcherProvider.io()) {
            encryptedPreferencesAsync()
        }
    }

    private val encryptedPreferencesSync: SharedPreferences? by lazy { encryptedPreferencesSync() }

    private suspend fun getEncryptedPreferences(): SharedPreferences? {
        return withContext(dispatcherProvider.io()) {
            if (createAsyncPreferences) encryptedPreferencesDeferred.await() else encryptedPreferencesSync
        }
    }

    override suspend fun isSyncUnavailable(): Boolean {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getBoolean(KEY_SYNC_UNAVAILABLE, false) ?: false
        }
    }

    override suspend fun setSyncUnavailable(value: Boolean) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                putBoolean(KEY_SYNC_UNAVAILABLE, value)
            }
        }
    }

    override suspend fun getSyncUnavailableSince(): String {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getString(KEY_SYNC_UNAVAILABLE_SINCE, "") ?: ""
        }
    }

    override suspend fun setSyncUnavailableSince(value: String) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                putString(KEY_SYNC_UNAVAILABLE_SINCE, value)
            }
        }
    }

    override suspend fun getSyncErrorCount(): Int {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getInt(KEY_SYNC_ERROR_COUNT, 0) ?: 0
        }
    }

    override suspend fun setSyncErrorCount(value: Int) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                putInt(KEY_SYNC_ERROR_COUNT, value)
            }
        }
    }

    override suspend fun getUserNotifiedAt(): String {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getString(KEY_SYNC_LAST_NOTIFICATION_AT, "") ?: ""
        }
    }

    override suspend fun setUserNotifiedAt(value: String) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                putString(KEY_SYNC_LAST_NOTIFICATION_AT, value)
            }
        }
    }

    override suspend fun clearError() {
        setSyncUnavailable(false)
        setSyncErrorCount(0)
        setSyncUnavailableSince("")
    }

    override suspend fun clearAll() {
        getEncryptedPreferences()?.edit(commit = true) { clear() }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.sync.unavailable.store.v1"
        private const val KEY_SYNC_UNAVAILABLE = "KEY_SYNC_UNAVAILABLE"
        private const val KEY_SYNC_UNAVAILABLE_SINCE = "KEY_SYNC_UNAVAILABLE_SINCE"
        private const val KEY_SYNC_ERROR_COUNT = "KEY_SYNC_ERROR_COUNT"
        private const val KEY_SYNC_LAST_NOTIFICATION_AT = "KEY_SYNC_LAST_NOTIFICATION_AT"
    }
}
