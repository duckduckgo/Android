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
import com.duckduckgo.mobile.android.vpn.di.AppTpBlocklistUpdateMutex
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerMetadata
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class AppTrackerListUpdateWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var appTrackerListDownloader: AppTrackerListDownloader

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    @AppTpBlocklistUpdateMutex
    lateinit var mutex: Mutex

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    override suspend fun doWork(): Result {
        return withContext(dispatchers.io()) {
            val updateBlocklistResult = mutex.withLock {
                val res = updateTrackerBlocklist()

                // Report current blocklist status after update
                deviceShieldPixels.reportBlocklistStats(
                    mapOf(
                        "blocklist_etag" to vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata()?.eTag.toString(),
                        "blocklist_size" to vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlockListSize().toString(),
                    ),
                )

                res
            }

            val success = Result.success()
            if (updateBlocklistResult != success) {
                logcat(WARN) { "One of the app tracker list updates failed, scheduling a retry" }
                return@withContext Result.retry()
            }

            logcat { "Tracker list updates success" }
            return@withContext success
        }
    }

    private fun updateTrackerBlocklist(): Result {
        logcat { "Updating the app tracker blocklist" }
        val blocklist = appTrackerListDownloader.downloadAppTrackerBlocklist()
        when (blocklist.etag) {
            is ETag.ValidETag -> {
                val currentEtag =
                    vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata()?.eTag
                val updatedEtag = blocklist.etag.value

                if (updatedEtag == currentEtag) {
                    logcat { "Downloaded blocklist has same eTag, noop" }
                    return Result.success()
                }

                logcat { "Updating the app tracker blocklist, previous/new eTag: $currentEtag / $updatedEtag" }

                vpnDatabase
                    .vpnAppTrackerBlockingDao()
                    .updateTrackerBlocklist(
                        blocklist.blocklist,
                        blocklist.appPackages,
                        AppTrackerMetadata(eTag = blocklist.etag.value),
                        blocklist.entities,
                    )

                return Result.success()
            }
            else -> {
                logcat(WARN) { "Received app tracker blocklist with invalid eTag" }
                return Result.retry()
            }
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class AppTrackerListUpdateWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        logcat { "Scheduling tracker blocklist update worker" }
        val workerRequest =
            PeriodicWorkRequestBuilder<AppTrackerListUpdateWorker>(12, TimeUnit.HOURS)
                .addTag(APP_TRACKER_LIST_UPDATE_WORKER_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
        workManager.enqueueUniquePeriodicWork(
            APP_TRACKER_LIST_UPDATE_WORKER_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            workerRequest,
        )
    }

    companion object {
        private const val APP_TRACKER_LIST_UPDATE_WORKER_TAG = "APP_TRACKER_LIST_UPDATE_WORKER_TAG"
    }
}
