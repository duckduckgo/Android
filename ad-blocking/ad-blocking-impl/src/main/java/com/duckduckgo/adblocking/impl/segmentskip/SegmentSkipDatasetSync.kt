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
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
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
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SingleIn(AppScope::class)
class SegmentSkipDatasetSync @Inject constructor(
    private val service: SegmentSkipDatasetService,
    private val binary: SegmentSkipBinaryStore,
    private val segmentsProvider: SegmentSkipSegmentsProvider,
    private val pixel: Pixel,
) {
    private val syncMutex = Mutex()

    suspend fun sync() = syncMutex.withLock {
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

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        if (!feature.mvpEnabled().isEnabled()) return@withContext Result.success()
        try {
            sync.sync()
            Result.success()
        } catch (e: UnsupportedSegmentSkipFormat) {
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
class SegmentSkipDatasetScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val feature: SegmentSkipFeature,
) : MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {
    override fun onCreate(owner: LifecycleOwner) = schedule()

    override fun onPrivacyConfigDownloaded() = schedule()

    private fun schedule() {
        if (!feature.mvpEnabled().isEnabled()) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SegmentSkipDatasetWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        private const val WORK_NAME = "sponsor_dataset"
    }
}
