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

package com.duckduckgo.mobile.android.vpn.blocklist

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerMetadata
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AppTrackerListUpdateWorker(context: Context, workerParameters: WorkerParameters) : CoroutineWorker(context, workerParameters) {
    lateinit var appTrackerListDownloader: AppTrackerListDownloader
    lateinit var vpnDatabase: VpnDatabase

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            Timber.d("Updating the app tracker bloclist")
            val blocklist = appTrackerListDownloader.downloadAppTrackerBlocklist()
            when (blocklist.etag) {
                is ETag.ValidETag -> {
                    val currentEtag = vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata()?.eTag
                    val updatedEtag = blocklist.etag.value

                    if (updatedEtag == currentEtag) {
                        Timber.v("Downloaded blocklist has same eTag, noop")
                        return@withContext Result.success()
                    }

                    Timber.d("Updating the app tracker blocklist, eTag: ${blocklist.etag.value}")
                    vpnDatabase.vpnAppTrackerBlockingDao().updateTrackerBlocklist(blocklist.blocklist, AppTrackerMetadata(eTag = blocklist.etag.value))

                    return@withContext Result.success()
                }
                else -> {
                    Timber.w("Received app tracker blocklist with invalid eTag")
                    return@withContext Result.retry()
                }
            }
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class AppTrackerListUpdateWorkerScheduler @Inject constructor(
    private val workManager: WorkManager
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleBlocklistUpdateWork() {
        Timber.v("Scheduling tracker blocklist update worker")
        val workerRequest = PeriodicWorkRequestBuilder<AppTrackerListUpdateWorker>(12, TimeUnit.HOURS)
            .addTag(APP_TRACKER_LIST_UPDATE_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(APP_TRACKER_LIST_UPDATE_WORKER_TAG, ExistingPeriodicWorkPolicy.KEEP, workerRequest)
    }

    companion object {
        private const val APP_TRACKER_LIST_UPDATE_WORKER_TAG = "APP_TRACKER_LIST_UPDATE_WORKER_TAG"
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class AppTrackerListUpdateWorkerPlugin @Inject constructor(
    private val appTrackerListDownloader: AppTrackerListDownloader,
    private val vpnDatabase: VpnDatabase
) : WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is AppTrackerListUpdateWorker) {
            Timber.v("Injecting dependencies for AppTrackerListUpdateWorker")
            worker.appTrackerListDownloader = appTrackerListDownloader
            worker.vpnDatabase = vpnDatabase
            return true
        }

        return false
    }
}
