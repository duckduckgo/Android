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

package com.duckduckgo.persistent.storage.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.persistent.storage.api.PersistentStorage
import com.duckduckgo.persistent.storage.api.PersistentStorageAvailability
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

/**
 * Real implementation of [PersistentStorage] that uses Google's Block Store API.
 *
 * This implementation is only included in play and internal builds (not fdroid).
 */
@ContributesBinding(AppScope::class)
class RealPersistentStorage @Inject constructor(
    private val clientProvider: BlockstoreClientProvider,
    private val dispatcherProvider: DispatcherProvider,
) : PersistentStorage {

    private val client get() = clientProvider.client
    private val isPlayServicesAvailable get() = clientProvider.isPlayServicesAvailable

    override suspend fun checkAvailability(): PersistentStorageAvailability = withContext(dispatcherProvider.io()) {
        logcat { "PersistentStorage: checking availability..." }

        if (!isPlayServicesAvailable) {
            logcat { "PersistentStorage: Play Services not available" }
            return@withContext PersistentStorageAvailability.Unavailable
        }

        val isE2EAvailable = try {
            client.isEndToEndEncryptionAvailable.await().also {
                logcat { "PersistentStorage: E2E encryption available = $it" }
            }
        } catch (e: Throwable) {
            ensureActive()
            logcat(LogPriority.WARN) { "PersistentStorage: E2E check failed - ${e.message}" }
            false
        }

        if (isE2EAvailable) {
            logcat { "PersistentStorage: available with E2E encryption" }
            PersistentStorageAvailability.AvailableEncrypted
        } else {
            logcat { "PersistentStorage: available without E2E encryption" }
            PersistentStorageAvailability.AvailableUnencrypted
        }
    }

    override suspend fun write(key: String, value: String): Result<Unit> = withContext(dispatcherProvider.io()) {
        logcat { "PersistentStorage: attempting to write key '$key' (${value.length} chars)..." }

        if (!isPlayServicesAvailable) {
            logcat(LogPriority.WARN) { "PersistentStorage: cannot write - Play Services not available" }
            return@withContext Result.failure(IllegalStateException("Play Services not available"))
        }

        try {
            val data = StoreBytesData.Builder()
                .setKey(key)
                .setBytes(value.toByteArray(Charsets.UTF_8))
                .build()

            client.storeBytes(data).await()
            logcat { "PersistentStorage: write success for key '$key'" }
            Result.success(Unit)
        } catch (e: Throwable) {
            ensureActive()
            logcat(LogPriority.WARN) { "PersistentStorage: write failed - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun read(key: String): Result<String?> = withContext(dispatcherProvider.io()) {
        logcat { "PersistentStorage: attempting to read key '$key'..." }

        if (!isPlayServicesAvailable) {
            logcat(LogPriority.WARN) { "PersistentStorage: cannot read - Play Services not available" }
            return@withContext Result.failure(IllegalStateException("Play Services not available"))
        }

        try {
            val request = RetrieveBytesRequest.Builder()
                .setKeys(listOf(key))
                .build()

            val response = client.retrieveBytes(request).await()
            val blockstoreData = response.blockstoreDataMap[key]
            val result = if (blockstoreData != null) {
                String(blockstoreData.bytes, Charsets.UTF_8).also {
                    logcat { "PersistentStorage: read success for key '$key' - data found (${it.length} chars)" }
                }
            } else {
                logcat { "PersistentStorage: read success for key '$key' - no data found" }
                null
            }
            Result.success(result)
        } catch (e: Throwable) {
            ensureActive()
            logcat(LogPriority.WARN) { "PersistentStorage: read failed - ${e.message}" }
            Result.failure(e)
        }
    }
}
