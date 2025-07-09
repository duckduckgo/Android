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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.SCAM
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext

private const val TYPE = "type"

@ContributesWorker(AppScope::class)
class MaliciousSiteProtectionFiltersUpdateWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var maliciousSiteRepository: MaliciousSiteRepository

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

            if (feeds.isEmpty()) return@withContext Result.success()

            return@withContext if (maliciousSiteRepository.loadFilters(*feeds).isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class MaliciousSiteProtectionFiltersUpdateWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature,

) : PrivacyConfigCallbackPlugin, MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        enqueuePeriodicWork()
        enqueuePeriodicScamWork()
    }

    override fun onPrivacyConfigDownloaded() {
        enqueuePeriodicWork()
        enqueuePeriodicScamWork()
    }

    private fun enqueuePeriodicWork() {
        if (maliciousSiteProtectionFeature.isFeatureEnabled().not() || maliciousSiteProtectionFeature.canUpdateDatasets().not()) {
            workManager.cancelUniqueWork(MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG)
            return
        }
        enqueueWorker(PHISHING, MALWARE, tag = MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG)
    }

    private fun enqueuePeriodicScamWork() {
        with(maliciousSiteProtectionFeature) {
            if (isFeatureEnabled().not() || canUpdateDatasets().not() || scamProtectionEnabled().not()) {
                workManager.cancelUniqueWork(MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG)
                return
            }
        }
        enqueueWorker(SCAM, tag = MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG)
    }

    private fun enqueueWorker(vararg feeds: Feed, tag: String) {
        PeriodicWorkRequestBuilder<MaliciousSiteProtectionFiltersUpdateWorker>(
            maliciousSiteProtectionFeature.getFilterSetUpdateFrequency(),
            TimeUnit.MINUTES,
        ).addTag(tag)
            .setInputData(Data.Builder().putStringArray(TYPE, feeds.map { it.name }.toTypedArray()).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build().let {
                workManager.enqueueUniquePeriodicWork(
                    tag,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    it,
                )
            }
    }

    companion object {
        private const val MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG = "MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG"
        private const val MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG = "MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG"
    }
}
