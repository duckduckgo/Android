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

package com.duckduckgo.devicemigration.api

import com.duckduckgo.navigation.api.GlobalActivityStarter

interface DeviceMigration {
    /** True when this install's data arrived through a device-to-device transfer. Fixed for the install's lifetime. */
    suspend fun isMigratedInstall(): Boolean

    /** True on a migrated install until the migration flow reaches its last screen. */
    suspend fun isMigrationFlowPending(): Boolean

    /** The previous phone's isNewInstall answer on a migrated install, null otherwise. Synchronous: AppBuildConfig.isNewInstall() is. */
    fun sourceDeviceIsNewInstall(): Boolean?
}

/** Launch params for the screen shown to a migrated install. */
object DeviceMigrationFlowParams : GlobalActivityStarter.ActivityParams
