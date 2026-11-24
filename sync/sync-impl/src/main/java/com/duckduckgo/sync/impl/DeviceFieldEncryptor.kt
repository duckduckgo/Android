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
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

/**
 * Encrypts the legacy `name`/`type` device fields for the current device's credential — the inverse
 * of [DeviceFieldDecryptor], so the values stay readable by clients that don't understand
 * `device_info` yet. 3party encrypts under a main encryption key derived from the scoped password;
 * ddg/null encrypts with the local primary key.
 */
@WorkerThread
interface DeviceFieldEncryptor {
    fun encrypt(name: String, type: String): Result<EncryptedDeviceFields>

    /**
     * 3party encryption with a main encryption key the caller already holds, for when [SyncStore]
     * does not carry the 3party credential yet (the 3party → ddg upgrade login).
     */
    fun encryptThirdParty(name: String, type: String, mainEncryptionKey: ByteArray): Result<EncryptedDeviceFields>
}

data class EncryptedDeviceFields(
    val name: String,
    val type: String,
)

@ContributesBinding(AppScope::class)
class RealDeviceFieldEncryptor @Inject constructor(
    private val syncStore: SyncStore,
    private val syncJweCrypto: SyncJweCrypto,
    private val nativeLib: SyncLib,
) : DeviceFieldEncryptor {

    override fun encrypt(name: String, type: String): Result<EncryptedDeviceFields> {
        return when (syncStore.credentialId) {
            CREDENTIAL_ID_3PARTY -> encryptThirdPartyWithStoredKey(name, type)
            CREDENTIAL_ID_DDG, null -> encryptDdg(name, type)
            else -> Error(reason = "DeviceFieldEncryptor: unknown credential_id=${syncStore.credentialId}")
        }
    }

    private fun encryptDdg(name: String, type: String): Result<EncryptedDeviceFields> {
        val primaryKey = syncStore.primaryKey?.takeUnless { it.isEmpty() }
            ?: return Error(reason = "DeviceFieldEncryptor: primaryKey missing")
        return runCatching {
            val encryptedName = nativeLib.encryptData(name, primaryKey).also {
                it.checkResult("DeviceFieldEncryptor: ddg name encrypt failed")
            }.encryptedData
            val encryptedType = nativeLib.encryptData(type, primaryKey).also {
                it.checkResult("DeviceFieldEncryptor: ddg type encrypt failed")
            }.encryptedData
            Success(EncryptedDeviceFields(encryptedName, encryptedType))
        }.getOrElse { it.asLoggedError("DeviceFieldEncryptor: ddg encrypt failed") }
    }

    private fun encryptThirdPartyWithStoredKey(name: String, type: String): Result<EncryptedDeviceFields> {
        val scopedPassword = syncStore.scopedPassword?.raw ?: return Error(reason = "DeviceFieldEncryptor: scopedPassword missing")
        val userId = syncStore.userId ?: return Error(reason = "DeviceFieldEncryptor: userId missing")
        val mainEncryptionKey = runCatching {
            syncJweCrypto.hkdfDeriveBytes(
                scopedPassword,
                userId.toByteArray(Charsets.UTF_8),
                HKDF_INFO_MAIN_ENCRYPTION_KEY,
                MAIN_ENCRYPTION_KEY_LENGTH_BYTES,
            )
        }.getOrElse { return it.asLoggedError("DeviceFieldEncryptor: 3party key derivation failed") }
        return encryptThirdParty(name, type, mainEncryptionKey)
    }

    override fun encryptThirdParty(name: String, type: String, mainEncryptionKey: ByteArray): Result<EncryptedDeviceFields> {
        return runCatching {
            fun encrypt(value: String) =
                syncJweCrypto.jweEncryptSymmetric(value.toByteArray(Charsets.UTF_8), mainEncryptionKey, kid = CREDENTIAL_ID_3PARTY)
            Success(EncryptedDeviceFields(encrypt(name), encrypt(type)))
        }.getOrElse { it.asLoggedError("DeviceFieldEncryptor: 3party encrypt failed") }
    }
}
