/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface CredentialsSyncStore {
    var serverModifiedSince: String
    var startTimeStamp: String
    var clientModifiedSince: String
    var isSyncPaused: Boolean
    var syncPausedReason: String
    fun isSyncPausedFlow(): Flow<Boolean>
    var invalidEntitiesIds: List<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealCredentialsSyncStore @Inject constructor(
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : CredentialsSyncStore {

    private val syncPausedSharedFlow = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = DROP_OLDEST)
    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            syncPausedSharedFlow.emit(isSyncPaused)
        }
    }

    override var serverModifiedSince: String
        get() = preferences.getString(KEY_MODIFIED_SINCE, "0") ?: "0"
        set(value) = preferences.edit(true) { putString(KEY_MODIFIED_SINCE, value) }
    override var startTimeStamp: String
        get() = preferences.getString(KEY_START_TIMESTAMP, "0") ?: "0"
        set(value) = preferences.edit(true) { putString(KEY_START_TIMESTAMP, value) }
    override var clientModifiedSince: String
        get() = preferences.getString(KEY_END_TIMESTAMP, "0") ?: "0"
        set(value) = preferences.edit(true) { putString(KEY_END_TIMESTAMP, value) }
    override var isSyncPaused: Boolean
        get() = preferences.getBoolean(KEY_CLIENT_LIMIT_EXCEEDED, false) ?: false
        set(value) {
            preferences.edit(true) { putBoolean(KEY_CLIENT_LIMIT_EXCEEDED, value) }
            emitNewValue()
        }
    override var invalidEntitiesIds: List<String>
        get() = preferences.getStringSet(KEY_CLIENT_INVALID_IDS, mutableSetOf())?.toList() ?: mutableListOf()
        set(value) {
            preferences.edit(true) {
                putStringSet(KEY_CLIENT_INVALID_IDS, value.toSet())
            }
        }
    override var syncPausedReason: String
        get() = preferences.getString(KEY_CLIENT_SYNC_PAUSED_REASON, "") ?: ""
        set(value) {
            preferences.edit(true) { putString(KEY_CLIENT_SYNC_PAUSED_REASON, value) }
            emitNewValue()
        }

    override fun isSyncPausedFlow(): Flow<Boolean> = syncPausedSharedFlow

    private fun emitNewValue() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            syncPausedSharedFlow.emit(isSyncPaused)
        }
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.sync"
        private const val KEY_MODIFIED_SINCE = "KEY_MODIFIED_SINCE"
        private const val KEY_START_TIMESTAMP = "KEY_START_TIMESTAMP"
        private const val KEY_END_TIMESTAMP = "KEY_END_TIMESTAMP"
        private const val KEY_CLIENT_LIMIT_EXCEEDED = "KEY_CLIENT_LIMIT_EXCEEDED"
        private const val KEY_CLIENT_INVALID_IDS = "KEY_CLIENT_INVALID_IDS"
        private const val KEY_CLIENT_SYNC_PAUSED_REASON = "KEY_CLIENT_SYNC_PAUSED_REASON"
    }
}
