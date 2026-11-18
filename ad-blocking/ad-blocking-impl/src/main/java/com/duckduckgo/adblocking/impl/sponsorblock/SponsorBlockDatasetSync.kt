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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.await
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SingleIn(AppScope::class)
class SponsorBlockDatasetSync @Inject constructor(
    private val service: SponsorBlockDatasetService,
    private val binary: SponsorBlockBinaryStore,
    private val segmentsProvider: SponsorBlockSegmentsProvider,
) {
    private val syncMutex = Mutex()

    suspend fun sync() = syncMutex.withLock {
        val latest = requireNotNull(service.revision().revision)
        require(latest in 0..0xffffffffL)
        var current = binary.revision()
        while (current < latest) {
            try {
                download(current, current)
            } catch (e: InvalidSponsorBlockFile) {
                if (current == 0L) throw e
                download(0L, current, requireFull = true)
            }
            current = binary.revision()
        }
    }

    private suspend fun download(revision: Long, expected: Long, requireFull: Boolean = false) = coroutineScope {
        val call = service.binaryHashPrefixes(revision)
        val completed = AtomicBoolean(false)
        val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!completed.get()) call.cancel()
            }
        }
        try {
            call.await().use { binary.apply(expected, it.byteStream(), requireFull, segmentsProvider::clearCache) }
            completed.set(true)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            canceller.cancel()
        }
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
        } catch (e: UnsupportedSponsorBlockFormat) {
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
