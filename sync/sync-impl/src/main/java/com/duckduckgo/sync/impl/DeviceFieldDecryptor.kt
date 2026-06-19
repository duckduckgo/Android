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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Decrypts `name` + `type` for a single `entries_v2` entry. 3party uses the scoped MEK; ddg/null
 * uses the local primary key. Stateless — caller handles refresh/retry on [Result.Error].
 */
interface DeviceFieldDecryptor {
    fun decrypt(entry: DeviceV2): Result<DecryptedDevice>
}

data class DecryptedDevice(
    val deviceId: String,
    val name: String,
    val type: String?,
)

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDeviceFieldDecryptor @Inject constructor(
    private val syncStore: SyncStore,
    private val syncJweCrypto: SyncJweCrypto,
    private val nativeLib: SyncLib,
) : DeviceFieldDecryptor {

    override fun decrypt(entry: DeviceV2): Result<DecryptedDevice> {
        val deviceId = entry.deviceId ?: return Result.Error(reason = "DeviceFieldDecryptor: entry missing id")

        return when (entry.credentialId) {
            CREDENTIAL_ID_3PARTY -> decryptThirdParty(entry, deviceId)
            CREDENTIAL_ID_DDG, null -> decryptDdg(entry, deviceId)
            else -> Result.Error(reason = "DeviceFieldDecryptor: unknown credential_id=${entry.credentialId}")
        }
    }

    private fun decryptThirdParty(
        entry: DeviceV2,
        deviceId: String,
    ): Result<DecryptedDevice> {
        val scopedPassword = syncStore.scopedPassword?.raw ?: return Result.Error(reason = "DeviceFieldDecryptor: scopedPassword missing")
        val userId = syncStore.userId ?: return Result.Error(reason = "DeviceFieldDecryptor: userId missing")

        val thirdPartyMainKey = runCatching {
            syncJweCrypto.hkdfDeriveBytes(scopedPassword, userId.toByteArray(Charsets.UTF_8), "Main Key", 32)
        }.getOrElse {
            return Result.Error(reason = "DeviceFieldDecryptor: 3p key derivation failed: ${it.message}")
        }

        val encryptedName = entry.deviceName ?: return Result.Error(reason = "DeviceFieldDecryptor: 3p entry $deviceId missing name")
        val name = runCatching {
            String(syncJweCrypto.jweDecryptSymmetric(encryptedName, thirdPartyMainKey), Charsets.UTF_8)
        }.getOrElse {
            return Result.Error(reason = "DeviceFieldDecryptor: 3p name decrypt failed: ${it.message}")
        }

        val type = entry.deviceType?.takeUnless { it.isEmpty() }?.let { encryptedType ->
            runCatching {
                String(syncJweCrypto.jweDecryptSymmetric(encryptedType, thirdPartyMainKey), Charsets.UTF_8)
            }.getOrElse {
                return Result.Error(reason = "DeviceFieldDecryptor: 3p type decrypt failed: ${it.message}")
            }
        }

        return Result.Success(DecryptedDevice(deviceId, name, type))
    }

    private fun decryptDdg(
        entry: DeviceV2,
        deviceId: String,
    ): Result<DecryptedDevice> {
        val primaryKey = syncStore.primaryKey?.takeUnless { it.isEmpty() } ?: return Result.Error(reason = "DeviceFieldDecryptor: primaryKey missing")

        val encryptedName = entry.deviceName ?: return Result.Error(reason = "DeviceFieldDecryptor: ddg entry $deviceId missing name")
        val name = runCatching { nativeLib.decryptData(encryptedName, primaryKey).decryptedData }
            .getOrElse {
                return Result.Error(reason = "DeviceFieldDecryptor: ddg name decrypt failed: ${it.message}")
            }

        val type = entry.deviceType?.takeUnless { it.isEmpty() }?.let { encryptedType ->
            runCatching { nativeLib.decryptData(encryptedType, primaryKey).decryptedData }
                .getOrElse {
                    return Result.Error(reason = "DeviceFieldDecryptor: ddg type decrypt failed: ${it.message}")
                }
        }

        return Result.Success(DecryptedDevice(deviceId, name, type))
    }
}
