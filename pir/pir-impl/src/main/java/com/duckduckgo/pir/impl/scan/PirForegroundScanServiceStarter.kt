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

package com.duckduckgo.pir.impl.scan

import android.content.Context
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import dev.zacsweers.metro.ContributesBinding
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/** Starts [PirForegroundScanService] from the dashboard. */
interface PirForegroundScanServiceStarter {
    /** Resumes an interrupted initial scan. */
    fun startResumeScan()

    /** Shows the notification of a scan that is already running, e.g. once the notification permission is granted. */
    fun showScanNotificationIfRunning()
}

@ContributesBinding(ActivityScope::class)
class RealPirForegroundScanServiceStarter @Inject constructor(
    private val context: Context,
    private val pirForegroundScanServiceMonitor: PirForegroundScanServiceMonitor,
) : PirForegroundScanServiceStarter {
    override fun startResumeScan() {
        context.startForegroundService(
            PirForegroundScanService.intentFor(context, PirExecutionType.MANUAL_INITIAL_RESUME),
        )
    }

    override fun showScanNotificationIfRunning() {
        if (!pirForegroundScanServiceMonitor.isRunning()) return

        // not startForegroundService, which crashes if the scan finishes before the service can call startForeground
        runCatching { context.startService(PirForegroundScanService.intentForShowScanNotification(context)) }
            .onFailure { logcat(WARN) { "PIR-SCAN: Could not show the scan notification: ${it.message}" } }
    }
}
