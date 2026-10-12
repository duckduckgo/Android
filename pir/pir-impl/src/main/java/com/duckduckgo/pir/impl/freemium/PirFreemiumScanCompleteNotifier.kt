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

package com.duckduckgo.pir.impl.freemium

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.checker.PirRunMode
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

interface PirFreemiumScanCompleteNotifier {
    /**
     * Called whenever a run's scan phase completes normally, for either run mode. Posts the freemium
     * scan-complete notification at most once per free profile save. Never throws, except for cancellation.
     */
    suspend fun onScanCompleted(runMode: PirRunMode)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPirFreemiumScanCompleteNotifier @Inject constructor(
    private val pirFreemiumDataStore: PirFreemiumDataStore,
    private val pirNotificationManager: PirNotificationManager,
    private val pirPixelSender: PirPixelSender,
    private val dispatcherProvider: DispatcherProvider,
) : PirFreemiumScanCompleteNotifier {

    // Both consumers, the foreground scan service and the scheduled worker, run in :pir, so an in-process lock makes check-and-clear exact.
    private val mutex = Mutex()

    override suspend fun onScanCompleted(runMode: PirRunMode) {
        withContext(dispatcherProvider.io()) {
            try {
                mutex.withLock {
                    if (!pirFreemiumDataStore.takePendingScanCompleteNotification()) {
                        return@withLock
                    }
                    if (runMode != PirRunMode.SCAN_ONLY) {
                        return@withLock
                    }

                    if (pirNotificationManager.showScanCompleteNotification(PirRunMode.SCAN_ONLY)) {
                        pirPixelSender.reportFreemiumScanCompleteNotificationSent()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "PIR-FREEMIUM: Failed to post the scan complete notification: $e" }
            }
        }
    }
}
