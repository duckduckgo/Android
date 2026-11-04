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

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_FLOW_PENDING
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_IS_NEW_INSTALL
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_LAUNCHED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_MIGRATED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_PREVIOUS_DEVICE_HAD_SYNC
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_RESTORED_FROM_BACKUP
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SEEN
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SIGNED_IN_TO_SYNC
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SOURCE_IS_NEW_INSTALL
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SOURCE_SIGNED_IN_TO_SYNC
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat

interface InstallTimeComparison {
    /** True when the package was never updated since its first install. */
    fun isUnupdatedSinceFirstInstall(): Boolean
}

@ContributesBinding(AppScope::class)
class RealInstallTimeComparison @Inject constructor(
    private val context: Context,
) : InstallTimeComparison {
    override fun isUnupdatedSinceFirstInstall(): Boolean {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.firstInstallTime == info.lastUpdateTime
    }
}

private data class Detection(val migrated: Boolean, val sourceIsNewInstall: Boolean?)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DeviceMigration>())
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
@ContributesIntoSet(AppScope::class, binding = binding<AtbInitializerListener>())
class RealDeviceMigration @Inject constructor(
    private val markerStore: DeviceMigrationMarkerStore,
    private val installTimeComparison: InstallTimeComparison,
    private val deviceMigrationFeature: DeviceMigrationFeature,
    private val statisticsDataStore: StatisticsDataStore,
    private val deviceSyncState: Lazy<DeviceSyncState>, // break any possible DI dependency cycle
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DeviceMigration, MainProcessLifecycleObserver, AtbInitializerListener {

    // Detection must run once per process, before anything overwrites the transferred marker.
    private val detection: Detection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { detect() }

    override suspend fun isMigratedInstall(): Boolean = withContext(dispatchers.io()) { detection.migrated }

    override suspend fun isMigrationFlowPending(): Boolean = withContext(dispatchers.io()) {
        detection.migrated && markerStore.local.getBoolean(KEY_FLOW_PENDING, false)
    }

    override fun sourceDeviceIsNewInstall(): Boolean? = detection.sourceIsNewInstall

    override suspend fun markMigrationFlowCompleted() {
        withContext(dispatchers.io()) {
            markerStore.local.edit().putBoolean(KEY_FLOW_PENDING, false).commit()
        }
    }

    // Either signal is enough: the previous phone may have been signed in without leaving a restore payload, or the reverse.
    // Unknown counts as "had Sync": wrongly offering to create an account is worse than withholding the offer.
    override fun previousDeviceHadSync(): Boolean {
        if (!detection.migrated) return false
        val local = markerStore.local
        return local.getBoolean(KEY_SOURCE_SIGNED_IN_TO_SYNC, true) || local.getBoolean(KEY_PREVIOUS_DEVICE_HAD_SYNC, true)
    }

    override suspend fun isMigrationExperienceEnabled(): Boolean = withContext(dispatchers.io()) {
        deviceMigrationFeature.self().isEnabled()
    }

    override fun onCreate(owner: LifecycleOwner) {
        // Detection and the stamps write to disk, so none of it may run on the main thread at startup. Detection runs first:
        // it has to read the transferred marker before this phone's stamp overwrites it.
        appCoroutineScope.launch(dispatchers.io()) {
            val sourceIsNewInstall = detection.sourceIsNewInstall
            markerStore.transferred.edit()
                .putBoolean(KEY_SEEN, true)
                .putBoolean(KEY_IS_NEW_INSTALL, installTimeComparison.isUnupdatedSinceFirstInstall() && (sourceIsNewInstall ?: true))
                .commit()
            // Sync can be turned on or off between starts, so the stamp is refreshed every time.
            try {
                val signedIn = deviceSyncState.value.isUserSignedInOnDevice()
                markerStore.transferred.edit().putBoolean(KEY_SIGNED_IN_TO_SYNC, signedIn).commit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "Could not record Sync state for a future device transfer: ${e.javaClass.simpleName}" }
            }
        }
    }

    // Detection treats an ATB on a fresh package as transferred data, so it must run before this install's first ATB is saved.
    override suspend fun beforeAtbInit() {
        detection
    }

    override fun beforeAtbInitTimeoutMillis(): Long = ATB_INIT_TIMEOUT_MS

    /**
     * An old phone that never opened a version writing the transferred marker still hands over its data. Until this install's
     * first detection, an ATB on a package never updated since install can only have come from that transfer: cloud backup is
     * excluded, and the first ATB of this install is saved only after [beforeAtbInit] has run.
     */
    private fun detect(): Detection {
        val local = markerStore.local
        if (local.getBoolean(KEY_LAUNCHED, false)) {
            val migrated = local.getBoolean(KEY_MIGRATED, false)
            val source = if (migrated) local.getBoolean(KEY_SOURCE_IS_NEW_INSTALL, true) else null
            return Detection(migrated, source)
        }

        val transferred = markerStore.transferred
        if (transferred.getBoolean(KEY_SEEN, false)) {
            return recordMigrated(source = transferred.getBoolean(KEY_IS_NEW_INSTALL, true))
        }

        // Set by DeviceMigrationBackupAgent on this phone; unlike the heuristic below, it survives an app update before first open.
        if (local.getBoolean(KEY_RESTORED_FROM_BACKUP, false)) {
            return recordMigrated(source = false)
        }

        if (installTimeComparison.isUnupdatedSinceFirstInstall() && statisticsDataStore.hasInstallationStatistics) {
            return recordMigrated(source = false)
        }

        local.edit().putBoolean(KEY_LAUNCHED, true).commit()
        return Detection(migrated = false, sourceIsNewInstall = null)
    }

    private fun recordMigrated(source: Boolean): Detection {
        val transferred = markerStore.transferred
        markerStore.local.edit()
            .apply {
                // Copied now because this phone overwrites the transferred stamp with its own state on every start.
                if (transferred.contains(KEY_SIGNED_IN_TO_SYNC)) {
                    putBoolean(KEY_SOURCE_SIGNED_IN_TO_SYNC, transferred.getBoolean(KEY_SIGNED_IN_TO_SYNC, true))
                }
            }
            .putBoolean(KEY_MIGRATED, true)
            .putBoolean(KEY_FLOW_PENDING, true)
            .putBoolean(KEY_SOURCE_IS_NEW_INSTALL, source)
            .putBoolean(KEY_LAUNCHED, true)
            .commit()
        return Detection(migrated = true, sourceIsNewInstall = source)
    }

    private companion object {
        const val ATB_INIT_TIMEOUT_MS = 1_000L
    }
}
