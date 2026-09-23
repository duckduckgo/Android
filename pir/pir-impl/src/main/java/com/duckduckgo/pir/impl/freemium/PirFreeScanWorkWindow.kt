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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.PirJobConstants.FREE_SCAN_BACKGROUND_WINDOW_DAYS
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface PirFreeScanWorkWindow {
    /**
     * Whether a scan-only user's initial scan still needs the periodic scan worker. A free user gets
     * background scans only so a killed foreground scan can resume, so the window closes once every
     * free-scannable broker holds a terminal result, or once the time bound expires.
     */
    suspend fun isOpen(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPirFreeScanWorkWindow @Inject constructor(
    private val pirFreeScanBrokerFilter: PirFreeScanBrokerFilter,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val pirFreemiumDataStore: PirFreemiumDataStore,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
) : PirFreeScanWorkWindow {

    override suspend fun isOpen(): Boolean = withContext(dispatcherProvider.io()) {
        if (hasTimeBoundExpired()) return@withContext false

        val scannableBrokers = pirFreeScanBrokerFilter.freeScannableBrokerNames()
        // Nothing this user is allowed to scan means no run can ever make progress.
        if (scannableBrokers.isEmpty()) return@withContext false

        val outstanding = pirSchedulingRepository.getAllValidScanJobRecords()
            .filter { !it.deprecated && it.brokerName in scannableBrokers }

        // No records yet means the first run has not created them, so the worker is still needed.
        outstanding.isEmpty() || outstanding.any { !it.hasTerminalResult() }
    }

    private fun hasTimeBoundExpired(): Boolean {
        val firstProfileSavedAt = pirFreemiumDataStore.firstProfileSavedTimestamp
        if (firstProfileSavedAt == 0L) return false
        return currentTimeProvider.currentTimeMillis() - firstProfileSavedAt >= WINDOW_DURATION_MS
    }

    private fun ScanJobRecord.hasTerminalResult(): Boolean =
        status == ScanJobStatus.NO_MATCH_FOUND || status == ScanJobStatus.MATCHES_FOUND

    private companion object {
        val WINDOW_DURATION_MS = TimeUnit.DAYS.toMillis(FREE_SCAN_BACKGROUND_WINDOW_DAYS)
    }
}
