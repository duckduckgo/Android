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

package com.duckduckgo.mobile.android.vpn.devicemigration

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * The VPN database arrives from the previous phone with that phone's running state and blocked-tracker history, next to the
 * per-app exclusions the user chose. The running state would make the heartbeat monitor restart a VPN that was never enabled
 * here, so it is dropped once after a device transfer. This runs regardless of the device migration kill switch because it
 * keeps the VPN correct rather than adding to the migration experience.
 * The done mark never transfers, so a chained transfer runs the cleanup again on the next phone.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class MigratedInstallVpnStateCleanup @Inject constructor(
    private val deviceMigration: DeviceMigration,
    private val vpnDatabase: VpnDatabase,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    private val mutex = Mutex()

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) { cleanUpIfNeeded() }
    }

    /**
     * Safe to call from any reader that may run before [onCreate]'s cleanup finishes; returns once it is done or not needed.
     * @return false when the cleanup failed, so the previous phone's state may still be in place.
     */
    suspend fun cleanUpIfNeeded(): Boolean = withContext(dispatchers.io()) {
        mutex.withLock {
            if (deviceMigration.isDoneOnThisInstall(DONE_KEY)) return@withLock true

            try {
                if (!deviceMigration.isMigratedInstall()) return@withLock true
                vpnDatabase.runInTransaction {
                    vpnDatabase.vpnHeartBeatDao().deleteAll()
                    vpnDatabase.vpnServiceStateDao().deleteAll()
                    appTrackerBlockingStatsRepository.deleteAllTrackers()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "Migrated install VPN cleanup failed, will retry on next start: ${e.javaClass.simpleName}" }
                return@withLock false
            }
            deviceMigration.markDoneOnThisInstall(DONE_KEY)
            true
        }
    }

    private companion object {
        const val DONE_KEY = "vpn.migratedInstallStateCleanup"
    }
}
