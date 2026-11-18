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

    /**
     * On a migrated install, whether the previous phone counted as a new install when it last started the app, or false when that
     * phone never recorded it. Null when this install is not migrated.
     */
    fun sourceDeviceIsNewInstall(): Boolean?

    /**
     * On a migrated install, whether DuckDuckGo was the default browser on the previous phone when that phone last recorded it.
     * Null when this install is not migrated, or when the previous phone never recorded it.
     */
    suspend fun sourceDeviceWasDefaultBrowser(): Boolean?

    /**
     * True on a migrated install until [markMigrationFlowCompleted] is called. Only installs first detected by a version that has
     * the migration flow are pending, so an install migrated before it shipped never gets the flow late.
     */
    suspend fun isMigrationFlowPending(): Boolean

    /** Records that the migration flow ended, completed or skipped, so [isMigrationFlowPending] stays false from now on. */
    suspend fun markMigrationFlowCompleted()

    /**
     * True when the previous phone's Sync account was found to restore silently, or when that is not known yet, so callers never
     * offer to set up Sync while it is being restored. False when the install is not migrated, or when nothing was found to
     * restore, even if the previous phone was signed in.
     */
    suspend fun previousDeviceHadSync(): Boolean

    /**
     * Remote kill switch for what a migrated install adds on the new phone: silent restores, the migration pixels and resets that
     * only improve the experience. While it is false, callers do none of it and record none of it as done, so turning it on later
     * still runs each piece once. Detection, [isMigratedInstall] and cleanups that keep a feature correct do not depend on it.
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
