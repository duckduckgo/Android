/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.stats

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.mobile.android.vpn.stats.VpnStatsReportingRequestBuilder.Companion.DAILY_PIXEL_WORK_TAG
import timber.log.Timber
import javax.inject.Inject

class VpnStatsReportingScheduler @Inject constructor(
    private val databaseCleanerRequestBuilder: VpnStatsReportingRequestBuilder,
    private val workManager: WorkManager
) {

    fun schedule() {
        Timber.i("Scheduling daily pixel sender")
        val workRequest = databaseCleanerRequestBuilder.buildWorkRequest()
        workManager.enqueueUniquePeriodicWork(DAILY_PIXEL_WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }
}