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

package com.duckduckgo.adblocking.impl.segmentskip

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames
import com.duckduckgo.adblocking.impl.BuildConfig
import com.duckduckgo.adblocking.impl.di.AdBlockingPreferences
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.codegen.annotations.ContributesWorker
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.await
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.random.Random

@SingleIn(AppScope::class)
class SegmentSkipDatasetSync @Inject constructor(
    private val service: SegmentSkipDatasetService,
    private val binary: SegmentSkipBinaryStore,
    private val segmentsProvider: SegmentSkipSegmentsProvider,
    private val pixel: Pixel,
    private val freshness: SegmentSkipDatasetFreshness,
    private val preferences: SegmentSkipUserPreferences,
    private val feature: SegmentSkipFeature,
) {
    private val syncMutex = Mutex()

    suspend fun sync(staleOnly: Boolean = false, enableGeneration: Long? = null) = syncMutex.withLock {
        if (!feature.mvpEnabled().isEnabled() || preferences.modeFlow().firstOrNull().let { it == null || it == SegmentSkipMode.OFF }) {
            mockLog("check skipped: disabled")
            return@withLock
        }
        if (staleOnly && !freshness.isStale(binary)) {
            mockLog("check skipped: fresh")
            return@withLock
        }
        if (enableGeneration != null && freshness.generation() != enableGeneration) {
            mockLog("check skipped: superseded")
            return@withLock
        }
        mockLog("revision check started")
        val latest = try {
            requireNotNull(service.revision().revision).also { require(it in 0..0xffffffffL) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_DAILY, AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_COUNT)
            throw e
        }
        var current = binary.revision()
        while (current < latest) {
            try {
                download(current, current)
            } catch (e: InvalidSegmentSkipFile) {
                if (current == 0L) throw e
                download(0L, current, requireFull = true)
            }
            current = binary.revision()
        }
        currentCoroutineContext().ensureActive()
        freshness.recordSuccess()
        mockLog("revision check completed")
    }

    private suspend fun download(revision: Long, expected: Long, requireFull: Boolean = false) = coroutineScope {
        val call = try {
            service.binaryHashPrefixes(revision)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            fire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_DAILY, AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_COUNT)
            throw e
        }
        val completed = AtomicBoolean(false)
        val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!completed.get()) call.cancel()
            }
        }
        try {
            val response = try {
                call.await()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                fire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_DAILY, AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_COUNT)
                throw e
            }
            response.use {
                try {
                    binary.apply(expected, NetworkStream(it.byteStream()), requireFull) {
                        segmentsProvider.clearCache()
                        completed.set(true)
                        fire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATED_DAILY, AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATED_COUNT)
                    }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    when (e) {
                        is NetworkReadException -> fire(
                            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_DAILY,
                            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_COUNT,
                        )
                        is InvalidSegmentSkipFile, is UnsupportedSegmentSkipFormat -> fire(
                            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_VALIDATION_ERROR_DAILY,
                            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_VALIDATION_ERROR_COUNT,
                            mapOf(
                                "reason" to if (e is InvalidSegmentSkipFile) {
                                    e.reason.pixelValue
                                } else {
                                    SegmentSkipValidationReason.DECODE_ERROR.pixelValue
                                },
                            ),
                        )
                        else -> fire(
                            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATE_ERROR_DAILY,
                            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATE_ERROR_COUNT,
                        )
                    }
                    throw e
                }
            }
            completed.set(true)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            canceller.cancel()
        }
    }
    private fun fire(daily: AdBlockingPixelNames, count: AdBlockingPixelNames, parameters: Map<String, String> = emptyMap()) {
        pixel.enqueueFire(daily, parameters = parameters, type = Pixel.PixelType.Daily())
        pixel.enqueueFire(count, parameters = parameters)
    }

    private class NetworkReadException(cause: IOException) : IOException(cause)

    private class NetworkStream(input: InputStream) : FilterInputStream(input) {
        override fun read(): Int = try { super.read() } catch (e: IOException) { throw NetworkReadException(e) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            try { super.read(buffer, offset, length) } catch (e: IOException) { throw NetworkReadException(e) }
    }
}

@ContributesWorker(AppScope::class)
class SegmentSkipDatasetWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @Inject lateinit var sync: SegmentSkipDatasetSync

    @Inject lateinit var feature: SegmentSkipFeature

    @Inject lateinit var dispatchers: DispatcherProvider

    @Inject lateinit var freshness: SegmentSkipDatasetFreshness

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        if (!feature.mvpEnabled().isEnabled()) {
            mockLog("worker skipped: disabled")
            return@withContext Result.success()
        }
        try {
            val force = inputData.getBoolean(FORCE_CHECK, false)
            val periodic = inputData.getBoolean(PERIODIC_CHECK, false)
            val generation = if (force) inputData.getLong(ENABLE_GENERATION, 0) else if (periodic) freshness.generation() else null
            mockLog("worker started: ${if (periodic) "periodic" else if (force) "forced" else "catch-up"}")
            if (periodic) {
                mockLog("periodic jitter started")
                delay(Random.nextLong(1, TimeUnit.MINUTES.toMillis(5) + 1))
                mockLog("periodic jitter completed")
            }
            sync.sync(staleOnly = !force && !periodic, enableGeneration = generation)
            mockLog("worker result: success")
            Result.success()
        } catch (e: UnsupportedSegmentSkipFormat) {
            mockLog("worker result: unsupported format")
            Result.success()
        } catch (e: CancellationException) {
            mockLog("worker cancelled")
            throw e
        } catch (e: Exception) {
            mockLog("worker result: retry")
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_CHECK = "periodic_check"
        const val FORCE_CHECK = "force_check"
        const val ENABLE_GENERATION = "enable_generation"
    }
}

@SingleIn(AppScope::class)
class SegmentSkipDatasetFreshness @Inject constructor(
    @AdBlockingPreferences private val dataStore: DataStore<Preferences>,
    private val clock: CurrentTimeProvider,
) {
    suspend fun isStale(binary: SegmentSkipBinaryStore): Boolean {
        if (binary.revision() == 0L) return true
        val last = dataStore.data.firstOrNull()?.get(LAST_CHECK) ?: return true
        val now = clock.currentTimeMillis()
        return last <= 0 || last > now || now - last >= TimeUnit.HOURS.toMillis(1)
    }

    suspend fun generation(): Long = dataStore.data.firstOrNull()?.get(GENERATION) ?: 0

    suspend fun recordSuccess() {
        dataStore.edit {
            it[LAST_CHECK] = clock.currentTimeMillis()
            it[GENERATION] = (it[GENERATION] ?: 0) + 1
        }
    }

    private companion object {
        val LAST_CHECK = longPreferencesKey("sponsor_dataset_last_check")
        val GENERATION = longPreferencesKey("sponsor_dataset_check_generation")
    }
}

@ContributesIntoSet(scope = AppScope::class, binding = binding<MainProcessLifecycleObserver>())
@ContributesIntoSet(scope = AppScope::class, binding = binding<PrivacyConfigCallbackPlugin>())
@SingleIn(AppScope::class)
class SegmentSkipDatasetScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val feature: SegmentSkipFeature,
    private val preferences: SegmentSkipUserPreferences,
    private val freshness: SegmentSkipDatasetFreshness,
    private val binary: SegmentSkipBinaryStore,
    @AppCoroutineScope private val scope: CoroutineScope,
) : MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {
    private val schedulingMutex = Mutex()

    override fun onCreate(owner: LifecycleOwner) {
        if (BuildConfig.SPONSOR_BLOCK_MOCK) {
            listOf(WORK_NAME, ENABLE_WORK_NAME, CATCH_UP_WORK_NAME).forEach { name ->
                scope.launch {
                    workManager.getWorkInfosForUniqueWorkFlow(name)
                        .map { infos -> infos.map { it.state }.toSet() }
                        .distinctUntilChanged()
                        .collect { states -> mockLog("work $name: ${if (states.isEmpty()) "NONE" else states.sortedBy { it.name }.joinToString()}") }
                }
            }
        }
        scope.launch {
            var previous: Boolean? = null
            combine(preferences.modeFlow(), feature.mvpEnabled().enabled()) { mode, enabled ->
                enabled && mode != SegmentSkipMode.OFF
            }.distinctUntilChanged().collect { enabled ->
                schedulingMutex.withLock {
                    if (enabled) {
                        if (previous == false) enqueueImmediate(force = true)
                        schedulePeriodic()
                    } else {
                        cancelWork()
                    }
                    previous = enabled
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            schedulingMutex.withLock {
                if (feature.mvpEnabled().isEnabled() && preferences.modeFlow().firstOrNull().let {
                        it != null && it != SegmentSkipMode.OFF
                    } && freshness.isStale(binary) &&
                    feature.mvpEnabled().isEnabled() && preferences.modeFlow().firstOrNull().let { it != null && it != SegmentSkipMode.OFF }
                ) {
                    enqueueImmediate(force = false)
                }
            }
        }
    }

    override fun onPrivacyConfigDownloaded() = Unit

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SegmentSkipDatasetWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf(SegmentSkipDatasetWorker.PERIODIC_CHECK to true))
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        mockLog("schedule requested: periodic")
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private suspend fun enqueueImmediate(force: Boolean) {
        val request = OneTimeWorkRequestBuilder<SegmentSkipDatasetWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    SegmentSkipDatasetWorker.FORCE_CHECK to force,
                    SegmentSkipDatasetWorker.ENABLE_GENERATION to if (force) freshness.generation() else 0L,
                ),
            )
            .build()
        mockLog("schedule requested: ${if (force) "forced" else "catch-up"}")
        workManager.enqueueUniqueWork(if (force) ENABLE_WORK_NAME else CATCH_UP_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun cancelWork() {
        mockLog("cancellation requested")
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(ENABLE_WORK_NAME)
        workManager.cancelUniqueWork(CATCH_UP_WORK_NAME)
    }

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        private const val WORK_NAME = "sponsor_dataset"
        private const val ENABLE_WORK_NAME = "sponsor_dataset_enable"
        private const val CATCH_UP_WORK_NAME = "sponsor_dataset_catch_up"
    }
}

private fun mockLog(message: String) {
    if (BuildConfig.SPONSOR_BLOCK_MOCK) Log.d("SegmentSkipMock", message)
}
