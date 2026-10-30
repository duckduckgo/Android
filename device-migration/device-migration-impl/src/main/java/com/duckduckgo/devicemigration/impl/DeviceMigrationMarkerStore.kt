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

package com.duckduckgo.devicemigration.impl

import android.content.SharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import dev.zacsweers.metro.Inject

/**
 * Two preference files tell a migrated install apart. [TRANSFERRED_FILE] is copied to a new phone by the OS transfer;
 * [LOCAL_FILE] is excluded from it, so on a new phone only the first one exists.
 */
@Inject
class DeviceMigrationMarkerStore(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    val transferred: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(TRANSFERRED_FILE) }
    val local: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(LOCAL_FILE) }

    companion object {
        const val TRANSFERRED_FILE = "com.duckduckgo.devicemigration.transferred"
        const val KEY_SEEN = "seen"
        const val KEY_IS_NEW_INSTALL = "isNewInstall"

        const val LOCAL_FILE = "com.duckduckgo.devicemigration.local"
        const val KEY_LAUNCHED = "launched"
        const val KEY_MIGRATED = "migrated"
        const val KEY_FLOW_PENDING = "flowPending"
        const val KEY_SOURCE_IS_NEW_INSTALL = "sourceIsNewInstall"
        const val KEY_SYNC_RESTORE_DONE = "syncRestoreDone"
        const val KEY_PREVIOUS_DEVICE_HAD_SYNC = "previousDeviceHadSync"
    }
}
