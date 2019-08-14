/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.statistics.api

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class OfflinePixelScheduler @Inject constructor() {

    fun scheduleOfflinePixels() {

        Timber.v("Scheduling offline pixels to be sent")
        val workManager = WorkManager.getInstance()
        workManager.cancelAllWorkByTag(WORK_REQUEST_TAG)

        val request = PeriodicWorkRequestBuilder<OfflinePixelWorker>(HOURLY_INTERVAL, TimeUnit.HOURS)
            .addTag(WORK_REQUEST_TAG)
            .build()

        workManager.enqueue(request)
    }

    open class OfflinePixelWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        lateinit var offlinePixelSender: OfflinePixelSender

        override suspend fun doWork(): Result {
            try {
                offlinePixelSender
                    .sendOfflinePixels()
                    .blockingGet()
                return Result.success()
            } catch (e: Exception) {
                return Result.failure()
            }
        }
    }

    companion object {
        private const val WORK_REQUEST_TAG = "com.duckduckgo.statistics.offlinepixels.schedule"
        private const val HOURLY_INTERVAL = 3L
    }
}