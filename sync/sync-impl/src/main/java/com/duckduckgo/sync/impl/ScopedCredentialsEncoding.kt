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
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

/**
 * HKDF-SHA-256 over the decoded `base64Key` bytes, returning raw bytes for direct use as an AES key.
 * Used to derive the credential MEK (info="Main Key") per Encryption Algorithms TD (Asana 1214802412121967).
 */
internal fun SyncJweCrypto.hkdfDeriveBytes(
    base64Key: String,
    salt: ByteArray,
    info: String,
    outBytes: Int = 32,
): ByteArray {
    val raw = Base64.decode(base64Key, Base64.NO_WRAP)
    return hkdfSha256SingleBlock(raw, salt, info, outBytes)
}

/**
 * HKDF-SHA-256 over the decoded `base64Key` bytes, re-encoded as base64url (no padding).
 * Used for 3party HKDF derivations: `credential_hashed_password` (info="Password") per
 * Encryption Algorithms TD (Asana 1214802412121967).
 *
 * Not for ddg auth — that uses libsodium BLAKE2b via `nativeLib.prepareForLogin`.
 */
internal fun SyncJweCrypto.hkdfDeriveBase64Url(
    base64Key: String,
    salt: ByteArray,
    info: String,
    outBytes: Int = 32,
): String {
    val raw = Base64.decode(base64Key, Base64.NO_WRAP)
    val out = hkdfSha256SingleBlock(raw, salt, info, outBytes)
    return Base64.encodeToString(out, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

/** Convert a base64url SP from the wire into the standard-base64 form used locally. */
internal fun base64UrlStringToStandardBase64(b64Url: String): String {
    val raw = Base64.decode(b64Url, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    return Base64.encodeToString(raw, Base64.NO_WRAP)
}

/** Log the throwable's full stack trace, then return an [Error] tagged with [reason]. */
internal fun Throwable.asLoggedError(reason: String): Error {
    logcat(ERROR) { "Sync-ScopedToken: $reason: ${asLog()}" }
    return Error(reason = "$reason: $message")
}
