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

package com.duckduckgo.sync

import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState

class FakeDeviceSyncState : DeviceSyncState {
    @get:JvmName(name = "_isFeatureEnabled")
    var isFeatureEnabled = false

    @get:JvmName(name = "_isUserSignedInOnDevice")
    var isUserSignedInOnDevice = false

    @get:JvmName(name = "_isDuckChatSyncFeatureEnabled")
    var isDuckChatSyncFeatureEnabled = false

    @get:JvmName(name = "_accountState")
    var accountState = SyncAccountState.SignedOut

    override fun isFeatureEnabled(): Boolean = isFeatureEnabled

    override fun isUserSignedInOnDevice(): Boolean = isUserSignedInOnDevice

    override fun isDuckChatSyncFeatureEnabled(): Boolean = isDuckChatSyncFeatureEnabled

    override fun getAccountState(): SyncAccountState = accountState
}
