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

import android.app.PendingIntent
import android.app.PendingIntent.getService
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.app.notification.NotificationHandlerService.Companion.NOTIFICATION_AUTO_CANCEL
import com.duckduckgo.app.notification.NotificationHandlerService.Companion.NOTIFICATION_SYSTEM_ID_EXTRA
import com.duckduckgo.app.notification.NotificationHandlerService.Companion.PIXEL_SUFFIX_EXTRA
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.model.NotificationSpec
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SearchNotification
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.NOTIFICATION_SHOWN
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NotificationScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val clearDataNotification: SchedulableNotification,
    private val privacyNotification: SchedulableNotification,
    private val searchPromptNotification: SearchNotification
) {

    suspend fun scheduleNextNotification() {

        workManager.cancelAllWorkByTag(UNUSED_APP_WORK_REQUEST_TAG)

        when {
            privacyNotification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<PrivacyNotificationWorker>(), 1, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            searchPromptNotification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<SearchPromptNotificationWorker>(), 2, TimeUnit.DAYS, CONTINUOUS_APP_USE_REQUEST_TAG)
            }
            clearDataNotification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<ClearDataNotificationWorker>(), 3, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            else -> Timber.v("Notifications not enabled for this variant")
        }
    }

    fun launchStickySearchNotification() {
        Timber.v("Posting sticky notification")
        val request = OneTimeWorkRequestBuilder<StickySearchNotificationWorker>()
            .addTag(STICKY_REQUEST_TAG)
            .build()

        workManager.enqueue(request)
    }

    fun dismissStickySearchNotification() {
        Timber.v("Dismissing sticky notification")
        val request = OneTimeWorkRequestBuilder<DismissSearchNotificationWorker>()
            .addTag(STICKY_REQUEST_TAG)
            .build()

        workManager.enqueue(request)
    }

    fun launchSearchPromptNotification() {
        Timber.v("Posting sticky search prompt notification")
        val request = OneTimeWorkRequestBuilder<SearchPromptNotificationWorker>()
            .addTag(STICKY_PROMPT_REQUEST_TAG)
            .build()

        workManager.enqueue(request)
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
            val launchIntent = pendingNotificationHandlerIntent(context, notification.launchIntent, specification)
            val cancelIntent = pendingNotificationHandlerIntent(context, notification.cancelIntent, specification)
            val systemNotification = factory.createNotification(specification, launchIntent, cancelIntent)
            notificationDao.insert(Notification(notification.id))
            manager.notify(specification.systemId, systemNotification)

            pixel.fire("${NOTIFICATION_SHOWN.pixelName}_${specification.pixelSuffix}")
            return Result.success()
        }

        private fun pendingNotificationHandlerIntent(context: Context, eventType: String, specification: NotificationSpec): PendingIntent {
            val intent = Intent(context, NotificationHandlerService::class.java)
            intent.type = eventType
            intent.putExtra(PIXEL_SUFFIX_EXTRA, specification.pixelSuffix)
            intent.putExtra(NOTIFICATION_SYSTEM_ID_EXTRA, specification.systemId)
            intent.putExtra(NOTIFICATION_AUTO_CANCEL, specification.autoCancel)
            return getService(context, 0, intent, 0)!!
        }
    }

    class SearchPromptNotificationWorker(context: Context, params: WorkerParameters) : SearchNotificationWorker(context, params)
    class StickySearchNotificationWorker(context: Context, params: WorkerParameters) : SearchNotificationWorker(context, params)

    open class SearchNotificationWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        lateinit var manager: NotificationManagerCompat
        lateinit var factory: NotificationFactory
        lateinit var notificationDao: NotificationDao
        lateinit var notification: SearchNotification
        lateinit var pixel: Pixel

        override suspend fun doWork(): Result {

            val specification = notification.buildSpecification()

            val launchIntent = pendingNotificationHandlerIntent(context, notification.launchIntent, specification)
            val cancelIntent = pendingNotificationHandlerIntent(context, notification.cancelIntent, specification)

            val systemNotification =
                factory.createSearchNotification(specification, launchIntent, cancelIntent, notification.layoutId, notification.priority)

            notificationDao.insert(Notification(notification.id))
            manager.notify(NotificationRegistrar.NotificationId.StickySearch, systemNotification)

            pixel.fire("${NOTIFICATION_SHOWN.pixelName}_${specification.pixelSuffix}")
            return Result.success()
        }

        private fun pendingNotificationHandlerIntent(context: Context, eventType: String, specification: NotificationSpec): PendingIntent {
            val intent = Intent(context, NotificationHandlerService::class.java)
            intent.type = eventType
            intent.putExtra(PIXEL_SUFFIX_EXTRA, specification.pixelSuffix)
            intent.putExtra(NOTIFICATION_SYSTEM_ID_EXTRA, specification.systemId)
            intent.putExtra(NOTIFICATION_AUTO_CANCEL, specification.autoCancel)
            return getService(context, 0, intent, 0)!!
        }
    }

    class DismissSearchNotificationWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        lateinit var manager: NotificationManagerCompat
        lateinit var notificationDao: NotificationDao
        lateinit var notification: SearchNotification
        lateinit var pixel: Pixel

        override suspend fun doWork(): Result {

            val specification = notification.buildSpecification()

            val intent = Intent(context, NotificationHandlerService::class.java)
            intent.type = NotificationHandlerService.NotificationEvent.STICKY_SEARCH_DISMISS
            intent.putExtra(PIXEL_SUFFIX_EXTRA, specification.pixelSuffix)
            intent.putExtra(NOTIFICATION_SYSTEM_ID_EXTRA, specification.systemId)
            intent.putExtra(NOTIFICATION_AUTO_CANCEL, specification.autoCancel)
            context.startService(intent)

            return Result.success()
        }

    }

    companion object {
        const val UNUSED_APP_WORK_REQUEST_TAG = "com.duckduckgo.notification.schedule"
        const val CONTINUOUS_APP_USE_REQUEST_TAG = "com.duckduckgo.notification.schedule.continuous"
        const val STICKY_REQUEST_TAG = "com.duckduckgo.notification.sticky"
        const val STICKY_PROMPT_REQUEST_TAG = "com.duckduckgo.notification.sticky.prompt"
    }
}