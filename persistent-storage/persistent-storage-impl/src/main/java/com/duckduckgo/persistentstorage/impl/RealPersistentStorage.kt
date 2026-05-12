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

package com.duckduckgo.persistentstorage.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability.Available
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability.Unavailable
import com.duckduckgo.persistentstorage.api.PersistentStorageKey
import com.duckduckgo.persistentstorage.api.PersistentStorageUnavailableException
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class, boundType = PersistentStorage::class)
@SingleInstanceIn(AppScope::class)
class RealPersistentStorage @Inject constructor(
    private val blockStoreWrapper: BlockStoreWrapper,
    private val dispatchers: DispatcherProvider,
) : PersistentStorage {

    override suspend fun checkAvailability() = withContext(dispatchers.io()) {
        if (!blockStoreWrapper.isPlayServicesAvailable) {
            logcat { "Block Store: Play Services not available" }
            return@withContext Unavailable
        }

        val isEndToEndEncryptionSupported = try {
            blockStoreWrapper.isEndToEndEncryptionAvailable()
        } catch (e: Throwable) {
            ensureActive()
            logcat(LogPriority.WARN) { "Block Store: E2E check failed - ${e.message}" }
            false
        }

        logcat { "Block Store: available, E2E encryption supported = $isEndToEndEncryptionSupported" }
        Available(isEndToEndEncryptionSupported)
    }

    override suspend fun retrieve(key: PersistentStorageKey): Result<ByteArray?> = withContext(dispatchers.io()) {
        if (!blockStoreWrapper.isPlayServicesAvailable) {
            logcat { "Block Store: cannot retrieve ${key.key} - Play Services not available" }
            return@withContext Result.failure(PersistentStorageUnavailableException())
        }

        logcat { "Block Store: fetching ${key.key}..." }

        runCatching {
            val bytes = blockStoreWrapper.retrieveBytes(key.key)

            if (bytes != null) {
                logcat { "Block Store: ${key.key} retrieved (${bytes.size} bytes)" }
            } else {
                logcat { "Block Store: ${key.key} not found" }
            }

            bytes
        }.onFailure { ensureActive() }
    }

    override suspend fun store(key: PersistentStorageKey, value: ByteArray): Result<Unit> = withContext(dispatchers.io()) {
        if (!blockStoreWrapper.isPlayServicesAvailable) {
            logcat { "Block Store: cannot store ${key.key} - Play Services not available" }
            return@withContext Result.failure(PersistentStorageUnavailableException())
        }

        runCatching {
            blockStoreWrapper.storeBytes(key.key, value, key.shouldBackupToCloud)
            logcat { "Block Store: stored ${key.key} (${value.size} bytes)" }
        }.onFailure { ensureActive() }
    }

    override suspend fun clear(key: PersistentStorageKey): Result<Unit> = withContext(dispatchers.io()) {
        if (!blockStoreWrapper.isPlayServicesAvailable) {
            logcat { "Block Store: cannot clear ${key.key} - Play Services not available" }
            return@withContext Result.failure(PersistentStorageUnavailableException())
        }

        runCatching {
            blockStoreWrapper.deleteBytes(key.key)
            logcat { "Block Store: cleared ${key.key}" }
        }.onFailure { ensureActive() }
    }
}
