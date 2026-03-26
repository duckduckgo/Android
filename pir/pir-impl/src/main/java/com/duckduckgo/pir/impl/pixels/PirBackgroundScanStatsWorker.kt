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

package com.duckduckgo.pir.impl.pixels

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.PirJobConstants
import com.duckduckgo.pir.impl.store.PirRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class PirBackgroundScanStatsWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var pirPixelSender: PirPixelSender

    @Inject
    lateinit var pirRepository: PirRepository

    @Inject
    lateinit var currentTimeProvider: CurrentTimeProvider

    override suspend fun doWork(): Result {
        /**
         * If lastRun is 0, that should be an invalid scenario. This runner gets scheduled after a successful initial scan run that should set
         * the first value for this data. In this case we consider the threshold not met and report accordingly.
         * If the diff between current time and last run is less than 48 hours, we consider the frequency acceptable.
         * If the diff is more than 48 hours, it means the job is not running as expected, and we report that.
         */
        val lastRun = pirRepository.latestBackgroundScanRunInMs()
        val diffMs = currentTimeProvider.currentTimeMillis() - lastRun
        val scanFrequencyWithinThreshold = diffMs <= TimeUnit.HOURS.toMillis(PirJobConstants.BG_SCAN_RUN_THRESHOLD_HRS)
        pirPixelSender.reportBackgroundScanStats(
            scanFrequencyWithinThreshold = scanFrequencyWithinThreshold,
        )

        return Result.success()
    }

    companion object {
        const val TAG_PIR_BACKGROUND_STATS_DAILY = "TAG_PIR_BACKGROUND_STATS_DAILY"
    }
}
