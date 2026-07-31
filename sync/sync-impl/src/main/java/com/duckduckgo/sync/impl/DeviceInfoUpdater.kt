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
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Publishes this device's name to the server. Writes the cross-credential `device_info` blob and
 * the per-credential `name`/`type` fields in the same request, so clients that don't read `device_info` yet still see the new name.
 */
@WorkerThread
interface DeviceInfoUpdater {
    /** Returns the server's device list as it stands after the update. */
    fun updateThisDevice(name: String): Result<List<DeviceV2>>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDeviceInfoUpdater @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncDeviceIds: SyncDeviceIds,
    private val deviceInfoEncryptor: DeviceInfoEncryptor,
    private val deviceFieldEncryptor: DeviceFieldEncryptor,
) : DeviceInfoUpdater {

    override fun updateThisDevice(name: String): Result<List<DeviceV2>> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "UpdateDeviceInfo: not signed in")
        val type = syncDeviceIds.deviceType().deviceFactor

        val deviceInfo = when (val result = deviceInfoEncryptor.encrypt(name, type)) {
            is Success -> result.data
            is Error -> return result
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
            is Success -> Success(result.data.devicesV2)
            is Error -> result
        }
    }
}
