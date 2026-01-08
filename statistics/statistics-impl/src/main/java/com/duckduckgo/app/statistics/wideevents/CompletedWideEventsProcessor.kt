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

package com.duckduckgo.app.statistics.wideevents

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject
import kotlin.collections.chunked
import kotlin.collections.toSet

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesMultibinding(AppScope::class)
class CompletedWideEventsProcessor @Inject constructor(
    private val wideEventRepository: WideEventRepository,
    private val wideEventSender: WideEventSender,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val wideEventFeature: WideEventFeature,
    private val dispatcherProvider: DispatcherProvider,
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {

    private val mutex = Mutex()

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch {
            runCatching {
                if (!isFeatureEnabled()) return@runCatching

                wideEventRepository
                    .hasCompletedWideEvents()
                    .filter { it }
                    .collect {
                        try {
                            processCompletedWideEvents()
                        } catch (e: Exception) {
                            logcat { "Failed to process completed wide events: ${e.stackTraceToString()}" }
                            scheduleRetry()
                        }
                    }
            }
        }
    }

    suspend fun processCompletedWideEvents() {
        if (!isFeatureEnabled()) return

        mutex.withLock {
            // Process events in chunks to avoid querying too many events at once.
            wideEventRepository.getCompletedWideEventIds().chunked(100).forEach { idsChunk ->
                wideEventRepository.getWideEvents(idsChunk.toSet()).forEach { event ->
                    wideEventSender.sendWideEvent(event)
                    wideEventRepository.deleteWideEvent(event.id)
                }
            }
        }
    }

    private suspend fun isFeatureEnabled(): Boolean =
        withContext(dispatcherProvider.io()) {
            wideEventFeature.self().isEnabled()
        }

    private fun scheduleRetry() {
        workManager.enqueueUniqueWork(
            TAG_WORKER_COMPLETED_WIDE_EVENTS,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CompletedWideEventsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    companion object {
        const val TAG_WORKER_COMPLETED_WIDE_EVENTS = "TAG_WORKER_COMPLETED_WIDE_EVENTS"
    }
}

@ContributesWorker(AppScope::class)
class CompletedWideEventsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var completedWideEventsProcessor: CompletedWideEventsProcessor

    override suspend fun doWork(): Result {
        return try {
            completedWideEventsProcessor.processCompletedWideEvents()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
