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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_PREVIOUS_DEVICE_HAD_SYNC
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SYNC_RESTORE_DONE
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncAutoRestore
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.logcat

/**
 * Sync credentials are not transferred to the new phone, but the auto-restore token may be. When it is, the account is restored
 * silently and the migration flow must not offer to create a new one, which would end up as a second account. The outcome is
 * recorded once, in the local preference file, which is excluded from transfer so a chained transfer runs this again.
 * A failed restore leaves the user without Sync rather than prompting them.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class MigratedInstallSyncRestorer @Inject constructor(
    private val deviceMigration: DeviceMigration,
    private val syncAutoRestore: SyncAutoRestore,
    private val markerStore: DeviceMigrationMarkerStore,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            val local = markerStore.local
            if (local.getBoolean(KEY_SYNC_RESTORE_DONE, false)) return@launch
            if (!deviceMigration.isMigratedInstall()) return@launch

            try {
                val hadSync = syncAutoRestore.canRestore()
                // Recorded before the restore so a crash during it cannot trigger a second restore on the next start.
                local.edit()
                    .putBoolean(KEY_PREVIOUS_DEVICE_HAD_SYNC, hadSync)
                    .putBoolean(KEY_SYNC_RESTORE_DONE, true)
                    .commit()
                if (hadSync) {
                    logcat(INFO) { "Restoring Sync after device transfer" }
                    syncAutoRestore.restoreSyncAccount()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "Sync restore after device transfer failed: ${e.javaClass.simpleName}" }
            }
        }
    }
}
