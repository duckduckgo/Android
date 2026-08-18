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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * The single writer of this device's name. Writes the cross-credential `device_info` blob and the per-credential `name`/`type` fields in the
 * same request, so clients that don't read `device_info` yet still see the new name.
 */
interface DeviceInfoUpdater {
    /**
     * Sets this device's name to [name]
     *
     * @return the server's device list as it stands after the update
     */
    suspend fun setThisDeviceName(name: String): Result<List<DeviceV2>>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDeviceInfoUpdater @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncDeviceIds: SyncDeviceIds,
    private val deviceInfoEncryptor: DeviceInfoEncryptor,
    private val deviceFieldEncryptor: DeviceFieldEncryptor,
    private val accountInfoKeyManager: AccountInfoKeyManager,
    private val syncFeature: SyncFeature,
    private val dispatchers: DispatcherProvider,
) : DeviceInfoUpdater {

    private val mutex = Mutex()

    override suspend fun setThisDeviceName(name: String): Result<List<DeviceV2>> = withContext(dispatchers.io()) {
        mutex.withLock { setName(name) }
    }

    /**
     * Sets this device's name
     *
     * @param name The new device name
     * @return the server's device list as it stands after the update
     */
    private suspend fun setName(name: String): Result<List<DeviceV2>> {
        val includeDeviceInfo = syncFeature.canWriteUnifiedDeviceList().isEnabled()

        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "UpdateDeviceInfo: not signed in")
        val type = syncDeviceIds.deviceType().deviceFactor

        val deviceInfo = if (includeDeviceInfo) {
            when (val keyResult = ensureAccountInfoKey()) {
                is Success -> Unit
                is Error -> return keyResult
            }
            when (val result = deviceInfoEncryptor.encrypt(name, type)) {
                is Success -> result.data
                is Error -> return result
            }
        } else {
            null
        }

        val legacyFields = when (val result = deviceFieldEncryptor.encrypt(name, type)) {
            is Success -> result.data
            is Error -> return result
        }

        return when (
            val result = syncApi.patchThisDevice(
                token = token,
                encryptedName = legacyFields.name,
                encryptedType = legacyFields.type,
                deviceInfo = deviceInfo,
            )
        ) {
            is Success -> {
                syncStore.deviceName = name
                Success(result.data.devicesV2)
            }
            is Error -> result
        }
    }

    /**
     * The `account_info` key is immutable for the lifetime of the account, so a cached public key is always the right one to encrypt with and
     * needs no network call. Otherwise, register one, which creates it or adopts the account's existing key.
     */
    private suspend fun ensureAccountInfoKey(): Result<Unit> {
        if (syncStore.accountInfoPublicKey != null) return Success(Unit)

        logcat { "Sync-UnifiedDevices: no cached account_info public key; registering one before writing device_info" }
        return when (val result = accountInfoKeyManager.ensureKeyRegistered()) {
            is Success -> {
                logcat { "Sync-UnifiedDevices: account_info key ready (kid=${result.data.kid})" }
                Success(Unit)
            }
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: cannot write device_info, account_info key unavailable: ${result.reason}" }
                result
            }
        }
    }
}
