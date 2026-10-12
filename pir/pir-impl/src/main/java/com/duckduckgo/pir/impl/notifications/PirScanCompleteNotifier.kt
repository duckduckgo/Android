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

package com.duckduckgo.pir.impl.notifications

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.checker.PirRunMode
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirDataStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

interface PirScanCompleteNotifier {
    /** Called before every initial scan starts, from a profile save or a dashboard resume, so the run that finishes it can notify. */
    fun onInitialScanStarted()

    /**
     * Called when a run finishes normally, for either run mode. Posts that mode's scan-complete notification at
     * most once per [onInitialScanStarted]. Never throws, except for cancellation.
     */
    suspend fun onRunCompleted(runMode: PirRunMode)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPirScanCompleteNotifier @Inject constructor(
    private val pirDataStore: PirDataStore,
    private val pirNotificationManager: PirNotificationManager,
    private val pirPixelSender: PirPixelSender,
    private val dispatcherProvider: DispatcherProvider,
) : PirScanCompleteNotifier {

    // Both callers, the foreground scan service and the scheduled worker, run in :pir, so an in-process lock makes take-and-post exact.
    private val mutex = Mutex()

    override fun onInitialScanStarted() {
        pirDataStore.markScanCompleteNotificationPending()
    }

    override suspend fun onRunCompleted(runMode: PirRunMode) {
        withContext(dispatcherProvider.io()) {
            try {
                mutex.withLock {
                    if (!pirDataStore.takePendingScanCompleteNotification()) {
                        return@withLock
                    }

                    if (pirNotificationManager.showScanCompleteNotification(runMode)) {
                        reportSent(runMode)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "PIR-NOTIFICATION: Failed to post the scan complete notification: $e" }
            }
        }
    }

    private fun reportSent(runMode: PirRunMode) {
        when (runMode) {
            PirRunMode.SCAN_ONLY -> pirPixelSender.reportFreemiumScanCompleteNotificationSent()
            PirRunMode.SCAN_AND_OPT_OUT -> pirPixelSender.reportScanCompleteNotificationSent()
        }
    }
}
