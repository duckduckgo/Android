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
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_DEFAULT_BROWSER
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_FLOW_PENDING
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_IS_NEW_INSTALL
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_LAUNCHED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_MIGRATED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_PREVIOUS_DEVICE_HAD_SYNC
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_RESTORED_FROM_BACKUP
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SEEN
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SIGNED_IN_TO_SYNC
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SOURCE_DEFAULT_BROWSER
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
class RealDeviceMigration @Inject constructor(
    private val markerStore: DeviceMigrationMarkerStore,
    private val installTimeComparison: InstallTimeComparison,
    private val deviceMigrationFeature: DeviceMigrationFeature,
    private val deviceSyncState: Lazy<DeviceSyncState>, // break any possible DI dependency cycle
    private val defaultBrowserDetector: Lazy<DefaultBrowserDetector>, // break any possible DI dependency cycle
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DeviceMigration, MainProcessLifecycleObserver {

    // Detection must run once per process, before anything overwrites the transferred marker.
    private val detection: Detection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { detect() }

    override suspend fun isMigratedInstall(): Boolean = withContext(dispatchers.io()) { detection.migrated }

    override fun sourceDeviceIsNewInstall(): Boolean? = detection.sourceIsNewInstall

    override suspend fun sourceDeviceWasDefaultBrowser(): Boolean? = withContext(dispatchers.io()) {
        val local = markerStore.local
        if (detection.migrated && local.contains(KEY_SOURCE_DEFAULT_BROWSER)) local.getBoolean(KEY_SOURCE_DEFAULT_BROWSER, false) else null
    }

    override suspend fun isMigrationFlowPending(): Boolean = withContext(dispatchers.io()) {
        detection.migrated && markerStore.local.getBoolean(KEY_FLOW_PENDING, false)
    }

    override suspend fun markMigrationFlowCompleted() {
        withContext(dispatchers.io()) {
            markerStore.local.edit().putBoolean(KEY_FLOW_PENDING, false).commit()
        }
    }

    // Only the restorer's finding decides, not the old phone's stamp that it was signed in: with no restore payload the user would
    // otherwise be left without Sync and without the suggestion, whose setup still offers to join the existing account. Until the
    // restorer has answered, it counts as "had Sync", so the suggestion never competes with a silent restore in progress.
    override suspend fun previousDeviceHadSync(): Boolean = withContext(dispatchers.io()) {
        detection.migrated && markerStore.local.getBoolean(KEY_PREVIOUS_DEVICE_HAD_SYNC, true)
    }

    override suspend fun isMigrationExperienceEnabled(): Boolean = withContext(dispatchers.io()) {
        deviceMigrationFeature.self().isEnabled()
    }

    override fun isDoneOnThisInstall(key: String): Boolean = markerStore.local.getBoolean(DONE_ON_THIS_INSTALL_PREFIX + key, false)

    override fun markDoneOnThisInstall(key: String) {
        markerStore.local.edit().putBoolean(DONE_ON_THIS_INSTALL_PREFIX + key, true).commit()
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
            stampSyncState()
            stampDefaultBrowser()
        }
    }

    // The default browser is changed in system settings, so the user comes back to the app through a resume, not a new process.
    override fun onResume(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            detection
            stampDefaultBrowser()
        }
    }

    // A transfer app backs up the app while it is in the background, often right after the user turned Sync on in this process.
    override fun onStop(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            detection
            stampSyncState()
        }
    }

    private fun stampSyncState() {
        try {
            val signedIn = deviceSyncState.value.isUserSignedInOnDevice()
            val transferred = markerStore.transferred
            if (transferred.contains(KEY_SIGNED_IN_TO_SYNC) && transferred.getBoolean(KEY_SIGNED_IN_TO_SYNC, false) == signedIn) return
            transferred.edit().putBoolean(KEY_SIGNED_IN_TO_SYNC, signedIn).commit()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(ERROR) { "Could not record Sync state for a future device transfer: ${e.javaClass.simpleName}" }
        }
    }

    private fun stampDefaultBrowser() {
        val transferred = markerStore.transferred
        val isDefaultBrowser = defaultBrowserDetector.value.isDefaultBrowser()
        if (transferred.contains(KEY_DEFAULT_BROWSER) && transferred.getBoolean(KEY_DEFAULT_BROWSER, false) == isDefaultBrowser) return
        transferred.edit().putBoolean(KEY_DEFAULT_BROWSER, isDefaultBrowser).commit()
    }

    /**
     * Only the migration's own signals count: the marker the old phone writes when the app starts, and the restore that
     * [DeviceMigrationBackupAgent] records on this phone. The agent sends nothing until the app has started on the old phone,
     * so a transfer that brings data also brings at least one of them. Other data on a fresh install, such as an ATB, is not
     * evidence of a transfer.
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

        // Set by DeviceMigrationBackupAgent on this phone, so it does not depend on the old phone's stamp being written before the backup.
        if (local.getBoolean(KEY_RESTORED_FROM_BACKUP, false)) {
            return recordMigrated(source = false)
        }

        local.edit().putBoolean(KEY_LAUNCHED, true).commit()
        return Detection(migrated = false, sourceIsNewInstall = null)
    }

    private fun recordMigrated(source: Boolean): Detection {
        val transferred = markerStore.transferred
        markerStore.local.edit()
            .apply {
                // Copied now because this phone overwrites the transferred stamps with its own state on every start.
                if (transferred.contains(KEY_SIGNED_IN_TO_SYNC)) {
                    putBoolean(KEY_SOURCE_SIGNED_IN_TO_SYNC, transferred.getBoolean(KEY_SIGNED_IN_TO_SYNC, true))
                }
                if (transferred.contains(KEY_DEFAULT_BROWSER)) {
                    putBoolean(KEY_SOURCE_DEFAULT_BROWSER, transferred.getBoolean(KEY_DEFAULT_BROWSER, false))
                }
            }
            .putBoolean(KEY_MIGRATED, true)
            .putBoolean(KEY_SOURCE_IS_NEW_INSTALL, source)
            .putBoolean(KEY_FLOW_PENDING, true)
            .putBoolean(KEY_LAUNCHED, true)
            .commit()
        return Detection(migrated = true, sourceIsNewInstall = source)
    }

    private companion object {
        // Kept apart from the detection keys that share the local file.
        const val DONE_ON_THIS_INSTALL_PREFIX = "doneOnThisInstall."
    }
}
