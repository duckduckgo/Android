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
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_FLOW_PENDING
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_IS_NEW_INSTALL
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_LAUNCHED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_MIGRATED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SEEN
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SOURCE_IS_NEW_INSTALL
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.withContext

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
    private val dispatchers: DispatcherProvider,
) : DeviceMigration, MainProcessLifecycleObserver {

    // Detection must run once per process, before anything overwrites the transferred marker.
    private val detection: Detection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { detect() }

    override suspend fun isMigratedInstall(): Boolean = withContext(dispatchers.io()) { detection.migrated }

    override suspend fun isMigrationFlowPending(): Boolean = withContext(dispatchers.io()) {
        detection.migrated && markerStore.local.getBoolean(KEY_FLOW_PENDING, false)
    }

    override fun sourceDeviceIsNewInstall(): Boolean? = detection.sourceIsNewInstall

    internal fun markMigrationFlowCompleted() {
        markerStore.local.edit().putBoolean(KEY_FLOW_PENDING, false).commit()
    }

    override fun onCreate(owner: LifecycleOwner) {
        val sourceIsNewInstall = detection.sourceIsNewInstall
        markerStore.transferred.edit()
            .putBoolean(KEY_SEEN, true)
            .putBoolean(KEY_IS_NEW_INSTALL, installTimeComparison.isUnupdatedSinceFirstInstall() && (sourceIsNewInstall ?: true))
            .commit()
    }

    private fun detect(): Detection {
        val local = markerStore.local
        if (local.getBoolean(KEY_LAUNCHED, false)) {
            val migrated = local.getBoolean(KEY_MIGRATED, false)
            val source = if (migrated) local.getBoolean(KEY_SOURCE_IS_NEW_INSTALL, true) else null
            return Detection(migrated, source)
        }

        val transferred = markerStore.transferred
        if (!transferred.getBoolean(KEY_SEEN, false)) {
            local.edit().putBoolean(KEY_LAUNCHED, true).commit()
            return Detection(migrated = false, sourceIsNewInstall = null)
        }

        val source = transferred.getBoolean(KEY_IS_NEW_INSTALL, true)
        local.edit()
            .putBoolean(KEY_MIGRATED, true)
            .putBoolean(KEY_FLOW_PENDING, true)
            .putBoolean(KEY_SOURCE_IS_NEW_INSTALL, source)
            .putBoolean(KEY_LAUNCHED, true)
            .commit()
        return Detection(migrated = true, sourceIsNewInstall = source)
    }
}
