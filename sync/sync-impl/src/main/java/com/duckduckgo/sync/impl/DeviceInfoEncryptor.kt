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
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

/**
 * Produces the cross-credential `device_info` blob:
 * a JWE of a [DeviceInfoPayload] under the account's `account_info` public key (RSA-OAEP-256 + A256GCM)
 */
@WorkerThread
interface DeviceInfoEncryptor {
    fun encrypt(name: String, type: String): Result<String>
}

/**
 * The plaintext `device_info` shape
 */
data class DeviceInfoPayload(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "type") val type: String? = null,
) {
    companion object {
        private val adapter by lazy {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(DeviceInfoPayload::class.java)
        }

        fun toJson(payload: DeviceInfoPayload): String = adapter.toJson(payload)

        fun fromJson(json: String): DeviceInfoPayload? = adapter.fromJson(json)
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDeviceInfoEncryptor @Inject constructor(
    private val syncStore: SyncStore,
    private val syncJweCrypto: SyncJweCrypto,
) : DeviceInfoEncryptor {

    override fun encrypt(name: String, type: String): Result<String> {
        val publicKey = syncStore.accountInfoPublicKey
            ?: return Error(reason = "DeviceInfoEncryptor: no cached account_info key")

        return runCatching {
            val plaintext = DeviceInfoPayload.toJson(DeviceInfoPayload(name = name, type = type)).toByteArray(Charsets.UTF_8)

            // Cached keys are stored as JWK n/e; the encrypter wants SPKI
            val recipientPublicKey = syncJweCrypto.rsaSpkiFromJwkComponents(publicKey.modulus, publicKey.exponent)
            val jwe = syncJweCrypto.jweEncryptRsaOaep(plaintext, recipientPublicKey, kid = publicKey.keyId)
            if (jwe.length > MAX_DEVICE_INFO_CHARS) {
                return Error(reason = "DeviceInfoEncryptor: device_info is ${jwe.length} chars, over the $MAX_DEVICE_INFO_CHARS limit")
            }
            logcat { "Sync-UnifiedDevices: encrypted device_info (${jwe.length} chars, kid=${publicKey.keyId})" }
            Success(jwe)
        }.getOrElse { it.asLoggedError("DeviceInfoEncryptor: failed to encrypt device_info") }
    }

    companion object {
        private const val MAX_DEVICE_INFO_CHARS = 2000
    }
}
