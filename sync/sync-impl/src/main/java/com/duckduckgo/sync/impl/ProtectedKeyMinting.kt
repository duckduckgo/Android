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
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import java.util.UUID

/** A freshly-minted protected key with its ddg-wrap [entry] and the underlying raw private bytes,
 *  so callers can produce additional wrappings (e.g. 3party) under the same `kid` without
 *  decrypting the ddg-wrap a second time. */
internal data class MintedProtectedKey(
    val entry: ProtectedKeyEntry,
    val rawPrivateKeyBytes: ByteArray,
)

/**
 * Generate a fresh RSA keypair and build its ddg-side [ProtectedKeyEntry] (libsodium-secretbox of
 * the private key, base64url-encoded, matching duck.ai's `EncryptWithSyncMasterKeyHandler`).
 *
 * Pure helper: does not touch the network or the store. Used by the 3party credential flow,
 * which bundles both ddg and 3party wraps into a single `POST /access-credentials/{id}`.
 */
internal fun mintDdgWrappedProtectedKey(
    purpose: String,
    accountSecretKey: String,
    syncJweCrypto: SyncJweCrypto,
    nativeLib: SyncLib,
    errorPrefix: String,
): Result<MintedProtectedKey> {
    val rsa = runCatching { syncJweCrypto.generateRsaKeyPair() }
        .getOrElse { return it.asLoggedError("$errorPrefix: failed to generate RSA keypair") }
    val (n, e) = runCatching { syncJweCrypto.extractJwkComponents(rsa.publicKeyBase64) }
        .getOrElse { return it.asLoggedError("$errorPrefix: failed to extract JWK components") }

    val privateKeyBytes = runCatching {
        Base64.decode(rsa.privateKeyBase64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }.getOrElse { return it.asLoggedError("$errorPrefix: failed to decode private key bytes") }

    val ddgEncryptedPrivateKey = runCatching {
        val result = nativeLib.encryptData(privateKeyBytes, accountSecretKey).also {
            it.checkResult("$errorPrefix: libsodium encryption of private key failed")
        }
        Base64.encodeToString(result.encryptedData, Base64.NO_WRAP).applyUrlSafetyFromB64()
    }.getOrElse { return it.asErrorResult() }

    val entry = ProtectedKeyEntry(
        kid = UUID.randomUUID().toString(),
        purpose = purpose,
        encryptedWith = CREDENTIAL_ID_DDG,
        encryptedPrivateKey = ddgEncryptedPrivateKey,
        publicKey = RsaJwk(n = n, e = e),
    )
    return Success(MintedProtectedKey(entry = entry, rawPrivateKeyBytes = privateKeyBytes))
}
