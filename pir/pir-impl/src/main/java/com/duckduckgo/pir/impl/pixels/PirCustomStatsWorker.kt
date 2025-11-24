/*
 * Copyright (c) 2025 DuckDuckGo
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
import com.duckduckgo.di.scopes.AppScope
import logcat.logcat
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class PirCustomStatsWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var optOutSubmissionSuccessRateReporter: OptOut24HourSubmissionSuccessRateReporter

    @Inject
    lateinit var optOutConfirmationReporter: OptOutConfirmationReporter

    @Inject
    lateinit var engagementReporter: PirEngagementReporter

    @Inject
    lateinit var weeklyPixelReporter: WeeklyPixelReporter

    override suspend fun doWork(): Result {
        logcat { "PIR-CUSTOM-STATS: Attempt to fire custom pixels" }
        optOutSubmissionSuccessRateReporter.attemptFirePixel()
        optOutConfirmationReporter.attemptFirePixel()
        engagementReporter.attemptFirePixel()
        weeklyPixelReporter.attemptFirePixel()

        return Result.success()
    }

    companion object {
        const val TAG_PIR_RECURRING_CUSTOM_STATS = "TAG_PIR_RECURRING_CUSTOM_STATS"
    }
}
