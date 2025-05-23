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

package com.duckduckgo.app.privacy.cleanup

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat

@Module
@ContributesTo(AppScope::class)
class TrackersDbCleanerSchedulerModule {

    @Provides
    @IntoSet
    fun provideDeviceShieldNotificationScheduler(
        workManager: WorkManager,
    ): MainProcessLifecycleObserver {
        return TrackersDbCleanerScheduler(workManager)
    }
}

class TrackersDbCleanerScheduler(private val workManager: WorkManager) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        logcat(VERBOSE) { "Scheduling Trackers Blocked DB cleaner" }
        val dbCleanerWorkRequest = PeriodicWorkRequestBuilder<TrackersDbCleanerWorker>(7, TimeUnit.DAYS)
            .addTag(WORKER_TRACKERS_BLOCKED_DB_CLEANER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(WORKER_TRACKERS_BLOCKED_DB_CLEANER_TAG, ExistingPeriodicWorkPolicy.KEEP, dbCleanerWorkRequest)
    }

    companion object {
        const val WORKER_TRACKERS_BLOCKED_DB_CLEANER_TAG = "TrackersBlockedDbCleanerTag"
    }
}

@ContributesWorker(AppScope::class)
class TrackersDbCleanerWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), CoroutineScope {

    @Inject
    lateinit var webTrackersBlockedDao: WebTrackersBlockedDao

    @Inject
    lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository

    @WorkerThread
    override suspend fun doWork(): Result {
        webTrackersBlockedDao.deleteOldDataUntil(dateOfLastWeek())
        appTrackerBlockingStatsRepository.deleteTrackersUntil(dateOfLastWeek())

        logcat(INFO) { "Clear trackers dao job finished; returning SUCCESS" }
        return Result.success()
    }

    private fun dateOfLastWeek(): String {
        val midnight = LocalDateTime.now().minusDays(7)
        return DatabaseDateFormatter.timestamp(midnight)
    }
}
