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
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
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
}

data class EncryptedDeviceFields(
    val name: String,
    val type: String,
)

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDeviceFieldEncryptor @Inject constructor(
    private val syncStore: SyncStore,
    private val syncJweCrypto: SyncJweCrypto,
    private val nativeLib: SyncLib,
) : DeviceFieldEncryptor {

    override fun encrypt(name: String, type: String): Result<EncryptedDeviceFields> {
        return when (syncStore.credentialId) {
            CREDENTIAL_ID_3PARTY -> encryptThirdParty(name, type)
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

    private fun encryptThirdParty(name: String, type: String): Result<EncryptedDeviceFields> {
        val scopedPassword = syncStore.scopedPassword?.raw ?: return Error(reason = "DeviceFieldEncryptor: scopedPassword missing")
        val userId = syncStore.userId ?: return Error(reason = "DeviceFieldEncryptor: userId missing")
        return runCatching {
            val mainEncryptionKey = syncJweCrypto.hkdfDeriveBytes(
                scopedPassword,
                userId.toByteArray(Charsets.UTF_8),
                HKDF_INFO_MAIN_ENCRYPTION_KEY,
                MAIN_ENCRYPTION_KEY_LENGTH_BYTES,
            )
            val encryptedName = syncJweCrypto.jweEncryptSymmetric(name.toByteArray(Charsets.UTF_8), mainEncryptionKey)
            val encryptedType = syncJweCrypto.jweEncryptSymmetric(type.toByteArray(Charsets.UTF_8), mainEncryptionKey)
            Success(EncryptedDeviceFields(encryptedName, encryptedType))
        }.getOrElse { it.asLoggedError("DeviceFieldEncryptor: 3party encrypt failed") }
    }
}
