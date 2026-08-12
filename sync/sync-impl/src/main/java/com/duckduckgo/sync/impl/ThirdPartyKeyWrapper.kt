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
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.ScopedPassword
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/** Wraps a freshly-minted keypair's private key for the 3party credential. */
@WorkerThread
interface ThirdPartyKeyWrapper {
    fun wrap(
        minted: MintedProtectedKey,
        purpose: String,
        scopedPassword: ScopedPassword,
        userId: String,
    ): Result<ProtectedKeyEntry>
}

@ContributesBinding(AppScope::class)
class RealThirdPartyKeyWrapper @Inject constructor(
    private val syncJweCrypto: SyncJweCrypto,
) : ThirdPartyKeyWrapper {

    override fun wrap(
        minted: MintedProtectedKey,
        purpose: String,
        scopedPassword: ScopedPassword,
        userId: String,
    ): Result<ProtectedKeyEntry> {
        // main encryption key, derived from the scoped password
        val scopedPasswordMainEncryptionKey = kotlin.runCatching {
            syncJweCrypto.hkdfDeriveBytes(
                base64Key = scopedPassword.raw,
                salt = userId.toByteArray(Charsets.UTF_8),
                info = MAIN_KEY_HKDF_INFO,
                outBytes = MAIN_KEY_LENGTH_BYTES,
            )
        }.getOrElse { return it.asLoggedError("ThirdPartyKeyWrap: failed to derive 3party MEK") }

        val encryptedPrivateKey = kotlin.runCatching {
            syncJweCrypto.jweEncryptSymmetric(
                plaintext = minted.rawPrivateKeyBytes,
                symmetricKey = scopedPasswordMainEncryptionKey,
                kid = CREDENTIAL_ID_3PARTY,
            )
        }.getOrElse { return it.asLoggedError("ThirdPartyKeyWrap: failed to JWE-encrypt private key for 3party") }

        return Success(
            ProtectedKeyEntry(
                kid = minted.entry.kid,
                purpose = purpose,
                encryptedWith = CREDENTIAL_ID_3PARTY,
                encryptedPrivateKey = encryptedPrivateKey,
                publicKey = minted.entry.publicKey,
            ),
        )
    }

    companion object {
        // HKDF info label and output length for deriving a credential's main encryption key (MEK).
        private const val MAIN_KEY_HKDF_INFO = "Main Key"
        private const val MAIN_KEY_LENGTH_BYTES = 32
    }
}
