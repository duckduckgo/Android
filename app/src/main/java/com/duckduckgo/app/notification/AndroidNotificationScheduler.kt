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
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.NOTIFICATION_SHOWN
import timber.log.Timber
import java.util.concurrent.TimeUnit

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
                scheduleNotification(OneTimeWorkRequestBuilder<PrivacyNotificationWorker>(), 1, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            clearDataNotification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<ClearDataNotificationWorker>(), 3, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            else -> Timber.v("Notifications not enabled for this variant")
        }
    }

    private fun scheduleNotification(builder: OneTimeWorkRequest.Builder, duration: Long, unit: TimeUnit, tag: String) {
        Timber.v("Scheduling notification")
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
    class DripA1NotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class DripA2NotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class DripB1NotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class DripB2NotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)

    open class SchedulableNotificationWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        lateinit var manager: NotificationManagerCompat
        lateinit var factory: NotificationFactory
        lateinit var notification: SchedulableNotification
        lateinit var notificationDao: NotificationDao
        lateinit var pixel: Pixel

        override suspend fun doWork(): Result {

            if (!notification.canShow()) {
                Timber.v("Notification no longer showable")
                return Result.success()
            }

            val specification = notification.buildSpecification()
            val launchIntent = NotificationHandlerService.pendingNotificationHandlerIntent(context, notification.launchIntent, specification)
            val cancelIntent = NotificationHandlerService.pendingNotificationHandlerIntent(context, notification.cancelIntent, specification)
            val systemNotification = factory.createNotification(specification, launchIntent, cancelIntent)
            notificationDao.insert(Notification(notification.id))
            manager.notify(specification.systemId, systemNotification)

            pixel.fire("${NOTIFICATION_SHOWN.pixelName}_${specification.pixelSuffix}")
            return Result.success()
        }
    }

    companion object {
        const val UNUSED_APP_WORK_REQUEST_TAG = "com.duckduckgo.notification.schedule"
    }
}
