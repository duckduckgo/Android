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

package com.duckduckgo.app.sync

import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState
import javax.inject.*

/**
 * We need to provide this fake implementation for non internal builds until we can add sync modules dependencies for all flavors.
 * Note: AppDeviceSyncState in sync-impl has RANK_HIGHEST and always wins. This fake is effectively dead code
 * but kept for build variants that might not include sync-impl.
 */
class FakeDeviceSyncState : DeviceSyncState {
    override fun isFeatureEnabled(): Boolean = false
    override fun isUserSignedInOnDevice(): Boolean = false
    override fun isDuckChatSyncFeatureEnabled(): Boolean = false
    override fun getAccountState(): SyncAccountState = SyncAccountState.SignedOut
}
