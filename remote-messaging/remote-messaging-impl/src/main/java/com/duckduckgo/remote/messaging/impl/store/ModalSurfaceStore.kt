/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.store

import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.Content.MessageType
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.impl.di.ModalSurface
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface ModalSurfaceStore {

    /**
     * Returns the timestamp of when the app was last backgrounded.
     * @return the timestamp in milliseconds, or null if not available.
     */
    suspend fun getBackgroundedTimestamp(): Long?

    /**
     * Clears the stored timestamp of when the app was last backgrounded.
     */
    suspend fun clearBackgroundTimestamp()

    /**
     * Records the current timestamp as the time the app was backgrounded.
     */
    suspend fun recordBackgroundedTimestamp()

    /**
     * Returns the last shown remote message ID.
     * @return the message ID, or null if not available.
     */
    suspend fun getLastShownRemoteMessageId(): String?

    /**
     * Returns the last shown remote message type.
     * @return the message type, or null if not available.
     */
    suspend fun getLastShownRemoteMessageType(): MessageType?

    /**
     * Records the remote message that was just shown.
     * Saves both the message ID and type atomically.
     * @param message the message that was shown.
     */
    suspend fun recordLastShownRemoteMessage(message: RemoteMessage)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class ModalSurfaceStoreImpl @Inject constructor(
    @ModalSurface private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : ModalSurfaceStore {

    override suspend fun getBackgroundedTimestamp(): Long? {
        return withContext(dispatchers.io()) {
            val key = getKeyBackgroundedTimestamp()
            store.data.firstOrNull()?.get(key)
        }
    }

    override suspend fun clearBackgroundTimestamp() {
        withContext(dispatchers.io()) {
            val key = getKeyBackgroundedTimestamp()
            store.edit { it.remove(key) }
        }
    }

    override suspend fun recordBackgroundedTimestamp() {
        withContext(dispatchers.io()) {
            val key = getKeyBackgroundedTimestamp()
            store.edit { it[key] = SystemClock.elapsedRealtime() }
        }
    }

    override suspend fun getLastShownRemoteMessageId(): String? {
        return withContext(dispatchers.io()) {
            store.data.firstOrNull()?.get(getKeyLastShownRemoteMessageId())
        }
    }

    override suspend fun getLastShownRemoteMessageType(): MessageType? {
        return withContext(dispatchers.io()) {
            store.data.firstOrNull()?.get(getKeyLastShownRemoteMessageType())?.let {
                runCatching { MessageType.valueOf(it) }.getOrNull()
            }
        }
    }

    override suspend fun recordLastShownRemoteMessage(message: RemoteMessage) {
        withContext(dispatchers.io()) {
            store.edit {
                it[getKeyLastShownRemoteMessageId()] = message.id
                it[getKeyLastShownRemoteMessageType()] = message.content.messageType.name
            }
        }
    }

    private fun getKeyBackgroundedTimestamp(): Preferences.Key<Long> {
        return longPreferencesKey(KEY_NAME_BACKGROUNDED_TIMESTAMP)
    }

    private fun getKeyLastShownRemoteMessageId(): Preferences.Key<String> {
        return stringPreferencesKey(KEY_NAME_LAST_SHOWN_REMOTE_MESSAGE_ID)
    }

    private fun getKeyLastShownRemoteMessageType(): Preferences.Key<String> {
        return stringPreferencesKey(KEY_NAME_LAST_SHOWN_MESSAGE_TYPE)
    }

    companion object {
        private const val KEY_NAME_BACKGROUNDED_TIMESTAMP = "modal_evaluator_backgrounded_timestamp"
        private const val KEY_NAME_LAST_SHOWN_REMOTE_MESSAGE_ID = "modal_evaluator_last_shown_remote_message_id"
        private const val KEY_NAME_LAST_SHOWN_MESSAGE_TYPE = "modal_evaluator_last_shown_message_type"
    }
}
