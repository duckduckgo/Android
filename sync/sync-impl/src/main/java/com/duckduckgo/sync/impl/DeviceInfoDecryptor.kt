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
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Recovers [DeviceInfoPayload]s from `device_info` JWEs using the account's `account_info` private key.
 *
 * The private key is fetched and unwrapped once per [openSession];
 * reuse the returned [Session] to decrypt every blob from a device list off that single fetch.
 */
@WorkerThread
interface DeviceInfoDecryptor {
    fun openSession(): DeviceInfoSessionResult

    /** Decrypts `device_info` blobs with a private key held only for this session's lifetime. */
    @WorkerThread
    interface Session {
        fun decrypt(deviceInfoJwe: String): Result<DeviceInfoPayload>
    }
}

sealed interface DeviceInfoSessionResult {
    data class Available(val session: DeviceInfoDecryptor.Session) : DeviceInfoSessionResult
    data class Unavailable(val reason: AccountInfoKeyUnavailableReason) : DeviceInfoSessionResult
}

@ContributesBinding(AppScope::class)
class RealDeviceInfoDecryptor @Inject constructor(
    private val accountInfoPrivateKeyProvider: AccountInfoPrivateKeyProvider,
    private val syncJweCrypto: SyncJweCrypto,
) : DeviceInfoDecryptor {

    override fun openSession(): DeviceInfoSessionResult =
        when (val result = accountInfoPrivateKeyProvider.privateKey()) {
            is AccountInfoPrivateKeyResult.Available ->
                DeviceInfoSessionResult.Available(RealSession(privateKeyBase64Url = result.privateKey, syncJweCrypto = syncJweCrypto))
            is AccountInfoPrivateKeyResult.Unavailable -> DeviceInfoSessionResult.Unavailable(result.reason)
        }

    private class RealSession(
        private val privateKeyBase64Url: String,
        private val syncJweCrypto: SyncJweCrypto,
    ) : DeviceInfoDecryptor.Session {
        override fun decrypt(deviceInfoJwe: String): Result<DeviceInfoPayload> = runCatching {
            val plaintext = String(syncJweCrypto.jweDecryptRsaOaep(deviceInfoJwe, privateKeyBase64Url), Charsets.UTF_8)
            val payload = DeviceInfoPayload.fromJson(plaintext)
                ?: return Error(reason = "DeviceInfoDecryptor: device_info payload could not be parsed")
            Success(payload.copy(type = payload.type?.takeUnless { it.isEmpty() }))
        }.getOrElse { it.asLoggedError("DeviceInfoDecryptor: failed to decrypt device_info") }
    }
}
