/*
 * Copyright (c) 2023 DuckDuckGo
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
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.ConnectedDevice
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.*

@ContributesBinding(
    scope = AppScope::class,
    boundType = DeviceSyncState::class,
    rank = ContributesBinding.RANK_HIGHEST,
)
class AppDeviceSyncState @Inject constructor(
    private val syncFeatureToggle: SyncFeatureToggle,
    private val syncAccountRepository: SyncAccountRepository,
) : DeviceSyncState {

    override fun isUserSignedInOnDevice(): Boolean = syncAccountRepository.isSignedIn()

    override fun getAccountState(): SyncAccountState {
        if (!isUserSignedInOnDevice()) return SyncAccountState.SignedOut
        val accountInfo = syncAccountRepository.getAccountInfo()
        val devices = syncAccountRepository.getConnectedDevices().getOrNull() ?: emptyList()
        val devicesMapped = devices.map {
            ConnectedDevice(
                thisDevice = it.thisDevice,
                deviceName = it.deviceName,
                deviceId = it.deviceId,
                deviceType = it.deviceType.type(),
            )
        }
        return SyncAccountState.SignedIn(
            userId = accountInfo.userId,
            devices = devicesMapped,
        )
    }

    override fun isFeatureEnabled(): Boolean {
        return syncFeatureToggle.showSync()
    }

    override fun isDuckChatSyncFeatureEnabled(): Boolean {
        return syncFeatureToggle.allowAiChatSync()
    }
}
