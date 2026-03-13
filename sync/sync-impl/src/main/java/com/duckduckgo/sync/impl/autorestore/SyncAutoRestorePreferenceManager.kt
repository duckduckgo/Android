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
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.getOrNull
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

interface SyncAutoRestorePreferenceManager {
    suspend fun saveRecoveryCodeToBlockStore()
    suspend fun clearRecoveryCodeFromBlockStore()
    suspend fun isRestoreOnReinstallEnabled(): Boolean
    suspend fun setRestoreOnReinstallEnabled(enabled: Boolean)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSyncAutoRestorePreferenceManager @Inject constructor(
    private val persistentStorage: PersistentStorage,
    private val syncAccountRepository: SyncAccountRepository,
    private val dataStore: SyncAutoRestorePreferenceDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : SyncAutoRestorePreferenceManager {

    override suspend fun saveRecoveryCodeToBlockStore() {
        withContext(dispatcherProvider.io()) {
            val recoveryCode = syncAccountRepository.getRecoveryCode().getOrNull()
            if (recoveryCode == null) {
                logcat(LogPriority.WARN) { "Sync-Recovery: no recovery code available, skipping Block Store write" }
                return@withContext
            }
            logcat { "Sync-Recovery: storing recovery code to Block Store" }
            persistentStorage.store(SyncRecoveryPersistentStorageKey, recoveryCode.rawCode.toByteArray())
                .onSuccess { logcat { "Sync-Recovery: recovery code stored successfully" } }
                .onFailure { logcat(LogPriority.ERROR) { "Sync-Recovery: failed to store recovery code - ${it.message}" } }
        }
    }

    override suspend fun clearRecoveryCodeFromBlockStore() {
        withContext(dispatcherProvider.io()) {
            logcat { "Sync-Recovery: clearing recovery code from Block Store" }
            persistentStorage.clear(SyncRecoveryPersistentStorageKey)
                .onSuccess { logcat { "Sync-Recovery: recovery code cleared successfully" } }
                .onFailure { logcat(LogPriority.ERROR) { "Sync-Recovery: failed to clear recovery code - ${it.message}" } }
        }
    }

    override suspend fun isRestoreOnReinstallEnabled(): Boolean {
        return withContext(dispatcherProvider.io()) {
            dataStore.isRestoreOnReinstallEnabled()
        }
    }

    override suspend fun setRestoreOnReinstallEnabled(enabled: Boolean) {
        withContext(dispatcherProvider.io()) {
            logcat { "Sync-Recovery: setRestoreOnReinstallEnabled=$enabled" }
            dataStore.setRestoreOnReinstallEnabled(enabled)
            if (enabled) {
                saveRecoveryCodeToBlockStore()
            } else {
                clearRecoveryCodeFromBlockStore()
            }
        }
    }
}
