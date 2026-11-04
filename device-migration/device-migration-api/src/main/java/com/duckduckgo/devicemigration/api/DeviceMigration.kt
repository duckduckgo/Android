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

interface DeviceMigration {
    /** True when this install's data arrived through a device-to-device transfer. Fixed for the install's lifetime. */
    suspend fun isMigratedInstall(): Boolean

    /** True on a migrated install until the migration flow reaches its last screen. */
    suspend fun isMigrationFlowPending(): Boolean

    /** The previous phone's isNewInstall answer on a migrated install, null otherwise. Synchronous: AppBuildConfig.isNewInstall() is. */
    fun sourceDeviceIsNewInstall(): Boolean?

    /**
     * Marks the migration flow as finished, so [isMigrationFlowPending] returns false from now on, in this process and after restarts.
     * Harmless on an install that is not migrated.
     */
    suspend fun markMigrationFlowCompleted()

    /**
     * True when the previous phone had Sync set up, or when that is not known yet, so callers never offer to create a new
     * Sync account that would duplicate the one being restored silently. False when the install is not migrated.
     * Cheap and safe to call from any thread.
     */
    fun previousDeviceHadSync(): Boolean

    /**
     * Remote kill switch for everything a migrated install does on the new phone beyond detection: the migration flow, silent
     * restores, cleanup of the previous phone's state and suggestions that follow a transfer. While it is false, callers do none
     * of it and record none of it as done, so turning it on later still runs each piece once. Detection and the answers of
     * [isMigratedInstall] and [isMigrationFlowPending] do not depend on it.
     */
    suspend fun isMigrationExperienceEnabled(): Boolean

    /**
     * True once [markDoneOnThisInstall] has recorded [key] on this install. These marks never travel in a device transfer, so a
     * step that must run once per install (a cleanup of the previous phone's state, a dismissal) runs again on the next phone
     * without each feature having to keep its own file out of the backup rules. Keys share one namespace: prefix them with the
     * feature's name. Reads from disk: call it off the main thread where possible.
     */
    fun isDoneOnThisInstall(key: String): Boolean

    /** Records [key] as done on this install only; see [isDoneOnThisInstall]. Writes to disk synchronously. */
    fun markDoneOnThisInstall(key: String)
}
