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

package com.duckduckgo.app.remotemessage

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.remotemessage.impl.RemoteMessagingConfigDownloader
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RemoteMessagingConfigDownloadWorker(context: Context, workerParameters: WorkerParameters) : CoroutineWorker(context, workerParameters) {
    lateinit var downloader: RemoteMessagingConfigDownloader
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

@ContributesMultibinding(AppObjectGraph::class)
class RemoteMessagingConfigDownloadScheduler @Inject constructor(
    private val workManager: WorkManager
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleDownload() {
        Timber.v("RMF: Scheduling remote config worker")
        val workerRequest = PeriodicWorkRequestBuilder<RemoteMessagingConfigDownloadWorker>(4, TimeUnit.HOURS)
            .addTag(REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, workerRequest)
    }

    companion object {
        private const val REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG = "REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG"
    }
}
