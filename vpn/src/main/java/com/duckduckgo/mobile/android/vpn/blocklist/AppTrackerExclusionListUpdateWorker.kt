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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExclusionListMetadata
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerSystemAppOverrideListMetadata
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AppTrackerExclusionListUpdateWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {
    lateinit var appTrackerListDownloader: AppTrackerListDownloader
    lateinit var vpnDatabase: VpnDatabase

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val exclusionListResult = updateExclusionList()
            val sysAppOverridesListResult = updateSystemAppOverrides()

            val success = Result.success()
            if (exclusionListResult != success) {
                Timber.w("Failed downloading exclution or system app override lists, scheduling a retry")
                return@withContext Result.retry()
            }

            return@withContext success
        }
    }

    private suspend fun updateSystemAppOverrides(): Result {
        Timber.d("Updating the app system app overrides list")

        val sysAppsOverrides = appTrackerListDownloader.downloadSystemAppOverrideList()

        when (sysAppsOverrides.etag) {
            is ETag.ValidETag -> {
                val currentEtag = vpnDatabase.vpnSystemAppsOverridesDao().getSystemAppOverridesMetadata()?.eTag
                val updatedEtag = sysAppsOverrides.etag.value

                if (updatedEtag == currentEtag) {
                    Timber.v("Downloaded system app overrides has same eTag, noop")
                    return Result.success()
                }

                Timber.d("Updating the app tracker system app overrides, eTag: ${sysAppsOverrides.etag.value}")
                vpnDatabase.vpnSystemAppsOverridesDao().upsertSystemAppOverrides(
                    sysAppsOverrides.overridePackages, AppTrackerSystemAppOverrideListMetadata(eTag = updatedEtag)
                )

                TrackerBlockingVpnService.restartVpnService(applicationContext)

                return Result.success()
            }
            else -> {
                Timber.w("Received app tracker exclusion list with invalid eTag")
                return Result.retry()
            }
        }
    }

    private suspend fun updateExclusionList(): Result {
        Timber.d("Updating the app tracker exclusion list")
        val exclusionList = appTrackerListDownloader.downloadAppTrackerExclusionList()

        when (exclusionList.etag) {
            is ETag.ValidETag -> {
                val currentEtag = vpnDatabase.vpnAppTrackerBlockingDao().getExclusionListMetadata()?.eTag
                val updatedEtag = exclusionList.etag.value

                if (updatedEtag == currentEtag) {
                    Timber.v("Downloaded exclusion list has same eTag, noop")
                    return Result.success()
                }

                Timber.d("Updating the app tracker exclusion list, eTag: ${exclusionList.etag.value}")
                vpnDatabase.vpnAppTrackerBlockingDao()
                    .updateExclusionList(exclusionList.excludedPackages, AppTrackerExclusionListMetadata(eTag = exclusionList.etag.value))

                TrackerBlockingVpnService.restartVpnService(applicationContext)

                return Result.success()
            }
            else -> {
                Timber.w("Received app tracker exclusion list with invalid eTag")
                return Result.retry()
            }
        }
    }
}

@ContributesMultibinding(AppScope::class)
class AppTrackerExclusionListUpdateWorkerScheduler @Inject constructor(
    private val workManager: WorkManager
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleBlocklistUpdateWork() {
        Timber.v("Scheduling tracker exclusion list update worker")
        val workerRequest = PeriodicWorkRequestBuilder<AppTrackerExclusionListUpdateWorker>(6, TimeUnit.HOURS)
            .addTag(APP_TRACKER_EXCLUSION_LIST_UPDATE_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(APP_TRACKER_EXCLUSION_LIST_UPDATE_WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, workerRequest)
    }

    companion object {
        private const val APP_TRACKER_EXCLUSION_LIST_UPDATE_WORKER_TAG = "APP_TRACKER_EXCLUSION_LIST_UPDATE_WORKER_TAG"
    }
}

@ContributesMultibinding(AppScope::class)
class AppTrackerExclusionListUpdateWorkerPlugin @Inject constructor(
    private val appTrackerListDownloader: AppTrackerListDownloader,
    private val vpnDatabase: VpnDatabase
) : WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is AppTrackerExclusionListUpdateWorker) {
            Timber.v("Injecting dependencies for AppTrackerExclusionListUpdateWorker")
            worker.appTrackerListDownloader = appTrackerListDownloader
            worker.vpnDatabase = vpnDatabase
            return true
        }

        return false
    }
}
