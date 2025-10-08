/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.triggers

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.BACKGROUND_SYNC
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class SyncBackgroundWorker(
    context: Context,
    workerParameters: WorkerParameters,
) :
    CoroutineWorker(context, workerParameters) {

    @Inject
    lateinit var syncEngine: SyncEngine

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var deviceSyncState: DeviceSyncState

    override suspend fun doWork(): Result {
        return withContext(dispatchers.io()) {
            if (deviceSyncState.isUserSignedInOnDevice()) {
                syncEngine.triggerSync(BACKGROUND_SYNC)
            }
            return@withContext Result.success()
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class SyncBackgroundWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val deviceSyncState: DeviceSyncState,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        coroutineScope.launch(dispatcherProvider.io()) {
            if (deviceSyncState.isUserSignedInOnDevice()) {
                scheduleBackgroundSync()
            } else {
                workManager.cancelAllWorkByTag(BACKGROUND_SYNC_WORKER_TAG)
            }
        }
    }

    private fun scheduleBackgroundSync() {
        logcat(VERBOSE) { "Scheduling background sync worker" }
        val workerRequest = PeriodicWorkRequestBuilder<SyncBackgroundWorker>(3, TimeUnit.HOURS)
            .addTag(BACKGROUND_SYNC_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(BACKGROUND_SYNC_WORKER_TAG, ExistingPeriodicWorkPolicy.KEEP, workerRequest)
    }

    companion object {
        private const val BACKGROUND_SYNC_WORKER_TAG = "BACKGROUND_SYNC_WORKER_TAG"
    }
}
