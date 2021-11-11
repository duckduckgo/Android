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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import timber.log.Timber

@ContributesMultibinding(AppObjectGraph::class)
class OfflinePixelScheduler @Inject constructor(private val workManager: WorkManager) :
    LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleOfflinePixels() {

        Timber.v("Scheduling offline pixels to be sent")
        workManager.cancelAllWorkByTag(WORK_REQUEST_TAG)

        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val request =
            PeriodicWorkRequestBuilder<OfflinePixelWorker>(SERVICE_INTERVAL, SERVICE_TIME_UNIT)
                .addTag(WORK_REQUEST_TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INTERVAL, BACKOFF_TIME_UNIT)
                .build()

        workManager.enqueue(request)
    }

    open class OfflinePixelWorker(val context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        lateinit var offlinePixelSender: OfflinePixelSender

        override suspend fun doWork(): Result {
            return try {
                offlinePixelSender.sendOfflinePixels().blockingAwait()
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

@ContributesMultibinding(AppObjectGraph::class)
class OfflinePixelWorkerInjectorPlugin
@Inject
constructor(private val offlinePixelSender: OfflinePixelSender) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is OfflinePixelScheduler.OfflinePixelWorker) {
            worker.offlinePixelSender = offlinePixelSender
            return true
        }
        return false
    }
}
