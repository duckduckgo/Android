/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.sponsorblock

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.codegen.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.versionedset.VersionedSetDelta
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SingleIn(AppScope::class)
class SponsorBlockDatasetSync @Inject constructor(
    private val service: SponsorBlockDatasetService,
    private val dao: SponsorBlockDatasetDao,
) {
    suspend fun sync() {
        val latest = requireNotNull(service.revision().revision)
        require(latest >= 0)
        var current = dao.revision() ?: 0
        while (current < latest) {
            val response = service.hashPrefixes(current)
            val revision = requireNotNull(response.revision)
            val replace = requireNotNull(response.replace)
            val insert = requireNotNull(response.insert)
            val delete = requireNotNull(response.delete)
            require(revision > current)
            require(current != 0 || replace)
            require((insert + delete).all { it.matches(PREFIX) })
            dao.apply(VersionedSetDelta(insert, delete, revision, replace))
            current = dao.revision() ?: error("Missing SponsorBlock revision")
        }
    }

    private companion object {
        val PREFIX = Regex("[0-9a-f]{8}")
    }
}

@ContributesWorker(AppScope::class)
class SponsorBlockDatasetWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @Inject lateinit var sync: SponsorBlockDatasetSync

    @Inject lateinit var feature: SponsorBlockFeature

    @Inject lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        if (!feature.mvpEnabled().isEnabled()) return@withContext Result.success()
        try {
            sync.sync()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

@ContributesIntoSet(scope = AppScope::class, binding = binding<MainProcessLifecycleObserver>())
@ContributesIntoSet(scope = AppScope::class, binding = binding<PrivacyConfigCallbackPlugin>())
@SingleIn(AppScope::class)
class SponsorBlockDatasetScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val feature: SponsorBlockFeature,
) : MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {
    override fun onCreate(owner: LifecycleOwner) = schedule()

    override fun onPrivacyConfigDownloaded() = schedule()

    private fun schedule() {
        if (!feature.mvpEnabled().isEnabled()) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SponsorBlockDatasetWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        private const val WORK_NAME = "sponsor_block_dataset"
    }
}
