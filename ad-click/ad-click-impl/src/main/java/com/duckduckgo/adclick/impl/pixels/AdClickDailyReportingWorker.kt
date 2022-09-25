/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl.pixels

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class AdClickDailyReportingWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    @Inject
    lateinit var adClickPixels: AdClickPixels

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            adClickPixels.fireCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
            return@withContext Result.success()
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class
)
class AdClickDailyReportingWorkerScheduler @Inject constructor(
    private val workManager: WorkManager
) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        Timber.v("Scheduling ad click daily reporting worker")
        val workerRequest = PeriodicWorkRequestBuilder<AdClickDailyReportingWorker>(24, TimeUnit.HOURS)
            .addTag(DAILY_REPORTING_AD_CLICK_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_REPORTING_AD_CLICK_WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, workerRequest)
    }

    companion object {
        private const val DAILY_REPORTING_AD_CLICK_WORKER_TAG = "DAILY_REPORTING_AD_CLICK_WORKER_TAG"
    }
}
