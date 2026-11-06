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
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

@WorkerThread
interface RemoteDeviceLogout {
    /**
     * Removes another device from the sync account. This device's own logout also clears local state, so it stays in
     * [SyncAccountRepository.logout].
     *
     * [reportAccountErrorPixel] false keeps sync account error pixels out of logouts the user didn't start, such as one from a background sync.
     */
    fun logout(
        deviceId: String,
        reportAccountErrorPixel: Boolean = true,
    ): Result<Boolean>
}

@ContributesBinding(AppScope::class)
class RealRemoteDeviceLogout @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncPixels: SyncPixels,
) : RemoteDeviceLogout {

    override fun logout(
        deviceId: String,
        reportAccountErrorPixel: Boolean,
    ): Result<Boolean> {
        // A blank id from a malformed server entry names no device we can log out, so we skip it rather than send a request the server rejects.
        if (deviceId.isBlank()) return Error(reason = "Logout: Device Id Empty")

        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "Logout: Token Empty").alsoFirePixel(SyncAccountOperation.LOGOUT, reportAccountErrorPixel)

        return when (val result = syncApi.logout(token, deviceId)) {
            is Error -> result.alsoFirePixel(SyncAccountOperation.REMOVE_DEVICE, reportAccountErrorPixel).copy(code = GENERIC_ERROR.code)
            is Success -> Success(true)
        }
    }

    private fun Error.alsoFirePixel(
        operation: SyncAccountOperation,
        reportAccountErrorPixel: Boolean,
    ): Error {
        if (reportAccountErrorPixel) syncPixels.fireSyncAccountErrorPixel(this, operation)
        return this
    }
}
