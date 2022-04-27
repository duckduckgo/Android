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
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.isVPNRetentionStudyEnabled
import com.duckduckgo.di.scopes.AppScope

import com.duckduckgo.mobile.android.vpn.store.R
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTracker
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerBlocklist
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExceptionRuleMetadata
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerJsonParser
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerJsonParser.Companion
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerMetadata
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi.Builder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class AppTrackerListUpdateWorker(
    context: Context,
    workerParameters: WorkerParameters
) :
    CoroutineWorker(context, workerParameters) {
    lateinit var appTrackerListDownloader: AppTrackerListDownloader
    lateinit var vpnDatabase: VpnDatabase
    lateinit var variantManager: VariantManager

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val updateBlocklistResult = updateTrackerBlocklist()
            val updateRulesResult = updateTrackerExceptionRules()

            val success = Result.success()
            if (updateBlocklistResult != success || updateRulesResult != success) {
                Timber.w("One of the app tracker list updates failed, scheduling a retry")
                return@withContext Result.retry()
            }

            Timber.w("Tracker list updates success")
            return@withContext success
        }
    }

    private fun updateTrackerBlocklist(): Result {
        Timber.d("Updating the app tracker bloclist")
        val blocklist = appTrackerListDownloader.downloadAppTrackerBlocklist()
        when (blocklist.etag) {
            is ETag.ValidETag -> {
                val currentEtag =
                    vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata()?.eTag
                val updatedEtag = blocklist.etag.value

                if (updatedEtag == currentEtag) {
                    Timber.v("Downloaded blocklist has same eTag, noop")
                    return Result.success()
                }

                val trackerBlocklist = if (variantManager.isVPNRetentionStudyEnabled()) {
                    Timber.d(
                        "This install is part of the AppTP retention study, we use a reduce app tracker blocklist, " +
                            "rest of entities will be updated eTag: ${blocklist.etag.value}"
                    )
                    val jsonString = applicationContext.resources.openRawResource(R.raw.reduced_app_trackers_blocklist).bufferedReader()
                        .use { it.readText() }

                    getReducedAppTrackerBlockingList(jsonString)
                } else {
                    Timber.d("Updating the app tracker blocklist, eTag: ${blocklist.etag.value}")
                    blocklist.blocklist
                }

                vpnDatabase
                    .vpnAppTrackerBlockingDao()
                    .updateTrackerBlocklist(
                        trackerBlocklist,
                        blocklist.appPackages,
                        AppTrackerMetadata(eTag = blocklist.etag.value),
                        blocklist.entities
                    )

                return Result.success()
            }
            else -> {
                Timber.w("Received app tracker blocklist with invalid eTag")
                return Result.retry()
            }
        }
    }

    private fun getReducedAppTrackerBlockingList(json: String): List<AppTracker> {
        return AppTrackerJsonParser.parseAppTrackerJson(Builder().build(), json).trackers
    }

    private fun updateTrackerExceptionRules(): Result {
        Timber.d("Updating the app tracker exception rules")
        val exceptionRules = appTrackerListDownloader.downloadAppTrackerExceptionRules()
        when (exceptionRules.etag) {
            is ETag.ValidETag -> {
                val currentEtag =
                    vpnDatabase.vpnAppTrackerBlockingDao().getTrackerExceptionRulesMetadata()?.eTag
                val updatedEtag = exceptionRules.etag.value

                if (updatedEtag == currentEtag) {
                    Timber.v("Downloaded exception rules has same eTag, noop")
                    return Result.success()
                }

                Timber.d("Updating the app tracker rules, eTag: ${exceptionRules.etag.value}")
                vpnDatabase
                    .vpnAppTrackerBlockingDao()
                    .updateTrackerExceptionRules(
                        exceptionRules.trackerExceptionRules,
                        AppTrackerExceptionRuleMetadata(eTag = exceptionRules.etag.value)
                    )

                return Result.success()
            }
            else -> {
                Timber.w("Received app tracker exception rules with invalid eTag")
                return Result.retry()
            }
        }
    }
}

@ContributesMultibinding(AppScope::class)
class AppTrackerListUpdateWorkerScheduler @Inject constructor(
    private val workManager: WorkManager
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleBlocklistUpdateWork() {
        Timber.v("Scheduling tracker blocklist update worker")
        val workerRequest =
            PeriodicWorkRequestBuilder<AppTrackerListUpdateWorker>(12, TimeUnit.HOURS)
                .addTag(APP_TRACKER_LIST_UPDATE_WORKER_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
        workManager.enqueueUniquePeriodicWork(
            APP_TRACKER_LIST_UPDATE_WORKER_TAG, ExistingPeriodicWorkPolicy.KEEP, workerRequest
        )
    }

    companion object {
        private const val APP_TRACKER_LIST_UPDATE_WORKER_TAG = "APP_TRACKER_LIST_UPDATE_WORKER_TAG"
    }
}

@ContributesMultibinding(AppScope::class)
class AppTrackerListUpdateWorkerPlugin
@Inject
constructor(
    private val appTrackerListDownloader: AppTrackerListDownloader,
    private val vpnDatabase: VpnDatabase,
    private val variantManager: VariantManager
) : WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is AppTrackerListUpdateWorker) {
            Timber.v("Injecting dependencies for AppTrackerListUpdateWorker")
            worker.appTrackerListDownloader = appTrackerListDownloader
            worker.vpnDatabase = vpnDatabase
            worker.variantManager = variantManager
            return true
        }

        return false
    }
}
