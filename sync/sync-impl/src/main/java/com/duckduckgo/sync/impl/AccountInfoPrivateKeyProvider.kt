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

import android.util.Base64
import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Supplies the account's `account_info` private key, unwrapped for the current credential and encoded as base64url PKCS#8.
 *
 * The *wrapped* key entry (still encrypted, as on disk) is cached in memory keyed by account+credential: the `account_info` key is
 * immutable for the account lifetime, so a burst of reads (e.g. repeated device-list renders) does a single `GET /sync/keys` instead of
 * hammering it and tripping rate limits. The key is unwrapped on every call, so the plaintext key is only ever held transiently by the
 * caller — it is not persisted or cached. Only successful fetches are cached; the cache is cleared on sign-out and
 * refreshed automatically on an account/credential switch.
 */
@WorkerThread
interface AccountInfoPrivateKeyProvider {
    fun privateKey(): Result<String>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAccountInfoPrivateKeyProvider @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val protectedKeyUnwrapper: ProtectedKeyUnwrapper,
) : AccountInfoPrivateKeyProvider {

    private val fetchLock = Any()

    @Volatile
    private var cachedEntry: CachedEntry? = null

    private data class CachedEntry(val userId: String, val credentialId: String, val wrappedEntry: ProtectedKeyEntry)

    override fun privateKey(): Result<String> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: run {
                cachedEntry = null
                return Error(reason = "AccountInfoPrivateKey: not signed in")
            }
        val userId = syncStore.userId.takeUnless { it.isNullOrEmpty() }
            ?: run {
                cachedEntry = null
                return Error(reason = "AccountInfoPrivateKey: no user id")
            }
        val credentialId = syncStore.credentialId ?: CREDENTIAL_ID_DDG

        val entry = when (val result = wrappedEntry(token, userId, credentialId)) {
            is Success -> result.data
            is Error -> return result
        }

        return when (val result = protectedKeyUnwrapper.unwrap(entry)) {
            is Success -> Success(Base64.encodeToString(result.data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            is Error -> result
        }
    }

    /** Returns the wrapped `account_info` entry from cache, or fetches it once (deduping a concurrent burst) and caches it. */
    private fun wrappedEntry(token: String, userId: String, credentialId: String): Result<ProtectedKeyEntry> {
        cachedEntry?.let { if (it.userId == userId && it.credentialId == credentialId) return Success(it.wrappedEntry) }

        return synchronized(fetchLock) {
            // another thread may have populated the cache while we waited on the lock
            cachedEntry?.let { if (it.userId == userId && it.credentialId == credentialId) return@synchronized Success(it.wrappedEntry) }

            val entry = when (val result = syncApi.getProtectedKeys(token)) {
                is Success -> result.data.firstOrNull { it.purpose == SYNC_PURPOSE_ACCOUNT_INFO && it.encryptedWith == credentialId }
                    ?: return@synchronized Error(reason = "AccountInfoPrivateKey: no account_info key wrapped for credential=$credentialId")
                is Error -> return@synchronized result
            }

            cachedEntry = CachedEntry(userId = userId, credentialId = credentialId, wrappedEntry = entry)
            Success(entry)
        }
    }
}
