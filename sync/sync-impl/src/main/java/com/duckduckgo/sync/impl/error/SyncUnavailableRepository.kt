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

package com.duckduckgo.sync.impl.error

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.engine.SyncEngineLifecycle
import com.duckduckgo.sync.impl.engine.SyncNotificationBuilder
import com.duckduckgo.sync.impl.error.SchedulableErrorNotificationWorker.Companion.SYNC_ERROR_NOTIFICATION_TAG
import com.duckduckgo.sync.store.SyncUnavailableStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface SyncUnavailableRepository {
    fun onServerAvailable()
    fun onServerUnavailable()
    suspend fun isSyncUnavailable(): Boolean
    suspend fun triggerNotification()
}

@Suppress("SameParameterValue")
@ContributesBinding(
    scope = AppScope::class,
    boundType = SyncUnavailableRepository::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = SyncEngineLifecycle::class,
)
@SingleInstanceIn(AppScope::class)
class RealSyncUnavailableRepository @Inject constructor(
    private val context: Context,
    private val syncUnavailableStore: SyncUnavailableStore,
    private val notificationManager: NotificationManagerCompat,
    private val syncNotificationBuilder: SyncNotificationBuilder,
    private val workManager: WorkManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SyncUnavailableRepository, SyncEngineLifecycle {

    override suspend fun isSyncUnavailable(): Boolean {
        return syncUnavailableStore.isSyncUnavailable()
    }

    override fun onServerAvailable() {
        appCoroutineScope.launch {
            if (syncUnavailableStore.isSyncUnavailable()) {
                Timber.d("Sync-Engine: Sync is back online - clearing data and canceling notif")
                syncUnavailableStore.clearError()
                cancelNotification()
            }
        }
    }

    override fun onServerUnavailable() {
        appCoroutineScope.launch {
            if (!syncUnavailableStore.isSyncUnavailable()) {
                syncUnavailableStore.setSyncUnavailableSince(getUtcIsoLocalDate())
            }
            syncUnavailableStore.setSyncUnavailable(true)
            syncUnavailableStore.setSyncErrorCount(syncUnavailableStore.getSyncErrorCount() + 1)

            Timber.d(
                "Sync-Engine: server unavailable count: ${syncUnavailableStore.getSyncErrorCount()} " +
                    "pausedAt: ${syncUnavailableStore.getSyncUnavailableSince()} lastNotifiedAt: ${syncUnavailableStore.getUserNotifiedAt()}",
            )
            if (syncUnavailableStore.getSyncErrorCount() >= ERROR_THRESHOLD_NOTIFICATION_COUNT) {
                Timber.d("Sync-Engine: Sync error count reached threshold")
                triggerNotification()
            } else {
                scheduleNotification(
                    OneTimeWorkRequest.Builder(SchedulableErrorNotificationWorker::class.java),
                    SYNC_ERROR_NOTIFICATION_DELAY,
                    TimeUnit.HOURS,
                    SYNC_ERROR_NOTIFICATION_TAG,
                )
            }
        }
    }

    override suspend fun triggerNotification() {
        val today = LocalDateTime.now().toLocalDate()
        val lastNotification = syncUnavailableStore.getUserNotifiedAt().takeUnless { it.isEmpty() }?.let {
            LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
        } ?: ""
        val userNotifiedToday = today == lastNotification
        Timber.d("Sync-Engine: was user notified today? $userNotifiedToday")
        if (!userNotifiedToday) {
            Timber.d("Sync-Engine: notifying user about sync error")
            notificationManager.checkPermissionAndNotify(
                context,
                SYNC_ERROR_NOTIFICATION_ID,
                syncNotificationBuilder.buildSyncErrorNotification(context),
            )
            syncUnavailableStore.setUserNotifiedAt(getUtcIsoLocalDate())
        }
    }

    private fun cancelNotification() {
        notificationManager.cancel(SYNC_ERROR_NOTIFICATION_ID)
        workManager.cancelAllWorkByTag(SYNC_ERROR_NOTIFICATION_TAG)
    }

    private fun getUtcIsoLocalDate(): String {
        return Instant.now().atOffset(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    private fun scheduleNotification(
        builder: OneTimeWorkRequest.Builder,
        duration: Long,
        unit: TimeUnit,
        tag: String,
    ) {
        val request = builder
            .addTag(tag)
            .setInitialDelay(duration, unit)
            .build()
        workManager.enqueueUniqueWork(tag, KEEP, request)
    }

    override fun onSyncEnabled() {
        // no-op
    }

    override suspend fun onSyncDisabled() {
        Timber.d("Sync-Engine: Sync disabled, clearing unavailable store data")
        syncUnavailableStore.clearAll()
        cancelNotification()
    }

    companion object {
        internal const val SYNC_ERROR_NOTIFICATION_ID = 7451
        internal const val ERROR_THRESHOLD_NOTIFICATION_COUNT = 10
        private const val SYNC_ERROR_NOTIFICATION_DELAY = 12L
    }
}

@ContributesWorker(AppScope::class)
class SchedulableErrorNotificationWorker(
    val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var syncPausedRepository: SyncUnavailableRepository

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override suspend fun doWork(): Result {
        withContext(dispatcherProvider.io()) {
            syncPausedRepository.triggerNotification()
        }
        return Result.success()
    }

    companion object {
        const val SYNC_ERROR_NOTIFICATION_TAG = "com.duckduckgo.sync.notification.error.schedule"
    }
}
