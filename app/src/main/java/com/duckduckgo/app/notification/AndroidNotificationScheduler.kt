/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.notification

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.di.scopes.AppScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.logcat

// Please don't rename any Worker class name or class path
// More information: https://craigrussell.io/2019/04/a-workmanager-pitfall-modifying-a-scheduled-worker/
@WorkerThread
interface AndroidNotificationScheduler {
    suspend fun scheduleNextNotification()
}

class NotificationScheduler(
    private val workManager: WorkManager,
    private val notificationManager: NotificationManagerCompat,
    private val clearDataNotification: SchedulableNotification,
    private val privacyNotification: SchedulableNotification,
) : AndroidNotificationScheduler {

    override suspend fun scheduleNextNotification() {
        scheduleInactiveUserNotifications()
    }

    private suspend fun scheduleInactiveUserNotifications() {
        workManager.cancelAllWorkByTag(UNUSED_APP_WORK_REQUEST_TAG)

        if (notificationManager.areNotificationsEnabled()) {
            scheduleUnusedAppNotifications()
        }
    }

    private suspend fun scheduleUnusedAppNotifications() {
        if (privacyNotification.canShow()) {
            scheduleNotification(
                OneTimeWorkRequestBuilder<PrivacyNotificationWorker>(),
                PRIVACY_DELAY_DURATION_IN_DAYS,
                TimeUnit.DAYS,
                UNUSED_APP_WORK_REQUEST_TAG,
            )
        }
        if (clearDataNotification.canShow()) {
            scheduleNotification(
                OneTimeWorkRequestBuilder<ClearDataNotificationWorker>(),
                CLEAR_DATA_DELAY_DURATION_IN_DAYS,
                TimeUnit.DAYS,
                UNUSED_APP_WORK_REQUEST_TAG,
            )
        }
    }

    private fun scheduleNotification(
        builder: OneTimeWorkRequest.Builder,
        duration: Long,
        unit: TimeUnit,
        tag: String,
    ) {
        logcat(VERBOSE) { "Scheduling notification for $duration" }
        val request = builder
            .addTag(tag)
            .setInitialDelay(duration, unit)
            .build()

        workManager.enqueue(request)
    }

    companion object {
        const val UNUSED_APP_WORK_REQUEST_TAG = "com.duckduckgo.notification.schedule"
        const val CLEAR_DATA_DELAY_DURATION_IN_DAYS = 3L
        const val PRIVACY_DELAY_DURATION_IN_DAYS = 1L
    }
}

// Legacy code. Unused class required for users who already have this notification scheduled from previous version. We will
// delete this as part of https://app.asana.com/0/414730916066338/1119619712088571
@ContributesWorker(AppScope::class)
class ShowClearDataNotification(
    context: Context,
    params: WorkerParameters,
) : SchedulableNotificationWorker<ClearDataNotification>(context, params) {
    @Inject
    override lateinit var notificationSender: NotificationSender

    @Inject
    override lateinit var notification: ClearDataNotification
}

@ContributesWorker(AppScope::class)
open class ClearDataNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : SchedulableNotificationWorker<ClearDataNotification>(context, params) {
    @Inject
    override lateinit var notificationSender: NotificationSender

    @Inject
    override lateinit var notification: ClearDataNotification
}

@ContributesWorker(AppScope::class)
class PrivacyNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : SchedulableNotificationWorker<PrivacyProtectionNotification>(context, params) {
    @Inject
    override lateinit var notificationSender: NotificationSender

    @Inject
    override lateinit var notification: PrivacyProtectionNotification
}

abstract class SchedulableNotificationWorker<T : SchedulableNotification>(
    val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    abstract var notificationSender: NotificationSender
    abstract var notification: T

    override suspend fun doWork(): Result {
        notificationSender.sendNotification(notification)
        return Result.success()
    }
}
