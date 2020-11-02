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

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class VpnStatsReportingRequestBuilder @Inject constructor() {

    fun buildWorkRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<VpnStatsReportingWorker>(24, TimeUnit.HOURS)
            .addTag(DAILY_PIXEL_WORK_TAG)
            .build()
    }

    companion object {
        const val DAILY_PIXEL_WORK_TAG = "DailyPixelWorker"
    }
}

class VpnStatsReportingWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    override suspend fun doWork(): Result {
        val current = vpnDatabase.vpnStatsDao().getCurrent()
        Timber.i("Sending daily pixel report $current")
        return Result.success()
    }
}