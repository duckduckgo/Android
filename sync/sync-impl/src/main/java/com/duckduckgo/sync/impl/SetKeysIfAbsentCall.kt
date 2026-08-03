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

package com.duckduckgo.sync.impl

import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.INFO
import logcat.logcat
import retrofit2.HttpException
import javax.inject.Inject

/** Runs the POST /sync/keys/purpose/{purpose}/set-if-absent request and maps its outcome. */
@WorkerThread
interface SetKeysIfAbsentCall {
    fun execute(
        token: String,
        purpose: String,
        keys: List<ProtectedKeyEntry>,
    ): Result<SetKeysIfAbsentResult>
}

@ContributesBinding(AppScope::class)
class RealSetKeysIfAbsentCall @Inject constructor(
    private val syncService: SyncService,
) : SetKeysIfAbsentCall {
    override fun execute(
        token: String,
        purpose: String,
        keys: List<ProtectedKeyEntry>,
    ): Result<SetKeysIfAbsentResult> {
        return runCatching {
            logcat { "Sync-UnifiedDevices: setKeysIfAbsent purpose=$purpose, wraps=${keys.size}" }
            val response = syncService.setKeysIfAbsent("Bearer $token", purpose, SetKeysIfAbsentRequest(keys)).execute()

            if (response.isSuccessful) {
                if (response.code() == 201) {
                    logcat { "Sync-UnifiedDevices: set-if-absent 201 — our keypair won" }
                    Result.Success(SetKeysIfAbsentResult.Created)
                } else {
                    // 200 is a backwards-compat shim for "a key already exists"; adopt it if the body carries it,
                    // otherwise fall back to fetching (same path as a 409).
                    val existing = response.body()?.keys?.firstOrNull()
                    if (existing != null) {
                        logcat { "Sync-UnifiedDevices: set-if-absent 200 — adopting key from response (kid=${existing.kid})" }
                        Result.Success(SetKeysIfAbsentResult.Existing(existing.kid, existing.publicKey))
                    } else {
                        logcat { "Sync-UnifiedDevices: set-if-absent 200 — key exists but not returned; will fetch" }
                        Result.Success(SetKeysIfAbsentResult.ExistsFetchRequired)
                    }
                }
            } else if (response.code() == HTTP_CONFLICT) {
                logcat { "Sync-UnifiedDevices: set-if-absent 409 — a different key already exists; will fetch" }
                Result.Success(SetKeysIfAbsentResult.ExistsFetchRequired)
            } else {
                response.toUnparsedError()
            }
        }.getOrElse { throwable ->
            logcat(INFO) { "Sync-UnifiedDevices: setKeysIfAbsent error ${throwable.localizedMessage}" }
            if (throwable is HttpException) {
                Result.Error(code = throwable.code(), reason = "unexpected status code")
            } else {
                Result.Error(reason = "internal error")
            }
        }
    }

    companion object {
        private const val HTTP_CONFLICT = 409
    }
}
