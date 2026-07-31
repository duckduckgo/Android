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
import javax.inject.Inject

/**
 * Recovers the raw private key from a [ProtectedKeyEntry] — the inverse of the two wrapping paths, [mintDdgWrappedProtectedKey] and [ThirdPartyKeyWrapper].
 */
@WorkerThread
interface ProtectedKeyUnwrapper {
    /**
     * Decrypt [entry]'s private key using the credential it was wrapped with:
     *   ddg entries are libsodium secretboxes under the account secret key,
     *   3party entries are JWEs under the key derived from the scoped password.
     */
    fun unwrap(entry: ProtectedKeyEntry): Result<ByteArray>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealProtectedKeyUnwrapper @Inject constructor(
    private val syncStore: SyncStore,
    private val syncJweCrypto: SyncJweCrypto,
    private val nativeLib: SyncLib,
) : ProtectedKeyUnwrapper {

    override fun unwrap(entry: ProtectedKeyEntry): Result<ByteArray> {
        return when (entry.encryptedWith) {
            CREDENTIAL_ID_3PARTY -> unwrapThirdParty(entry)
            CREDENTIAL_ID_DDG -> unwrapDdg(entry)
            else -> Error(reason = "UnwrapProtectedKey: unknown credential encrypted_with=${entry.encryptedWith}")
        }
    }

    private fun unwrapDdg(entry: ProtectedKeyEntry): Result<ByteArray> {
        val accountSecretKey = syncStore.secretKey?.takeUnless { it.isEmpty() }
            ?: return Error(reason = "UnwrapProtectedKey: no account secret key")

        return runCatching {
            // ddg keys are base64url on the wire; libsodium wants standard base64 bytes.
            val encryptedBytes = Base64.decode(entry.encryptedPrivateKey.removeUrlSafetyToRestoreB64(), Base64.NO_WRAP)
            val rawKeyBytes = nativeLib.decryptData(encryptedBytes, accountSecretKey).also {
                it.checkResult("UnwrapProtectedKey: libsodium decrypt of ddg-wrapped key ${entry.kid} failed")
            }.decryptedData
            Success(rawKeyBytes)
        }.getOrElse { it.asLoggedError("UnwrapProtectedKey: failed to unwrap ddg key ${entry.kid}") }
    }

    private fun unwrapThirdParty(entry: ProtectedKeyEntry): Result<ByteArray> {
        val scopedPassword = syncStore.scopedPassword?.raw ?: return Error(reason = "UnwrapProtectedKey: scopedPassword missing")
        val userId = syncStore.userId ?: return Error(reason = "UnwrapProtectedKey: userId missing")
        return runCatching {
            val mainEncryptionKey = syncJweCrypto.hkdfDeriveBytes(
                base64Key = scopedPassword,
                salt = userId.toByteArray(Charsets.UTF_8),
                info = HKDF_INFO_MAIN_ENCRYPTION_KEY,
                outBytes = MAIN_ENCRYPTION_KEY_LENGTH_BYTES,
            )
            Success(syncJweCrypto.jweDecryptSymmetric(entry.encryptedPrivateKey, mainEncryptionKey))
        }.getOrElse { it.asLoggedError("UnwrapProtectedKey: failed to unwrap 3party key ${entry.kid}") }
    }
}
