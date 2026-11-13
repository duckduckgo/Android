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

package com.duckduckgo.sync.impl.promotion.devicemigration

import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.SyncAfterDeviceTransferPrompt
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

/**
 * The previous phone's Sync state is checked through [DeviceMigration.previousDeviceHadSync], which stays true until it is known,
 * so the suggestion never competes with the silent restore of an existing account.
 * The dismissal is a done mark on this install, so it never travels to a later phone.
 */
@ContributesBinding(AppScope::class)
class RealSyncAfterDeviceTransferPrompt @Inject constructor(
    private val deviceMigration: DeviceMigration,
    private val deviceSyncState: DeviceSyncState,
) : SyncAfterDeviceTransferPrompt {

    override suspend fun shouldShow(): Boolean {
        if (deviceMigration.isDoneOnThisInstall(DISMISSED_KEY)) return false
        if (!deviceMigration.isMigratedInstall()) return false
        if (!deviceMigration.isMigrationExperienceEnabled()) return false
        if (deviceMigration.previousDeviceHadSync()) return false
        if (deviceMigration.isMigrationFlowPending()) return false
        return deviceSyncState.isFeatureEnabled() && !deviceSyncState.isUserSignedInOnDevice()
    }

    override suspend fun dismiss() {
        deviceMigration.markDoneOnThisInstall(DISMISSED_KEY)
    }

    private companion object {
        const val DISMISSED_KEY = "sync.afterDeviceTransferPromptDismissed"
    }
}
