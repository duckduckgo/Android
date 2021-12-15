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
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Please don't rename any Worker class name or class path
// More information: https://craigrussell.io/2019/04/a-workmanager-pitfall-modifying-a-scheduled-worker/
@WorkerThread
interface AndroidNotificationScheduler {
    suspend fun scheduleNextNotification()
}

class NotificationScheduler(
    private val workManager: WorkManager,
    private val clearDataNotification: SchedulableNotification,
    private val privacyNotification: SchedulableNotification
) : AndroidNotificationScheduler {

    override suspend fun scheduleNextNotification() {
        scheduleInactiveUserNotifications()
    }

    private suspend fun scheduleInactiveUserNotifications() {
        workManager.cancelAllWorkByTag(UNUSED_APP_WORK_REQUEST_TAG)

        when {
            privacyNotification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<PrivacyNotificationWorker>(), PRIVACY_DELAY_DURATION_IN_DAYS, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            clearDataNotification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<ClearDataNotificationWorker>(), CLEAR_DATA_DELAY_DURATION_IN_DAYS, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            else -> Timber.v("Notifications not enabled for this variant")
        }
    }

    private fun scheduleNotification(builder: OneTimeWorkRequest.Builder, duration: Long, unit: TimeUnit, tag: String) {
        Timber.v("Scheduling notification for $duration")
        val request = builder
            .addTag(tag)
            .setInitialDelay(duration, unit)
            .build()

        workManager.enqueue(request)
    }

    // Legacy code. Unused class required for users who already have this notification scheduled from previous version. We will
    // delete this as part of https://app.asana.com/0/414730916066338/1119619712088571
    class ShowClearDataNotification(context: Context, params: WorkerParameters) : ClearDataNotificationWorker(context, params)

    open class ClearDataNotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class PrivacyNotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)

    open class SchedulableNotificationWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        lateinit var notificationSender: NotificationSender
        lateinit var notification: SchedulableNotification

        override suspend fun doWork(): Result {
            notificationSender.sendNotification(notification)
            return Result.success()
        }
    }

    companion object {
        const val UNUSED_APP_WORK_REQUEST_TAG = "com.duckduckgo.notification.schedule"
        const val CLEAR_DATA_DELAY_DURATION_IN_DAYS = 3L
        const val PRIVACY_DELAY_DURATION_IN_DAYS = 1L
    }
}

@ContributesMultibinding(AppScope::class)
class ClearDataNotificationWorkerInjectorPlugin @Inject constructor(
    private val notificationSender: NotificationSender,
    private val clearDataNotification: ClearDataNotification
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is NotificationScheduler.ClearDataNotificationWorker) {
            worker.notificationSender = notificationSender
            worker.notification = clearDataNotification
            return true
        }
        return false
    }
}

@ContributesMultibinding(AppScope::class)
class PrivacyNotificationWorkerInjectorPlugin @Inject constructor(
    private val notificationSender: NotificationSender,
    private val privacyProtectionNotification: PrivacyProtectionNotification
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is NotificationScheduler.PrivacyNotificationWorker) {
            worker.notificationSender = notificationSender
            worker.notification = privacyProtectionNotification
            return true
        }
        return false
    }
}
