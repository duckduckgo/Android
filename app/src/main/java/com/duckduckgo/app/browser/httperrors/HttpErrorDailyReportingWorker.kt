/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.httperrors

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesWorker(AppScope::class)
class HttpErrorDailyReportingWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    @Inject
    lateinit var httpErrorPixels: HttpErrorPixels

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result {
        return withContext(dispatchers.io()) {
            httpErrorPixels.fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)
            httpErrorPixels.fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_4XX_DAILY)
            httpErrorPixels.fire5xxCountPixels()
            return@withContext Result.success()
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class HttpErrorDailyReportingWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        logcat(VERBOSE) { "Scheduling http error daily reporting worker" }
        val workerRequest = PeriodicWorkRequestBuilder<HttpErrorDailyReportingWorker>(24, TimeUnit.HOURS)
            .addTag(DAILY_REPORTING_HTTP_ERROR_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_REPORTING_HTTP_ERROR_WORKER_TAG, ExistingPeriodicWorkPolicy.UPDATE, workerRequest)
    }

    companion object {
        private const val DAILY_REPORTING_HTTP_ERROR_WORKER_TAG = "DAILY_REPORTING_HTTP_ERROR_WORKER_TAG"
    }
}
