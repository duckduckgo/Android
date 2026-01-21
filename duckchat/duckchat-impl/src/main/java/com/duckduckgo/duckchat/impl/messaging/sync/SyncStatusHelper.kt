/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.messaging.sync

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * Helper for building sync status JSON payload for Duck AI chat
 */
class SyncStatusHelper @Inject constructor(
    private val deviceSyncState: DeviceSyncState,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun buildSyncStatusPayload(): JSONObject = withContext(dispatcherProvider.io()) {
        val syncAvailable = deviceSyncState.isFeatureEnabled()
        when (val accountState = deviceSyncState.getAccountState()) {
            is SignedIn -> {
                // Find this device in the list of connected devices
                val thisDevice = accountState.devices.firstOrNull { it.thisDevice }

                JSONObject().apply {
                    put(KEY_SYNC_AVAILABLE, syncAvailable)
                    put(KEY_USER_ID, accountState.userId)
                    put(KEY_DEVICE_ID, thisDevice?.deviceId ?: JSONObject.NULL)
                    put(KEY_DEVICE_NAME, thisDevice?.deviceName ?: JSONObject.NULL)
                    put(KEY_DEVICE_TYPE, thisDevice?.deviceType?.toApiString() ?: JSONObject.NULL)
                }
            }
            else -> {
                // User is signed out - all account fields are null
                JSONObject().apply {
                    put(KEY_SYNC_AVAILABLE, syncAvailable)
                    put(KEY_USER_ID, JSONObject.NULL)
                    put(KEY_DEVICE_ID, JSONObject.NULL)
                    put(KEY_DEVICE_NAME, JSONObject.NULL)
                    put(KEY_DEVICE_TYPE, JSONObject.NULL)
                }
            }
        }
    }

    private fun DeviceSyncState.Type.toApiString(): String = when (this) {
        DeviceSyncState.Type.MOBILE -> "mobile"
        DeviceSyncState.Type.DESKTOP -> "desktop"
        DeviceSyncState.Type.UNKNOWN -> "unknown"
    }

    private companion object {
        private const val KEY_SYNC_AVAILABLE = "syncAvailable"
        private const val KEY_USER_ID = "userId"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_DEVICE_NAME = "deviceName"
        private const val KEY_DEVICE_TYPE = "deviceType"
    }
}
