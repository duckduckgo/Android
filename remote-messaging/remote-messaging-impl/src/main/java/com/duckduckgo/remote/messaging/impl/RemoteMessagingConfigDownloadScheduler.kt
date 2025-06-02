/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat

const val REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG = "REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG"

@ContributesWorker(AppScope::class)
class RemoteMessagingConfigDownloadWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var downloader: RemoteMessagingConfigDownloader

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override suspend fun doWork(): Result {
        return withContext(dispatcherProvider.io()) {
            val result = downloader.download()
            return@withContext if (result) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class RemoteMessagingConfigDownloadScheduler @Inject constructor(
    private val workManager: WorkManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            scheduleDownload()
        }
    }

    private fun scheduleDownload() {
        val refreshInterval = if (remoteMessagingFeatureToggles.scheduleEveryHour().isEnabled()) 1L else 4L
        logcat(VERBOSE) { "RMF: Scheduling remote config worker with fresh interval of $refreshInterval hours" }
        val requestBuilder = PeriodicWorkRequestBuilder<RemoteMessagingConfigDownloadWorker>(refreshInterval, TimeUnit.HOURS)
            .addTag(REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
        if (remoteMessagingFeatureToggles.canScheduleOnPrivacyConfigUpdates().isEnabled()) {
            logcat(VERBOSE) { "RMF: Add delay to remote config worker" }
            requestBuilder.setInitialDelay(5L, TimeUnit.MINUTES)
        }
        val workerRequest = requestBuilder.build()
        workManager.enqueueUniquePeriodicWork(REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG, CANCEL_AND_REENQUEUE, workerRequest)
    }
}
