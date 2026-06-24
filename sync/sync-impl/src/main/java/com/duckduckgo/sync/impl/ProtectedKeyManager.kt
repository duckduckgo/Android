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
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.ERROR
import logcat.logcat
import java.util.UUID
import javax.inject.Inject

/**
 * Creates and stores RSA keypairs ("protected keys") on the account, wrapped with the ddg
 * credential's secret. Used to mint per-purpose keys (e.g. for ai_chats) that other devices on
 * the account can decrypt to receive end-to-end-encrypted payloads addressed to a known kid.
 */
interface ProtectedKeyManager {

    /**
     * Generates an RSA keypair for [purpose] (e.g. "ai_chats"), libsodium-encrypts the private key
     * with the account secret key, and POSTs the entry via /sync/keys/.../set-if-absent.
     *
     * Returns the authoritative [ProtectedKeyEntry] taken from the server's response. In the
     * we-wrote-it case the returned entry equals the one we POSTed; in the race-loser case the
     * server's `set-if-absent` returns its pre-existing entry instead, and we surface that so
     * the caller reflects the truth on the server rather than the local key we minted.
     *
     * Returns the entry for [purpose] only — not all keys for the account.
     */
    fun create(purpose: String): Result<ProtectedKeyEntry>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@WorkerThread
class RealProtectedKeyManager @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncJweCrypto: SyncJweCrypto,
    private val nativeLib: SyncLib,
    private val syncFeature: SyncFeature,
) : ProtectedKeyManager {

    override fun create(purpose: String): Result<ProtectedKeyEntry> {
        if (!syncFeature.canUseV2ConnectFlow().isEnabled()) {
            return Error(reason = "Scoped access credentials feature is disabled")
        }
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "CreateProtectedKey: not signed in")
        val secretKey = syncStore.secretKey
            ?: return Error(reason = "CreateProtectedKey: no secret key for libsodium encryption")

        logcat { "Sync-ScopedToken: creating protected key for purpose=$purpose" }

        val rsaKeyPair = kotlin.runCatching {
            syncJweCrypto.generateRsaKeyPair()
        }.getOrElse { return it.asLoggedError("CreateProtectedKey: failed to generate RSA keypair") }
        val (n, e) = kotlin.runCatching {
            syncJweCrypto.extractJwkComponents(rsaKeyPair.publicKeyBase64)
        }.getOrElse { return it.asLoggedError("CreateProtectedKey: failed to extract JWK components") }

        // ddg-side wrap: libsodium-secretbox(privateKey, secretKey), base64url-encoded.
        // Matches duck.ai's EncryptWithSyncMasterKeyHandler so native clients read keys uniformly.
        val privateKeyBytes = kotlin.runCatching {
            Base64.decode(rsaKeyPair.privateKeyBase64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }.getOrElse { return it.asLoggedError("CreateProtectedKey: failed to decode private key bytes") }
        val encryptedPrivateKey = kotlin.runCatching {
            val result = nativeLib.encryptData(privateKeyBytes, secretKey).also {
                it.checkResult("CreateProtectedKey: libsodium encryption of private key failed")
            }
            Base64.encodeToString(result.encryptedData, Base64.NO_WRAP).applyUrlSafetyFromB64()
        }.getOrElse { return it.asErrorResult() }

        val kid = UUID.randomUUID().toString()
        val key = ProtectedKeyEntry(
            kid = kid,
            purpose = purpose,
            encryptedWith = CREDENTIAL_ID_DDG,
            encryptedPrivateKey = encryptedPrivateKey,
            publicKey = RsaJwk(n = n, e = e),
        )

        return when (val result = syncApi.setProtectedKeyIfAbsent(token, purpose, listOf(key))) {
            is Success -> {
                val entry = result.data.firstOrNull { it.purpose == purpose && it.encryptedWith == CREDENTIAL_ID_DDG }
                    ?: return Error(reason = "CreateProtectedKey: server response missing created key for $purpose")
                if (entry.kid != kid) {
                    // Server returned a pre-existing entry — another device created the key for this
                    // purpose between our (implicit) check and our POST. The server's entry wins.
                    logcat {
                        "Sync-ScopedToken: protected key for $purpose already existed on server " +
                            "(server kid=${entry.kid}, our kid=$kid) — adopting server entry"
                    }
                } else {
                    logcat { "Sync-ScopedToken: protected key for $purpose created (kid=$kid)" }
                }
                Success(entry)
            }
            is Error -> {
                logcat(ERROR) { "Sync-ScopedToken: failed to create protected key: ${result.reason}" }
                result
            }
        }
    }
}
