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
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_DETECTED_PIXEL_SENT_AT
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SOURCE_DEFAULT_BROWSER
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SOURCE_SIGNED_IN_TO_SYNC
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class DeviceMigrationPixelName(override val pixelName: String) : Pixel.PixelName {
    DETECTED("m_device_migration_detected"),
    DAY_7("m_device_migration_day7"),
}

/**
 * Counts migrated installs and what they look like a week later. Both pixels carry the previous phone's state, because no
 * identifier links the two: the day-7 pixel alone has to answer how many users kept DuckDuckGo as default or kept Sync.
 * They fire when the app comes to the foreground, so a process started in the background for a worker never counts as an open.
 */
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class MigratedInstallPixels @Inject constructor(
    private val deviceMigration: DeviceMigration,
    private val markerStore: DeviceMigrationMarkerStore,
    private val pixel: Pixel,
    private val defaultBrowserDetector: Lazy<DefaultBrowserDetector>, // break any possible DI dependency cycle
    private val deviceSyncState: Lazy<DeviceSyncState>, // break any possible DI dependency cycle
    private val timeProvider: CurrentTimeProvider,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (deviceMigration.isDoneOnThisInstall(DAY_7_SENT_KEY)) return@launch
            if (!deviceMigration.isMigratedInstall()) return@launch
            if (!deviceMigration.isMigrationExperienceEnabled()) return@launch

            val local = markerStore.local
            if (!deviceMigration.isDoneOnThisInstall(DETECTED_SENT_KEY)) {
                pixel.enqueueFire(DeviceMigrationPixelName.DETECTED, previousPhoneParameters(), type = Unique())
                local.edit().putLong(KEY_DETECTED_PIXEL_SENT_AT, timeProvider.currentTimeMillis()).commit()
                deviceMigration.markDoneOnThisInstall(DETECTED_SENT_KEY)
                return@launch
            }

            val detectedAt = local.getLong(KEY_DETECTED_PIXEL_SENT_AT, timeProvider.currentTimeMillis())
            if (timeProvider.currentTimeMillis() - detectedAt < DAY_7_DELAY_MILLIS) return@launch

            val parameters = previousPhoneParameters() + mapOf(
                PARAM_IS_DEFAULT_BROWSER to defaultBrowserDetector.value.isDefaultBrowser().toString(),
                PARAM_SYNC_ENABLED to deviceSyncState.value.isUserSignedInOnDevice().toString(),
            )
            pixel.enqueueFire(DeviceMigrationPixelName.DAY_7, parameters, type = Unique())
            deviceMigration.markDoneOnThisInstall(DAY_7_SENT_KEY)
        }
    }

    // A previous phone that never opened a version writing these stamps leaves them out: unknown, not false.
    private fun previousPhoneParameters(): Map<String, String> {
        val local = markerStore.local
        val defaultBrowser = if (local.contains(KEY_SOURCE_DEFAULT_BROWSER)) {
            local.getBoolean(KEY_SOURCE_DEFAULT_BROWSER, false).toString()
        } else {
            UNKNOWN
        }
        val sync = when {
            !local.contains(KEY_SOURCE_SIGNED_IN_TO_SYNC) -> UNKNOWN
            local.getBoolean(KEY_SOURCE_SIGNED_IN_TO_SYNC, false) -> "yes"
            else -> "no"
        }
        return mapOf(PARAM_OLD_PHONE_DEFAULT_BROWSER to defaultBrowser, PARAM_OLD_PHONE_SYNC to sync)
    }

    private companion object {
        const val PARAM_OLD_PHONE_DEFAULT_BROWSER = "old_phone_default_browser"
        const val PARAM_OLD_PHONE_SYNC = "old_phone_sync"
        const val PARAM_IS_DEFAULT_BROWSER = "is_default_browser"
        const val PARAM_SYNC_ENABLED = "sync_enabled"
        const val UNKNOWN = "unknown"

        const val DETECTED_SENT_KEY = "deviceMigration.detectedPixelSent"
        const val DAY_7_SENT_KEY = "deviceMigration.day7PixelSent"
        val DAY_7_DELAY_MILLIS = TimeUnit.DAYS.toMillis(7)
    }
}
