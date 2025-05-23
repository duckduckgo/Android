/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.history.impl.scheduleddeletion

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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.history.impl.InternalNavigationHistory
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class HistoryDeletionWorker @Inject constructor(
    private val workManager: WorkManager,
    private val history: InternalNavigationHistory,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        scheduleWorker(workManager)
    }

    fun clearOldHistoryEntries() {
        appCoroutineScope.launch {
            history.clearOldEntries()
        }
    }

    companion object {
        private const val WORKER_DELETE_HISTORY = "com.duckduckgo.history.delete.worker"

        private fun scheduleWorker(workManager: WorkManager) {
            logcat(VERBOSE) { "Scheduling the HistoryDeletionWorker" }

            val request = PeriodicWorkRequestBuilder<RealHistoryDeletionWorker>(repeatInterval = 1L, repeatIntervalTimeUnit = DAYS)
                .addTag(WORKER_DELETE_HISTORY)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(WORKER_DELETE_HISTORY, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

@ContributesWorker(AppScope::class)
class RealHistoryDeletionWorker(
    val context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    @Inject
    lateinit var historyDeletionWorker: HistoryDeletionWorker

    override suspend fun doWork(): Result {
        logcat(VERBOSE) { "Deleting old history entries" }

        historyDeletionWorker.clearOldHistoryEntries()

        return Result.success()
    }
}
