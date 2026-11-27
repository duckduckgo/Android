/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.codegen.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.SCAM
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.models.Type
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TYPE = "type"
private const val BOOTSTRAP = "bootstrap"

@ContributesWorker(AppScope::class)
class MaliciousSiteProtectionFiltersUpdateWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var maliciousSiteRepository: MaliciousSiteRepository

    @Inject
    lateinit var maliciousSiteDao: MaliciousSiteDao

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature

    override suspend fun doWork(): Result {
        return withContext(dispatcherProvider.io()) {
            if (maliciousSiteProtectionFeature.isFeatureEnabled().not() || maliciousSiteProtectionFeature.canUpdateDatasets().not()) {
                return@withContext Result.success()
            }

            val feeds = inputData.getStringArray(TYPE)
                ?.mapNotNull { Feed.fromString(it) }
                ?.let { feeds ->
                    if (!maliciousSiteProtectionFeature.scamProtectionEnabled()) {
                        return@let feeds.filterNot { it == SCAM }
                    }
                    feeds
                }
                ?.toTypedArray() ?: return@withContext Result.failure()

            val feedsToLoad = if (inputData.getBoolean(BOOTSTRAP, false)) {
                feeds.filter { maliciousSiteDao.getLatestRevision(it.name, Type.FILTER_SET.name) == null }.toTypedArray()
            } else {
                feeds
            }
            if (feedsToLoad.isEmpty()) return@withContext Result.success()

            return@withContext if (maliciousSiteRepository.loadFilters(*feedsToLoad).isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}

@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<PrivacyConfigCallbackPlugin>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
@SingleIn(AppScope::class)
class MaliciousSiteProtectionFiltersUpdateWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature,
    private val schedule: ProtectionUpdateSchedule,
) : PrivacyConfigCallbackPlugin, MainProcessLifecycleObserver {

    @Synchronized
    override fun onCreate(owner: LifecycleOwner) {
        enqueuePeriodicWork()
        enqueuePeriodicScamWork()
    }

    @Synchronized
    override fun onPrivacyConfigDownloaded() {
        enqueuePeriodicWork()
        enqueuePeriodicScamWork()
    }

    private fun enqueuePeriodicWork() {
        if (maliciousSiteProtectionFeature.isFeatureEnabled().not() || maliciousSiteProtectionFeature.canUpdateDatasets().not()) {
            cancelWorker(MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG)
            return
        }
        enqueueWorker(PHISHING, MALWARE, tag = MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG)
    }

    private fun enqueuePeriodicScamWork() {
        with(maliciousSiteProtectionFeature) {
            if (isFeatureEnabled().not() || canUpdateDatasets().not() || scamProtectionEnabled().not()) {
                cancelWorker(MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG)
                return
            }
        }
        enqueueWorker(SCAM, tag = MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG)
    }

    private fun cancelWorker(tag: String) {
        schedule.clear(tag)
        workManager.cancelUniqueWork(tag)
        workManager.cancelUniqueWork("${tag}_v2")
        workManager.cancelUniqueWork("${tag}_bootstrap")
    }

    private fun enqueueWorker(vararg feeds: Feed, tag: String) {
        val data = Data.Builder().putStringArray(TYPE, feeds.map { it.name }.toTypedArray()).build()
        val bootstrapData = Data.Builder().putAll(data).putBoolean(BOOTSTRAP, true).build()
        val bootstrap = OneTimeWorkRequestBuilder<MaliciousSiteProtectionFiltersUpdateWorker>()
            .setInputData(bootstrapData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        val interval = maliciousSiteProtectionFeature.getFilterSetUpdateFrequency()
        val periodic = PeriodicWorkRequestBuilder<MaliciousSiteProtectionFiltersUpdateWorker>(interval, TimeUnit.MINUTES)
            .addTag(tag)
            .setInputData(data)
            .setInitialDelay(schedule.delayMillis(tag, interval), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.cancelUniqueWork(tag)
        workManager.enqueueUniqueWork("${tag}_bootstrap", androidx.work.ExistingWorkPolicy.KEEP, bootstrap)
        workManager.enqueueUniquePeriodicWork("${tag}_v2", ExistingPeriodicWorkPolicy.UPDATE, periodic)
    }

    companion object {
        private const val MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG = "MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG"
        private const val MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG = "MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG"
    }
}
