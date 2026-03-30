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

package com.duckduckgo.sync.impl.autorestore

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability
import com.duckduckgo.sync.impl.SyncFeature
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

interface SyncAutoRestoreManager {
    suspend fun isAutoRestoreAvailable(): Boolean
    suspend fun saveAutoRestoreData(recoveryCode: String, deviceId: String?): Boolean
    suspend fun retrieveRecoveryPayload(): RestorePayload?
    suspend fun clearAutoRestoreData()
    suspend fun isRestoreOnReinstallEnabled(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSyncAutoRestoreManager @Inject constructor(
    private val persistentStorage: PersistentStorage,
    private val dataStore: SyncAutoRestorePreferenceDataStore,
    private val syncFeature: SyncFeature,
    private val dispatcherProvider: DispatcherProvider,
    private val moshi: Moshi,
) : SyncAutoRestoreManager {

    override suspend fun isAutoRestoreAvailable(): Boolean {
        return withContext(dispatcherProvider.io()) {
            syncFeature.syncAutoRestore().isEnabled() &&
                persistentStorage.checkAvailability() is PersistentStorageAvailability.Available
        }
    }

    override suspend fun saveAutoRestoreData(recoveryCode: String, deviceId: String?): Boolean {
        return withContext(dispatcherProvider.io()) {
            val payload = RestorePayload(recoveryCode = recoveryCode, deviceId = deviceId)
            val payloadJson = moshi.adapter(RestorePayload::class.java).toJson(payload)
            logcat { "Sync-Recovery: storing recovery code and device ID ($deviceId) to Block Store" }
            persistentStorage.store(SyncRecoveryPersistentStorageKey, payloadJson.toByteArray(Charsets.UTF_8))
                .onSuccess {
                    logcat { "Sync-Recovery: payload stored successfully" }
                    dataStore.setRestoreOnReinstallEnabled(true)
                }
                .onFailure { logcat(LogPriority.ERROR) { "Sync-Recovery: failed to store payload - ${it.message}" } }
                .isSuccess
        }
    }

    override suspend fun retrieveRecoveryPayload(): RestorePayload? {
        return withContext(dispatcherProvider.io()) {
            val bytes = persistentStorage.retrieve(SyncRecoveryPersistentStorageKey).getOrNull() ?: return@withContext null
            val rawString = String(bytes, Charsets.UTF_8)
            moshi.adapter(RestorePayload::class.java).fromJson(rawString)
        }
    }

    override suspend fun clearAutoRestoreData() {
        withContext(dispatcherProvider.io()) {
            logcat { "Sync-Recovery: clearing auto-restore data" }
            persistentStorage.clear(SyncRecoveryPersistentStorageKey)
                .onSuccess { logcat { "Sync-Recovery: recovery code cleared successfully" } }
                .onFailure { logcat(LogPriority.ERROR) { "Sync-Recovery: failed to clear recovery code - ${it.message}" } }
            dataStore.setRestoreOnReinstallEnabled(false)
        }
    }

    override suspend fun isRestoreOnReinstallEnabled(): Boolean {
        return withContext(dispatcherProvider.io()) {
            dataStore.isRestoreOnReinstallEnabled()
        }
    }
}

data class RestorePayload(
    @field:Json(name = "recovery_code") val recoveryCode: String,
    @field:Json(name = "device_id") val deviceId: String?,
)
