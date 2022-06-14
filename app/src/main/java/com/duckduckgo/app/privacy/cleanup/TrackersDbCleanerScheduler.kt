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
import androidx.lifecycle.*
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import org.threeten.bp.LocalDateTime
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Module
@ContributesTo(AppScope::class)
class TrackersDbCleanerSchedulerModule {

    @Provides
    @IntoSet
    fun provideDeviceShieldNotificationScheduler(
        workManager: WorkManager
    ): LifecycleObserver {
        return TrackersDbCleanerScheduler(workManager)
    }

    @Provides
    fun providesVpnTrackerDao(vpnDatabase: VpnDatabase): VpnTrackerDao = vpnDatabase.vpnTrackerDao()
}

class TrackersDbCleanerScheduler(private val workManager: WorkManager) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        Timber.v("Scheduling Trackers Blocked DB cleaner")
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
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), CoroutineScope {

    @Inject
    lateinit var webTrackersBlockedDao: WebTrackersBlockedDao
    @Inject
    lateinit var appTrackersDao: VpnTrackerDao

    @WorkerThread
    override suspend fun doWork(): Result {

        webTrackersBlockedDao.deleteOldDataUntil(dateOfLastWeek())
        appTrackersDao.deleteOldDataUntil(dateOfLastWeek())

        Timber.i("Clear trackers dao job finished; returning SUCCESS")
        return Result.success()
    }

    private fun dateOfLastWeek(): String {
        val midnight = LocalDateTime.now().minusDays(7)
        return DatabaseDateFormatter.timestamp(midnight)
    }
}
