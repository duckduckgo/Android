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

package com.duckduckgo.networkprotection.impl.devicemigration

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * NetP preferences arrive from the previous phone with that phone's tunnel enable time and data volume next to the preferred
 * location the user chose. A stale enable time is never overwritten when the tunnel next comes up, so the connected-since
 * timer would count from the previous phone. Only the preferred location is kept, so keys older installs left behind
 * (tunnel IPs, keys, server details) are dropped too. This runs regardless of the device migration kill switch because it
 * keeps NetP correct rather than adding to the migration experience.
 * The done mark never transfers, so a chained transfer runs the cleanup again on the next phone.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class MigratedInstallNetpStateCleanup @Inject constructor(
    private val deviceMigration: DeviceMigration,
    private val geoswitchingRepository: NetPGeoswitchingRepository,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (deviceMigration.isDoneOnThisInstall(DONE_KEY)) return@launch

            try {
                if (!deviceMigration.isMigratedInstall()) return@launch
                geoswitchingRepository.clearAllPreferencesExceptUserPreferredLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "Migrated install NetP cleanup failed, will retry on next start: ${e.javaClass.simpleName}" }
                return@launch
            }
            deviceMigration.markDoneOnThisInstall(DONE_KEY)
        }
    }

    private companion object {
        const val DONE_KEY = "netp.migratedInstallStateCleanup"
    }
}
