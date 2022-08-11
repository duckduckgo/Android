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

package com.duckduckgo.adclick.impl

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
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class DataRemovalAdClickWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    @Inject
    lateinit var adClickManager: AdClickManager

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            adClickManager.clearAllExpiredAsync()

            return@withContext Result.success()
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class
)
class DataRemovalAdClickWorkerScheduler @Inject constructor(
    private val workManager: WorkManager
) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        Timber.v("Scheduling ad click data removal worker")
        val workerRequest = PeriodicWorkRequestBuilder<DataRemovalAdClickWorker>(12, TimeUnit.HOURS)
            .addTag(DATA_REMOVAL_AD_CLICK_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(DATA_REMOVAL_AD_CLICK_WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, workerRequest)
    }

    companion object {
        private const val DATA_REMOVAL_AD_CLICK_WORKER_TAG = "DATA_REMOVAL_AD_CLICK_WORKER_TAG"
    }
}
