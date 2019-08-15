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
import androidx.work.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class OfflinePixelScheduler @Inject constructor() {

    fun scheduleOfflinePixels() {

        Timber.v("Scheduling offline pixels to be sent")
        val workManager = WorkManager.getInstance()
        workManager.cancelAllWorkByTag(WORK_REQUEST_TAG)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<OfflinePixelWorker>(SERVICE_INTERVAL, SERVICE_TIME_UNIT)
            .addTag(WORK_REQUEST_TAG)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INTERVAL, BACKOFF_TIME_UNIT)
            .build()

        workManager.enqueue(request)
    }

    open class OfflinePixelWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        lateinit var offlinePixelSender: OfflinePixelSender

        override suspend fun doWork(): Result {
            return try {
                offlinePixelSender
                    .sendOfflinePixels()
                    .blockingAwait()
                Result.success()
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }

    companion object {
        private const val WORK_REQUEST_TAG = "com.duckduckgo.statistics.offlinepixels.schedule"
        private const val SERVICE_INTERVAL = 3L
        private val SERVICE_TIME_UNIT = TimeUnit.HOURS
        private const val BACKOFF_INTERVAL = 10L
        private val BACKOFF_TIME_UNIT = TimeUnit.MINUTES
    }
}